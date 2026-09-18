# AUDIT.md — Pre-implementation audit

**Date:** 2026-09-17
**Scope:** `SPEC.md`, `ARCHITECTURE.md`, `DECISIONS.md`, all 33 `.java` files under
`legacy/MyMenu/src/`, and `legacy/MyMenu/resources/` (`plugin.yml`, `config.yml`,
`menus.yml`, `MANIFEST.MF`, `.classpath`, `.project`).

This audit covers the 11 files a prior review skipped: `MyMenuChangelog`, `MyMenuDelete`,
`MyMenuHelp`, `MyMenuList`, `MyMenuMyMenu`, `MyMenuReload`, `MyMenuSave`,
`MyMenuUpdate`, `MyMenuConfig`, `Metrics` and `Updater`. All other files were re-read as
well.

**Confidence.** Items marked ✅ were checked against current Paper documentation. Everything
else comes from reading the legacy source or from memory, and is marked **unverified** where it
matters. Nothing has been compiled or run.

Legacy paths below are relative to `legacy/MyMenu/src/me/ChaddTheMan/MyMenu/`.

---

## 1. Highest-priority findings

### 1.1 Unconditional close cleanup contradicts the architecture's own features

`ARCHITECTURE.md` §5 requires session removal to be unconditional on `InventoryCloseEvent`.
Several features close the current inventory as a side effect:

- a `MENU` action opens another menu, which closes the current one;
- opening the property editor (SPEC §11.2) closes the edit view;
- starting a chat prompt (SPEC §11.3) closes the menu before prompting.

Applied literally, the rule clears the navigation stack on every `MENU` action (breaking
SPEC §9.3) and destroys the `EditSession`'s pending chat prompt the moment it starts.

1.x avoided this with a conditional check (`Listeners/InventoryCloseListener.java:56`,
`if (!this.player.isInConversation())`). That conditional path is part of the leak-prone
logic 2.0 is meant to replace.

**Proposal:** always clean up *something*, but decide what from
`InventoryCloseEvent.getReason()`. Paper's `Reason.OPEN_NEW` distinguishes "replaced by
another inventory" from a real close. Keep "no session survives quit" unconditional.

### 1.2 Loading can silently delete data

In 1.x, one bad entry aborted the whole load:

- an unknown material gives `Material.getMaterial(...) == null`, so `new ItemStack(null)`
  throws (`Objects/MyMenuItem.java:81`);
- a menu without `inventory.slots` throws (`Objects/MyMenuItem.java:78`);
- a file without a top-level `menus:` throws (`Objects/MyMenuMenu.java:135`).

The spec doesn't say how 2.0 handles bad entries. If 2.0 skips them and later calls
`saveAll`, it rewrites the file without them and erases the skipped menus silently.

**Needs a rule.** Options: (a) skip the bad entry and refuse to save until an admin fixes
the file; (b) keep the unparsed YAML node and write it back unchanged; (c) fail the whole
load, keep the plugin in a safe read-only state, and never overwrite.

### 1.3 A shutdown save conflicts with hard rule 2

`ARCHITECTURE.md` §12 lists "No shutdown save" as a 1.x bug, implying 2.0 saves on
shutdown. In `onDisable` the only way to guarantee an async save finishes is to block on its
future, which is blocking I/O on the main thread. Bukkit's async scheduler also won't run
new tasks during disable, so storage needs its own executor that `onDisable` can drain.

**Needs:** an explicit, documented exception to hard rule 2 for shutdown.

### 1.4 `{SERVER}` has no source

SPEC §10 defines `{SERVER}` as "Server name from `server.properties`". Modern
`server.properties` has no server-name key, and `Server#getName()` returns the software
name, which is the 1.x bug the spec says it fixes.

**Proposal:** add a `serverName` setting to `config.yml`.

### 1.5 The documents misdescribe 1.x in two places

This doesn't change the design, but `DECISIONS.md` is meant to be the record.

- **Saving.** `ARCHITECTURE.md` §1 and `DECISIONS.md` #15 say 1.x saved by "reading items
  back out of" the shared `Inventory`. It didn't: `saveInventoryItems` iterates the
  `HashMap<Integer, MyMenuItem>` model (`Objects/MyMenuItem.java:144`). The shared
  inventory was a display problem, not a persistence problem.
- **Bound-item matching.** `ARCHITECTURE.md` §12 and `DECISIONS.md` #25 say 1.x stored only
  a material and matched on `getType()` alone. In fact `/mmset <menu> <material> <name...>`
  created a name-specific binding (`boundItemName`, `isSpecific`), and `getMenuByItem`
  checked the display name (`Objects/MyMenuMenu.java:279-285`,
  `Commands/MyMenuSet.java:104-115`). 1.x offered both what 2.0 calls `TYPE` and
  `TYPE_AND_NAME`. The real change is the **default**, from `TYPE` to `TYPE_AND_NAME`.

---

## 2. `ARCHITECTURE.md` against current Paper APIs

| Claim | Status | Notes |
|---|---|---|
| Brigadier via `LifecycleEvents.COMMANDS`, registered from `onEnable`, alias passed to `Commands.register` | ✅ Confirmed | Paper docs show `getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ...)` from the plugin class, with `register` overloads taking a description and aliases. The handler runs again on `/minecraft:reload`, so it must be safe to run twice. |
| Registering a `COMMANDS` handler disables Bukkit `/reload` (§8) | ⚠️ Partly confirmed | Paper's lifecycle docs say reload is disabled "if plugins register handlers in certain situations". They don't say whether `COMMANDS` from `onEnable` is one of them. §8 states it too firmly; check with `runServer`. |
| `PluginLoader` + `MavenLibraryResolver` for HikariCP and MySQL (§11) | ⚠️ Needs change | Paper's docs say using Maven Central directly as a CDN breaks Central's terms. Use `MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR`. Also, the loader runs before config is read, so every server downloads the MySQL driver, even YAML-only ones. |
| PlaceholderAPI accessed only through a hook that no-ops if absent (§10) | ⚠️ Incomplete | Paper plugins have isolated classloaders. Access requires declaring PlaceholderAPI under `dependencies.server` in `paper-plugin.yml` with `load: BEFORE`, `required: false`, `join-classpath: true`. Not mentioned in §10 or §11. |
| Glow = add an enchantment + the hide-enchantments flag (§4 step 4) | ⚠️ Outdated | `ItemMeta#setEnchantmentGlintOverride` does this directly (since 1.20.5). A fake enchantment would also trip SPEC §6's rule that "enchantments beyond the glow flag" force the serialized form. |
| Identify menus by `InventoryHolder`; `Inventory.getName()` removed (§4.1) | ✅ Correct | This is Paper's recommended pattern. |
| Paper byte-array item serialisation (§7.2) | ✅ Fine | SPEC §6's `H4sI…` example is consistent with gzip-compressed output. |
| Java 25 for the 26.x line (SPEC §2, "verify") | ✅ Confirmed | Paper lists Java 25 for 26.1+. |
| *(not mentioned)* Opening/closing inventories inside `InventoryClickEvent` | ⚠️ Constraint | Calling `openInventory`/`closeInventory` directly in a click handler is unsupported. `MENU`, `BACK` and `CLOSE` must be scheduled for the next tick. |
| *(not mentioned)* Suggestion threading | ❓ **Unverified** | Paper may compute command suggestions off the main thread. If so, the `<menu>` suggestion provider reads a registry that §3 declares main-thread-only. Check before stage 7; a concurrent map or snapshot would resolve it. |
| *(not in spec)* Text input | ❓ Worth checking | Paper has a Dialog API with native text-input fields, a possible replacement for chat input in SPEC §11.3. |

Build tooling in §11 (`run-paper`, a `paper-plugin.yml` generator, Shadow) was not
verified here. Several have moved namespaces in recent years (Shadow is now
`com.gradleup.shadow`, and `plugin-yml` has a maintained fork). Verify at stage 1.

---

## 3. Behaviour in the previously unread files not captured in the spec

### 3.1 Save and reload timing

1.x saved immediately (`MyMenu.updateToConfig()`) on create, delete, set/unset, item add
(conversation finish), item remove (right-click) and edit-mode close. It did **not** save
after a middle-click move, or on shutdown.

`/mmreload` (`MyMenu.updateFromConfig()`, `MyMenu.java:243`) threw away unsaved in-memory
changes, closed every open menu with "Server reloaded. Menu closed"
(`Objects/MyMenuPlayer.java:65-69`), and abandoned every chat conversation. The help text
calls the discard intentional (`Tools/InfoMenus.java:36`).

The spec says neither **when edits reach storage** nor **what `reload` does** to unsaved
edits, open menus, edit sessions or pending action sequences. It also doesn't say whether
`reload` can switch `storage.type` at runtime.

### 3.2 Update checking

- **Blocked the main thread.** `checkUpdate()` runs in a sync task (`MyMenu.java:124`), and
  `updater.getResult()` calls `waitForThread()` → `thread.join()` on the network thread
  (`Updater/Updater.java:131,167-176`). The connect timeout was 5 s with no read timeout.
- **Compared versions as plain strings.** `shouldUpdate` is `!local.equalsIgnoreCase(remote)`
  (`Updater/Updater.java:394`), so an older remote version counted as an update.
- **Needs for 2.0:** a release tag format (`v2.0.0` or `2.0.0`), a real semver comparison,
  and a `User-Agent` header, which GitHub's API requires.

### 3.3 `changelog` and `update` commands

1.x changelogs were hard-coded string arrays per version (`Tools/InfoMenus.java:17-24`,
`Commands/MyMenuChangelog.java`). `/update mymenu` downloaded a replacement jar.

SPEC §3 keeps `/mymenu changelog [version]` and `/mymenu update` but doesn't say where
2.0's changelog comes from (hard-coded, a bundled resource, or GitHub release notes), or
what `update` does now that the plugin never downloads. A likely answer is "re-check now
and report", but it should be stated.

### 3.4 Delete command

`MyMenuDelete` holds a `FileConfiguration` captured when it was constructed
(`Commands/MyMenuDelete.java:29-30`). `configFile.reload()` replaces the config object, so
the later `config.set("menus." + name, null)` updates an orphaned object that is never
saved (`:55-57`). Only the preceding `updateToConfig()` actually removed the menu.

1.x had no confirmation step and didn't close open views of the deleted menu. The spec
doesn't say what happens to players viewing or editing a menu when it's deleted.

### 3.5 `MyMenuConfig` and startup config handling

`MyMenuConfig` is a thin wrapper: `reload()` loads from disk, `saveConfig()` calls
`FileConfiguration.save(File)`, which writes the live file in place, and
`saveDefaultConfig()` copies the bundled resource if missing.

- **First-run clobber.** `onEnable` calls `configFile.reload()` *before*
  `saveDefaultConfig()` (`MyMenu.java:89-91`). The in-memory config is empty, so the
  missing `Plugin.CheckForUpdates` key triggers `set` + `saveConfig()` (`:96-98`), which
  overwrites the freshly copied default `config.yml`. It fixes itself only because
  `MyMenuConfigMenu.saveMenuConfigList()` rebuilds `Menus:` afterwards.
- **Two objects for one file.** `configFile` and `menuConfigConfigFile` both point at
  `config.yml` (`MyMenu.java:88,95`), each with its own in-memory copy.
- **In-game join settings never persisted.** `saveMenuConfigList()` calls
  `reloadMenuConfigList()`, which rebuilds the list from disk *before* writing
  (`Objects/MyMenuConfigMenu.java:56-60`). In-memory `openOnJoin`/`giveItemOnJoin` changes
  were discarded. There was no command to change them anyway; they were hand-edit only.
- **Load-time writes confirmed.** `setupMenuList` saves once per menu inside the load loop
  (`Objects/MyMenuMenu.java:173`). `addInventoryItems` migrates `command` → `commands` and
  saves mid-load (`Objects/MyMenuItem.java:89-97`). `setupMenuConfigList` saves at the end
  of loading. This matches the `ARCHITECTURE.md` §7.1 claim.

### 3.6 Join behaviour

- When several menus have `OpenOnJoin`, the **last one in the list wins**
  (`Listeners/PlayerJoinListener.java:86-102`).
- Items are given before the menu opens. The menu opens 20 ticks after join; the update
  notice shows 100 ticks after join.
- Players who already have a session are skipped.
- "Already has the item" used `inventory.contains(ItemStack)`, an exact match that
  includes the amount.

The spec doesn't say what happens when several menus have `openOnJoin: true`.

### 3.7 Help text drift

The help text had already drifted from the code, which supports `ARCHITECTURE.md` §8's
generated help:

- `ADMIN_HELP_SET` says `null` or `unset` unbind; the code accepts `none` and `null`
  (`Commands/MyMenuSet.java:54`).
- Help advertises `/mmupdate`; the real command was `/update mymenu`.
- Player help says "Right click on it", but the code opens on any non-physical interaction,
  left-click included (`Listeners/PlayerInteractListener.java:40`).

### 3.8 Metrics

MCStats reported four custom graphs: plugin version, bound-item materials, menus per server,
and total menus (`MyMenu.java:174-236`). SPEC §13 doesn't say which bStats charts to keep.

### 3.9 Permission defaults

`plugin.yml` sets no `default:` on any node, so every node was op-only, including
`MyMenu.help.player`. SPEC §4 doesn't specify defaults either.

### 3.10 Minor

- `/mymenu` (`MyMenuMyMenu`), `list`, `delete`, `reload`, `save`, `changelog` and `help`
  were all player-only in 1.x. SPEC makes most of them console-usable, a deliberate change.
- `list` colour-coded menus (white = unbound, blue = bound, red = bound and name-specific)
  and numbered them.
- `help command <name>` required `MyMenu.help.admin`. SPEC §3 says "as above", which is
  ambiguous between the player and admin nodes.

---

## 4. Legacy behaviour SPEC.md changes silently

These aren't necessarily wrong, but each should get a `DECISIONS.md` entry.

1. **Menu stays open after a click.** 1.x closed the menu before running any item's
   commands (`Listeners/InventoryClickListener.java:121`). SPEC implies it stays open unless
   there's a `CLOSE` action.
2. **Location wildcards.** 1.x used exact doubles (`getLocation().getX()`); SPEC §10 says
   block coordinates.
3. **Where wildcards apply.** 1.x applied wildcards **to command strings only**, never to
   item names or lore (`Listeners/InventoryClickListener.java:124-141`). SPEC §10 lists
   names, lore and message text, and doesn't mention command values. `$give {PLAYER} …` was
   the main use case, so this gap matters.
4. **Colour codes in commands.** 1.x translated `&` codes in commands, so a command
   containing `&` was mangled.
5. **Bound-item trigger.** 1.x opened on left-click as well as right-click. SPEC doesn't say
   which interactions open a menu. Paper also fires `PlayerInteractEvent` once per hand, so
   off-hand behaviour needs stating.
6. **Features dropped without comment.** Item moving by middle-click in edit mode; the
   32-character limit on menu names and titles; conversation limits (at most 10 commands,
   10 lore lines, 45 characters per line).
7. **Click type discrimination.** 1.x ran the same command list for every click type. The
   per-click-type keys in SPEC §8.4 are new. This is already implied, but worth recording.

---

## 5. Open questions

Numbered so they can be answered by number.

1. **Join settings location.** `openOnJoin`/`giveItemOnJoin` live in `config.yml`
   (SPEC §5.1, §12), but SPEC §11.2 makes them editable in-game. That means the plugin
   writes `config.yml` at runtime, and with MySQL the join settings stay local to each
   server while menus are shared. Recommendation: move them onto the `Menu` model.
2. **Where glow lives.** `ARCHITECTURE.md` §3 puts `glow` on both `ItemTemplate`
   (descriptive) and `MenuItem`. Which wins, and how does glow apply to serialized items?
3. **Serialized items.** Can their display name, lore and amount be edited in the property
   editor (SPEC §11.2)? Do wildcards and placeholders resolve inside them?
4. **Clicks after a live edit.** Per SPEC §11.4, viewers keep the old rendering, but a click
   resolves by slot against the *current* model, so a player could click an old icon and run
   the new item's actions. The same applies to delete and reload while a menu is open,
   because `MenuHolder` still references the old `Menu` object.
5. **Delay behaviour.**
   - What happens when a stored list exceeds `maxTotalDelaySeconds` on load: reject the
     menu, reject the list, or clamp?
   - Is "ignore repeat click during a pending sequence" per item or per player?
   - What happens to a `MENU` or `BACK` that comes after a `DELAY` once the player has
     closed the menu?
6. **Double-click and middle-click.** A double-click produces a `LEFT` click followed by
   `DOUBLE_CLICK`, so with an `OTHER` fallback two lists may run. `MIDDLE` probably only
   arrives from creative-mode players (**unverified**). Creative players can also clone
   rendered icons, so the rendered-item tag must use a different key from the bound-item tag,
   or cloned icons become menu openers.
7. **Placing items in edit mode (SPEC §11.1).**
   - Does placing an item copy it or move it out of the admin's inventory?
   - What happens when the target slot is occupied, and on shift-click?
   - How are items moved between slots now that middle-click is gone?
   - What happens to an item left on the cursor when the editor closes? In 1.x it dropped a
     real item and lost the menu entry.
8. **Menu names.** Which characters are allowed, and are names case-sensitive? `.` breaks
   YAML paths. 1.x validated only the length (32).
9. **`create` argument order.** `create <menu> [type] [rows] [title...]` is ambiguous for
   titles that start with a number (`create shop 5 Star Shop`) or match a type name
   (`create shop Chest Shop`). Brigadier resolves this by the order branches were built,
   so the spec should decide first.
10. **`TAG_ONLY` bindings.** The only way to hand out a tagged item is `giveItemOnJoin`. Does
    `set` tag the item in the admin's hand? Is a `give` subcommand needed? (It isn't in the
    spec or the deferred list.)
11. **Name matching.** Does `TYPE_AND_NAME` compare Adventure Components (colour-sensitive)
    or plain text?
12. **Backup retention.** SPEC §5 doesn't limit the number of files in `backups/`. With
    frequent saves it grows without bound.
13. **Save ordering.** "Rotate previous into `backups/` → atomic move" leaves a moment with
    no live file. Safer: copy the live file to `backups/`, then atomically move the temp file
    over the live file.
14. **Cooldown bypass.** Is `MyMenu.bypass.cooldown` a child of `MyMenu.*`? If its default is
    op, ops never see cooldowns while testing.
15. **Command dispatch.** Should `PLAYER` actions use `performCommand`, as 1.x did (no
    `PlayerCommandPreprocessEvent`, invisible to other plugins), or go through chat so other
    plugins see them?
16. **Injection.** Should PlaceholderAPI values that players control (nicknames, etc.) be
    parsed as MiniMessage in `<!mm>` strings, or substituted into `CONSOLE` commands?
17. **Chat input.** Paper's chat event is async, so input handling must hop back to the main
    thread. The typed text must also be kept out of public chat. 1.x used a modal Bukkit
    `Conversation`, which suppressed other chat for the admin while it ran. Should 2.0
    suppress it too?
18. **Multi-server MySQL.** Several servers writing to the same tables will overwrite one
    another's edits, and nothing tells the other servers to reload. Is shared editing across
    servers a goal, or is MySQL meant for one writer?

---

## 6. `ARCHITECTURE.md` §12 audit

### Confirmed

- Static conversation state (`Objects/MyMenuItemConversation.java:51-62`).
- Close listener removes the player only when the title lookup succeeds
  (`Listeners/InventoryCloseListener.java:32-35`).
- Four `MyMenuItem` constructors call `this.item.getItemMeta()` before assigning `this.item`
  (`Objects/MyMenuItem.java:40-70`).
- `translateAlternateColorCodes('$', …)` in `Commands/MyMenuInfo.java:72`.
- `removeMenu(String)` checks `index` where it meant `configIndex`
  (`Objects/MyMenuMenu.java:241-243`).
- `PlayerJoinListener` uses `this.player` inside delayed tasks.
- Load-time writes, and no shutdown save (`MyMenu.java:133-152`).
- Material map: seven flowers → `WOOL`; `mossy cobblestone wall` → `WOOD_STEP`
  (`Objects/MyMenuItemConversation.java:150-156,205`).
- `plugin.yml` contains the `commands:` block twice; `/update` is registered globally.
- Totals: `Tools/InfoMenus.java` is 14,744 bytes, and the legacy source is 4,983 lines.

### Overstated

- **Row clamping "threw".** 1.8's `createInventory` only validated `size % 9 == 0`, so a
  72-slot call probably didn't throw on the server. The clamp was also wrong at the bottom:
  `rows < 0 ? 1 : …` let `0` through (size 0) and turned `-1` into rows 1 but size −9.
- **Enchantment clearing and wrong index check.** Both are real, but neither can trigger:
  items are always freshly built with no enchantments, and `setConfigMenu` ignores
  negative indices.

### Missing, should be added

- **Update check blocked the main thread** (§3.2 above).
- **Delete's direct file write did nothing** (§3.4 above).
- **First-run config clobber**, and two in-memory copies of `config.yml` (§3.5 above).
- **Session created before the menu opened.** `new MyMenuPlayer(...)` runs before
  `openInventory`. If another plugin cancelled the open, no close event fired and the
  session leaked (`Listeners/PlayerInteractListener.java:60-62`). 2.0 should create sessions
  only after the open succeeds (`openInventory` returns `null` when cancelled).
- **Middle-click move lost data.** Closing the editor with a picked-up item on the cursor
  dropped a real item into the world or inventory and removed the entry from the menu, which
  the close-time save then persisted (`Listeners/InventoryClickListener.java:192-204`).
- **Players tracked by name, not UUID** (`Objects/MyMenuPlayer.java:86-95`).
- **First match wins** when several menus are bound to the same material
  (`Objects/MyMenuMenu.java:274-291`).
- **One bad entry aborts all loading** (§1.2 above).

---

## Sources

- [PaperMC — Command registration](https://docs.papermc.io/paper/dev/command-api/basics/registration/)
- [PaperMC — Paper plugins](https://docs.papermc.io/paper/dev/getting-started/paper-plugins/)
- [PaperMC — Lifecycle API](https://docs.papermc.io/paper/dev/lifecycle/)
- [PaperMC — Getting started (Java requirements)](https://docs.papermc.io/paper/getting-started/)
- [Minecraft Wiki — Java Edition 26.1](https://minecraft.wiki/w/Java_Edition_26.1)
