# DECISIONS.md

A running log of choices that are not obvious from the code, and what they replaced.
Entries are append-only. Each records what the old plugin did, what this one does, and
why.

Entries 1 to 30 were settled before any code existed. Claude Code adds entries as it
makes further non-obvious calls.

---

## Platform and project

### 1. Paper only, latest version only
**Old:** Built against Bukkit/CraftBukkit 1.8.1, Java 1.7 target.
**New:** Paper only, current release line only.
**Why:** Paper is the mainstream server platform and implements the Bukkit API anyway.
Supporting a version range costs real effort for users who mostly do not exist. Narrowing
the target is what makes Brigadier, `PluginLoader`, and modern item serialisation
available without conditional code.

### 2. No backward compatibility at all
**Old:** n/a.
**New:** No 1.x commands, no 1.x data files, no material alias table, no damage-value
handling, no migration layer.
**Why:** The plugin has not worked since roughly 2015. Anyone still holding a copy has a
non-functional jar, not a running configuration. Carrying compatibility would mean
porting a 220-line material map, writing a flattening converter, dual-registering legacy
commands, and rewriting command strings stored inside menu data. All of that cost, for a
user base that is almost certainly zero. Consequence: this deletes the single largest
chunk of porting work.

### 3. Ground-up rewrite with the old source as reference
**Old:** n/a.
**New:** New codebase; `legacy/` kept read-only for consultation.
**Why:** Most of what makes the plugin interesting (storage abstraction, per-viewer
rendering, per-item permissions) is architectural, not additive. Usage limits are deferred
to a later version, but the same argument applies to them. Retrofitting
those onto 2014 code structured around a shared `Inventory` object is harder than
building them in. The old source remains the authority on intended behaviour.

### 4. GPL-3.0, public repository
**Old:** All Rights Reserved on BukkitDev, closed source.
**New:** GPL-3.0, public on GitHub.
**Why:** Closed source provides essentially no protection for a Java plugin — the
decompiled source that seeded this project proves it. The license, not the repository
setting, is what governs reuse. GPLv3 keeps the plugin free while preventing closed forks.
A public repo also supplies the release feed the update checker reads.

### 5. Gradle, not Maven
**Old:** Eclipse export, no build tool. Libraries referenced by absolute path
(`C:/Documents and Settings/Owner/Desktop/...`).
**New:** Gradle with `run-paper`.
**Why:** `./gradlew runServer` downloads a Paper server, installs the jar, and launches it
in one command. That build-run-read-log-fix loop is the core development cycle for this
project. Maven is conceptually simpler but has no equivalent.

### 6. `paper-plugin.yml` with a loader, no bootstrapper
**Old:** `plugin.yml`, with the entire `commands:` block duplicated and `/update`
registered as a global command.
**New:** `paper-plugin.yml` declaring a `loader`; no `bootstrapper`.
**Why:** The loader resolves HikariCP and the MySQL driver from Maven at load time, which
avoids shading them and the package relocation that shading requires. A bootstrapper would
only buy datapack-function access to commands, which a menu plugin does not need.
Accepted costs: Paper-only and stricter classloader isolation. **Corrected 2026-09-17:**
this entry originally listed "no `/reload`" as settled. Paper's docs only say reload is
disabled in certain situations; whether registering `COMMANDS` from `onEnable` is one of
them is unverified. Confirm empirically at stage 1.
**Corrected 2026-09-18:** confirmed at stage 1 — a `COMMANDS` handler registered from
`onEnable` does **not** disable Bukkit's reload. See #59.

### 7. Package renamed to lowercase
**Old:** `me.ChaddTheMan.MyMenu`.
**New:** `me.chaddtheman.mymenu`.
**Why:** Package names map to directories, and mixed case breaks on case-insensitive
filesystems. Java convention is lowercase for a reason.

### 8. Semantic versioning from 2.0.0
**Old:** `1.0.4.3`, four segments, no defined meaning.
**New:** `2.0.0`, major/minor/patch.
**Why:** Standard, machine-comparable, and the major bump honestly signals that nothing
from 1.x carries over.

---

## Commands

### 9. One root command with subcommands
**Old:** 13 top-level commands (`/mmopen`, `/mmedit`, `/mmset`, …) plus a global `/update`.
**New:** `/mymenu <subcommand>`, aliased `/mm`.
**Why:** Thirteen top-level commands claim thirteen server-wide names. `/update` in
particular squatted on an extremely generic word and would collide with other plugins.
A single root also allows generated help and permission-filtered discovery.

### 10. Brigadier rather than a classic dispatcher
**Old:** `CommandExecutor` per command, no tab completion at all.
**New:** Brigadier tree registered via `LifecycleEvents.COMMANDS` in `onEnable`.
**Why:** Typed arguments, client-side suggestions as you type, error highlighting that
points at the offending word, and permission-gated nodes. For a plugin whose stated
identity is "easy to use," having no tab completion would be conspicuous. The two
arguments against Brigadier — dual-registering legacy aliases, and added complexity
during a port — both disappeared once backward compatibility was dropped.

Reversal noted: an earlier plan favoured a classic dispatcher with Brigadier layered on
later. That was rejected because routing Brigadier into a `String[]` dispatcher keeps the
suggestions but loses typed parsing and error positions, paying the complexity for half
the benefit.

### 11. Brigadier confined to the edge
**New:** Subcommand classes receive resolved, typed parameters
(`execute(sender, Menu, int, String)`), never `String[] args`.
**Why:** Confines generics-heavy builder code to one file. Subcommand logic never parses,
validates, or checks permissions, so it stays short, readable, and testable. Also removes
the largest source of duplication in the original, where all 13 command classes repeated
the same sender/permission/argument-count checks.

### 12. Permission nodes unchanged
**Old:** `MyMenu.admin.menu.open`, etc.
**New:** Identical, plus `MyMenu.admin.menu.give`, `MyMenu.admin.joinmenu`, and
`MyMenu.bypass.cooldown`.
**Why:** Command names are typed fresh every time; permission nodes sit in LuckPerms
databases and group files that owners would have to edit by hand. Renaming commands is
cheap, renaming permissions is not.

### 13. `set`/`unset` kept; `unset` becomes its own subcommand
**Old:** `/mmset [menu] none` to unbind, with the `unset` permission checked inside the
`set` handler.
**New:** `/mymenu unset <menu>`.
**Why:** The permission check belongs where the operation lives, and the operation becomes
discoverable through tab completion instead of only through documentation.

### 14. Positional arguments, not flags
**Old:** `/mmcreate [Menu] (Rows) (Name)`, parsed by trying `Integer.valueOf(args[1])` and
treating it as a title if that threw.
**New:** Positional, with typed optional Brigadier branches.
**Why:** The ambiguity that made flags attractive was an artifact of manual string
parsing. Typed argument branches resolve it without the verbosity.

---

## Architecture

### 15. The model is not the view
**Old:** `MyMenuMenu` held one `Inventory`, shared by all viewers.
**Corrected 2026-09-17:** an earlier version of this entry said 1.x saved by reading items
back out of that shared inventory. It did not — `saveInventoryItems` iterated a
`HashMap<Integer, MyMenuItem>` model. The shared inventory was a display problem, not a
persistence problem. The decision stands; the justification was partly wrong.
**New:** `Menu` is data; `MenuRenderer` produces a fresh `Inventory` per viewer.
**Why:** The single most consequential change. Per-item view permissions and
PlaceholderAPI both require per-viewer content, which one shared inventory cannot provide.
It is also what makes a database backend, preview, undo, and testing possible at all.

### 16. Identify menus by `InventoryHolder`
**Old:** `getMenuIndexByInventory(inventory.getName())` — title-string matching.
**New:** A `MenuHolder` carried by the inventory.
**Why:** `Inventory.getName()` no longer exists. Titles were never unique, could be
duplicated across menus, and the lookup failing is what caused the session-leak bug.

### 17. No static mutable state
**Old:** `static ArrayList` registries, and the entire in-progress item conversation held
in `static` fields.
**New:** Instance state owned by the plugin object.
**Why:** The static conversation state meant two admins building menus simultaneously
overwrote each other's slot, menu, and lore counters, silently writing one admin's item
into the other's menu. Never reproducible by a single tester, catastrophic on a staffed
server.

### 18. Unconditional session cleanup
**Old:** `InventoryCloseListener` removed the player only when a title lookup succeeded.
**New:** Removal on close and quit, unconditional, with cleanup in `finally`.
**Corrected 2026-09-17:** superseded by #31. Unconditional close cleanup is
self-contradictory — `MENU` actions, the property editor, and chat prompts all close the
inventory as a side effect. Quit cleanup and `finally` cleanup remain unconditional.
**Why:** The conditional path leaked sessions, and `PlayerInteractListener` then refused
to open menus for anyone holding a stale session. That is the reported "You are currently
already using/editing a menu!" lockout, which persisted until relog.

### 19. All storage I/O off the main thread
**Old:** Config was mutated and saved *during* loading, inside the load loop, on the main
thread.
**New:** Async I/O returning futures, hopping back to main for any Bukkit API call.
**Why:** Blocking the server thread on disk or network is the standard way a plugin gets
blamed for lag. Mandatory once MySQL is an option.

### 20. Listeners hold no per-event state
**Old:** Event player, inventory, and slot stored as instance fields on singleton
listeners.
**New:** Locals and parameters only.
**Why:** Mostly harmless within one synchronous handler, but `PlayerJoinListener` used
`this.player` inside a 100-tick delayed task, so two players joining within five seconds
both received the message intended for one.

---

## Data and behaviour

### 21. `commands:` renamed to `actions:`
**Old:** A list of command strings with `$`, `\`, and `none` conventions.
**New:** A list of typed action objects.
**Why:** With `MENU`, `BACK`, `CLOSE`, and `DELAY` in the set, most entries are not
commands. The field name was lying. Explicit types also remove the `\$` ambiguity, where
a message prefixed with a console marker silently became a console command.

### 22. Prefixes survive as input shorthand only
**New:** `$`, `\`, `!`, and bare are accepted when typing in-game, then converted to an
explicit `type:` field.
**Why:** Fast to type, unambiguous once stored. Also permits commands that legitimately
begin with `$`.

### 23. `PLAYER_ELEVATED` via temporary attachment
**Old:** Not possible. Users had to choose between console (wrong player context) and
player (permission denied). Two separate commenters hit this.
**New:** Temporary `PermissionAttachment`, revoked in `finally`.
**Why:** The common alternative — op the player, run the command, de-op — leaves a player
opped if anything throws in between.

### 24. `OTHER` as the click-type fallback
**New:** Actions keyed by click type, with `OTHER` catching unlisted types. Fallback only,
never additive; exactly one list runs per click.
**Why:** `ANY` was rejected because it invites reading it as "run in addition to the
specific ones," which is not the behaviour. `OTHER` states the semantics in the word.
`DEFAULT` was the runner-up but reads as programmer jargon.

### 25. Full-item binding, `TYPE_AND_NAME` matching by default
**Old:** Bound items stored a material name, optionally with a specific display name
(`/mmset <menu> <material> <name...>` set `boundItemName` and `isSpecific`). Default
matching compared `getType()` alone, so any compass opened a compass-bound menu.
**Corrected 2026-09-17:** an earlier version of this entry said 1.x matched on type only.
It supported the equivalents of both `TYPE` and `TYPE_AND_NAME`. The real changes are the
default moving to `TYPE_AND_NAME` and capturing the whole ItemStack.
**New:** The whole `ItemStack` is stored. A hidden persistent-data tag is checked first;
the configurable match mode applies only to items the plugin did not hand out.
**Why:** `EXACT` fails invisibly when a tool takes one point of damage or another plugin
appends a lore line, producing "the menu item randomly stopped working" reports.
`TYPE_AND_NAME` tolerates both, and its own failure mode (an unrelated item sharing
material and name) is harmless.

### 26. Readable-first item storage, opaque fallback
**New:** Descriptive YAML fields when an item can be expressed by them; a serialised blob
when it cannot. Never both in one slot. MySQL always uses the serialised form.
**Why:** Four fields cannot represent player-head textures, potion data, banner patterns,
or custom model data — and item-from-hand editing means arbitrary items are now the main
data path. Readable-only would silently destroy them; blob-only would make `menus.yml`
uneditable. Storing both would let the two drift, producing "my YAML edits do nothing"
reports.

### 27. Navigation pushes rather than unwinds
**New:** A `MENU` action targeting a menu already in the stack pushes a new entry. Depth
capped at 10.
**Why:** Predictable. Unwinding feels smoother in simple cases and becomes strange fast.
The cap prevents A→B→A loops from running away.

### 28. Hidden items leave empty slots
**New:** An item the viewer lacks permission for leaves the slot empty, or renders an
optional `hiddenFallback`. The menu never compacts.
**Why:** Compacting means every player sees a differently-shaped menu, breaking the shared
mental model of a fixed layout. The fallback (usually a grey pane reading "requires rank
X") is what admins generally want anyway.

### 29. Cooldowns in memory, delays capped, sequences cancelled on logout
**New:** Per-player cooldowns cleared on restart. Total delay per sequence capped at 30
seconds, configurable. Pending sequences cancelled on logout, continued through death and
world change. Repeat clicks during a pending sequence are ignored.
**Why:** Cooldowns exist to stop spam-clicking within a session, so persisting them would
add a storage table and expiry cleanup for no real benefit. Running `PLAYER` actions for
an offline player throws, hence cancel-on-logout. Ignoring repeat clicks is simpler than
queueing, and the cooldown feature covers the spam case properly.

### 30. Dead dependencies replaced
**Old:** MCStats/PluginMetrics (mcstats.org is defunct) and a CurseForge auto-updater that
downloaded and replaced the plugin jar.
**New:** bStats for metrics; a notify-only update check against GitHub releases.
**Why:** Both original libraries target services that no longer exist — together roughly
1,000 of the original 4,983 lines. Self-downloading updates are also a supply-chain
hazard that modern operators expect to control themselves.

---

## Post-audit (entries 31 onward)

Added after the pre-implementation audit recorded in `AUDIT.md`. Entries 15 and 25 above
carry dated corrections from the same audit.

### 31. Sessions derive state rather than trust tracked state
**Old:** A `MyMenuPlayer` entry in a static list was authoritative. A leaked entry locked
the player out of every menu until relog.
**New:** A view session is valid only if the player currently has a `MenuHolder` inventory
open, or is in a known transient state. Validated on read.
**Why:** An earlier draft required unconditional cleanup on `InventoryCloseEvent`, which is
self-contradictory — `MENU` actions, the property editor, and chat prompts all close the
inventory as a side effect, so the rule would clear the navigation stack on every menu
change and destroy prompts as they started. Reason-aware cleanup fixes that, but tracking
is still what leaked in 1.x. Consulting reality makes the leak harmless. Cleanup remains,
as an optimisation rather than a correctness requirement.

### 32. Load failures put storage in a degraded, write-refusing state
**Old:** One unparseable entry threw and aborted the entire load.
**New:** Skip the entry, log it, refuse **all writes** until `/mymenu reload` clears it.
**Why:** Skipping bad entries and saving later rewrites the file without them — silent,
total, unrecoverable data loss. Refusing writes makes that structurally impossible. Chosen
over preserving unparsed YAML nodes because it is far simpler and fails loudly.

### 33. `onDisable` drains in-flight writes; it never initiates a save
**Old:** No shutdown save at all.
**New:** Storage owns its own `ExecutorService`. `onDisable` calls `shutdown()` plus
`awaitTermination` with a bounded timeout.
**Why:** Blocking on a save in `onDisable` would violate the no-main-thread-I/O rule, and
Bukkit's scheduler stops accepting tasks during disable. Since every mutation already
writes immediately, there is nothing to flush — only in-flight work to drain. This is the
single documented exception to that rule, and it is a narrow one.

### 34. `{SERVER}` reads a `serverName` config key
**Old:** `Server#getName()`, which returns the software name.
**New:** A `serverName` setting in `config.yml`.
**Why:** The spec claimed to fix the 1.x bug by reading `server.properties`, but modern
`server.properties` has no server-name key. There is no source for this value other than
one the admin supplies.

### 35. Substitution applies to command values, and happens after parsing
**Old:** 1.x applied wildcards **only** to command strings, and also translated `&` colour
codes inside them, mangling any command containing an ampersand.
**New:** Wildcards and PlaceholderAPI apply to names, lore, messages, **and command
values**. Colour translation does not apply to command values. Substituted values are never
re-parsed as MiniMessage, and values entering commands are sanitised for newlines,
semicolons, and leading slashes.
**Why:** An earlier spec draft listed names, lore, and messages but omitted command values,
which would have broken `$give {PLAYER} diamond 64` — the primary use case for the entire
wildcard feature. The sanitisation half is a security requirement: placeholder output and
nicknames are player-controlled, and substituting them into a `CONSOLE` action before
parsing is command injection.

### 36. Bound items and rendered icons use different tag keys
**New:** Two persistent-data keys, not one.
**Why:** Creative-mode players can clone an icon out of an open menu. With a shared key,
every cloned icon would become a working menu opener.

### 37. Menus carry a revision counter
**New:** `MenuHolder` captures the revision at render time; a mismatch on click refuses the
click and re-renders.
**Why:** Edits are not pushed to open screens (decision: they are seen on reopen). Without
a revision check, a player could click an icon they can still see and trigger whatever
replaced it. Also covers delete and reload while a menu is open.

### 38. `MENU`, `BACK`, and `CLOSE` are scheduled for the next tick
**Why:** Opening or closing an inventory from inside `InventoryClickEvent` is unsupported
and misbehaves. Not a style preference.

### 39. Glow uses the enchantment glint override
**Old:** 1.x deliberately stripped enchantments.
**New:** `ItemMeta#setEnchantmentGlintOverride` (1.20.5+).
**Why:** An earlier draft specified a fake enchantment plus the hide-enchantments flag,
which is the old workaround. It would also have collided with the rule that real
enchantments force an item into the serialized storage form.

### 40. `joinMenu` replaces per-menu `openOnJoin`; `giveItemOnJoin` moves to the menu
**Old:** Both were per-menu keys in `config.yml`. With several menus setting `OpenOnJoin`,
the last in the list silently won. In-game changes to either were never persisted.
**New:** One `joinMenu: <name>` in `config.yml`. `giveItemOnJoin` becomes a field on the
`Menu` model.
**Why:** A single value removes the ambiguity rather than inventing a tiebreak rule.
`giveItemOnJoin` is menu data and must travel with the menu when storage is MySQL,
otherwise it would stay local to one server while the menus are shared.

### 41. `create` drops the menu-type argument
**Old:** n/a.
**New:** `create <menu> [rows] [title...]`, chest by default; type set in the editor.
**Why:** An optional enum before a greedy string makes `/mymenu create shop Chest Shop`
ambiguous, resolved only by the order the Brigadier branches happen to be built. Removing
the argument removes the ambiguity instead of documenting it.

### 42. Menu names are restricted; titles are not
**Old:** Length-validated only (32 characters).
**New:** Names are `[a-z0-9_-]`, lowercased on input, max 32. Titles keep full colour,
Unicode, and length freedom.
**Why:** `.` breaks YAML paths, and mixed case invites collisions between `Shop` and
`shop`. The restriction applies to the identifier, never to what players see.

### 43. Delete writes a backup instead of asking for confirmation
**Old:** No confirmation, no backup, and open views of the deleted menu were left open.
**New:** Backup to `backups/deleted-<menu>-<timestamp>.yml`, then close open views.
**Why:** A recoverable copy beats a prompt people learn to click through.

### 44. Save order is copy-then-move, not rotate-then-move
**New:** Copy the live file into `backups/`, write a temp file, atomically move the temp
over the live file. Retention bounded by `backups.keep` (default 10).
**Why:** Rotating the live file out first leaves a window with no live file at all. The
retention bound exists because saving on every mutation would otherwise grow `backups/`
without limit.

### 45. Assorted rulings recorded for the record
- `MyMenu.bypass.cooldown` sits **outside** `MyMenu.*` and defaults to false, so ops
  experience cooldowns while testing them.
- `MyMenu.help.player` defaults to true; everything else defaults to op. 1.x declared no
  defaults, making even player help op-only.
- `DOUBLE_CLICK` never falls through to `OTHER`, because the client sends a `LEFT` click
  first and both lists would run.
- Repeat-click suppression during a pending sequence is **per player**, not per item.
- Over-cap delay lists are clamped with a warning, never rejected.
- `TYPE_AND_NAME` compares plain text with colour stripped, consistent with why the
  forgiving mode is the default.
- `PLAYER` actions dispatch via `Player#performCommand`, which does not fire the command
  preprocess event.
- Editor placement **copies** items, replaces occupied slots, cancels shift-click, and
  returns cursor items to the inventory on close. 1.x dropped them into the world and
  deleted the menu entry.
- Slot-to-slot moves are an operation in the property GUI, replacing 1.x's data-losing
  middle-click move.
- Players are tracked by UUID, never by name.
- Sessions are created only after `openInventory` returns non-null.
- Bound items open on both left- and right-click, main hand only.
- MySQL is single-writer in 2.0.0; cross-server synchronisation is deferred.
- `reload` cannot switch `storage.type` at runtime.
- Release tags are `v<semver>`, compared semantically. 1.x used string inequality, so an
  older remote version registered as an update.

---

## Post-consistency-check (entries 46 onward)

### 46. Stale-view checking uses a name lookup and a global revision counter
**New:** The holder records the menu name and a revision from one plugin-wide counter.
**Why:** #37 had the holder keep the `Menu` object and a per-menu counter, which cannot
detect delete or reload — both leave the old object untouched with its revision intact — and
a delete-then-recreate could reissue the same number. Edit mode skips the check, or an
admin's own edit invalidates their next click. Re-rendering happens next tick, because #38
forbids opening an inventory inside a click handler; #37 violated #38.

### 47. The degraded-state gate moves to `MenuService`
**New:** Mutations are refused before reaching the model. Storage only reports the
condition.
**Why:** #32 put the refusal in storage, which is too late: the model has already changed,
so the edit survives in memory until the next reload silently discards it. That is the exact
data-loss shape the rule exists to prevent, and it contradicted the claim that reload cannot
discard unsaved work. Runtime write failures also degrade, not just load-time parse
failures. Read paths are unaffected.

### 48. Writes are debounced, and backups are rate-limited separately
**New:** `storage.writeDebounceMillis` (2000) coalesces rapid edits;
`backups.minIntervalSeconds` (300) caps backup frequency; delete backups are exempt from
`backups.keep`.
**Why:** Writing on every mutation with `keep: 10` meant roughly ten clicks on the amount
control pushed every useful backup out of retention, which is worse than having no backups
at all because it looks like protection.

### 49. `onDisable` flushes before draining
**New:** `flush()`, then `shutdown()` and `awaitTermination`.
**Why:** #33 argued there was nothing to flush because every mutation wrote immediately.
Debouncing (#48) makes that false. `/mymenu reload` also flushes and waits before re-reading,
or it reads a half-written file. The exception to the no-main-thread-I/O rule stays single
and bounded.

### 50. Edit mode is a render mode, not just a flag on a session
**New:** `MenuHolder` carries `VIEW` or `EDIT`. Edit mode bypasses view-permission
filtering, marks gated items, interprets clicks as editing operations, and skips the
stale-view check.
**Why:** The renderer applied view permissions unconditionally, so an admin lacking an
item's node could not see it to edit it. Three separate gaps — no mode field, no permission
bypass, no click exception — meant the editor as specified could not function.

### 51. `/mymenu joinmenu <menu|none>` added
**Why:** `joinMenu` had no in-game setter, contradicting the promise that hand-editing is
never required. It is the only runtime writer of `config.yml`, which is acceptable for one
scalar. `joinMenu` stays per-server rather than moving onto the menu, so servers sharing a
MySQL database can open different menus. Permission node `MyMenu.admin.joinmenu`.

### 52. Delete cascades
**New:** Ends edit sessions and prompts on the menu, purges it from navigation stacks,
cancels pending `MENU` actions targeting it, and warns if `joinMenu` names it.
**Why:** #43 covered the backup and closed open views, and stopped there. Every other
reference to a deleted menu would have dangled.

### 53. Storage uses a single-threaded executor; snapshots are taken on the main thread
**Why:** A single thread gives write ordering for free. Models are main-thread-only, so the
whole-file snapshot a YAML `save(Menu)` needs must be taken before the async hop. Neither
was stated and both are easy to get wrong silently.

### 54. Glow lives only on `ItemTemplate`
**Why:** It appeared on both `ItemTemplate` and `MenuItem`, and the serialized item form had
nowhere to put it. One location, valid alongside `serialized:`, applying identically to both
shapes.

### 55. Rulings recorded during the consistency check
- `set` stores a prototype and does **not** tag the admin's held item; `give` hands out
  tagged copies.
- Chat input cancels only the admin's own typed line. Other players' chat is not suppressed
  to the admin, as 1.x's modal conversation did.
- `joinMenu` naming a missing menu: warn once at load, open nothing.
- `giveItemOnJoin: true` on a menu with no bound item: warn at load, give nothing.
- Players with an active session are skipped on join, as in 1.x.
- Wildcards are upper-case and case-sensitive; message placeholders are lower-case. Separate
  systems, no interchange.
- Serialized items are edited by deserialize, modify metadata, re-serialize.
- Bound items open on left- or right-click, main hand only; the off-hand event is ignored.
- Menus stay open after a click unless a `CLOSE` action runs. 1.x closed before running any
  command.
- Location wildcards are block coordinates. 1.x used exact doubles.
- 1.x's conversation limits are dropped: 10 commands, 10 lore lines, 45 characters per line,
  32-character names and titles. Only the menu-name rules in #42 remain.
- `AsyncChatListener` is a listener in its own right; Paper's chat event is async and
  handling hops to the main thread.
- `CHANGELOG.md` is bundled into the jar as a resource.
- `create` receives a validated name rather than a `Menu`, since its menu does not exist yet.
- `InventoryCloseEvent.getReason()` is unverified against current Paper. Confirm before
  relying on it.

---

### 56. bStats: nine charts, shapes only, global opt-out
**Old:** MCStats/PluginMetrics, targeting a service that has been dead for years.
**New:** bStats, plugin ID 34120, with nine custom charts covering storage backend,
bucketed menu and item counts, menu types, action types, match modes, optional-feature
adoption, PlaceholderAPI presence, and a total-menus line.
**Why:** Every chart answers a question that would change a future decision; anything else
is noise. Counts are **bucketed** because a pie of exact values produces a hundred
one-server slices and says nothing.

**Privacy is a hard constraint, not a preference:** bStats pages are public, so the plugin
reports shapes and never values. Action types yes, action values never — a stored command
can contain player names or addresses. No menu names, permission nodes, item names, or
server names. Nothing per-player.

**Opt-out is bStats' own server-wide toggle only.** A second plugin-level switch implies
granularity that does not exist and creates a split-brain state where one setting says yes
and the other no.

**Operational note:** custom charts must be created on the bStats website with IDs matching
the code. A chart that exists only in code shows "No data to display" forever, silently.

### 57. The update checker is written fresh, and notify-only
**Old:** 536 lines targeting the BukkitDev/Curse API, architected around downloading and
installing a replacement jar.
**New:** ~80 lines against the GitHub releases API. Reads `tag_name`, strips the leading
`v`, compares semantically, logs and notifies.
**Why:** The old service no longer exists, and self-replacing jars are a supply-chain
hazard operators expect to control. Semantic comparison matters: 1.x used
`!equalsIgnoreCase`, so an *older* remote version registered as an update.

Three constraints that shape the implementation: GitHub returns **404 when a repository has
no releases** (true for this repo until 2.0.0 ships) and that must be silent, not an error;
the unauthenticated rate limit is **60 requests per hour per IP**, which shared hosts share
between many servers, so results are cached and rate-limiting is treated as "unknown"; and
GitHub **rejects requests with no `User-Agent`**.

---

## Implementation (entries 58 onward)

### 58. Target Paper 26.2, the newest stable build, not 26.3
**Old:** n/a.
**New:** `paper-api 26.2.build.124-stable`, `api-version: '26.2'`, and `runServer` on 26.2.
**Why:** On 2026-09-18, 26.3 exists but every build is on Paper's ALPHA channel, and the
newest STABLE build is 26.2-124. SPEC §2 says "latest release line (26.x)", which 26.2
satisfies. Compiling against an alpha API would risk building on methods that change before
release. Paper's own plugin docs use `api-version: '26.2'`. Move to 26.3 once it has a stable
build: that means changing two values at the top of `build.gradle.kts`. Note that API
artifacts now follow `<mc>.build.<n>-stable`, not the old `-R0.1-SNAPSHOT` form, so older
tutorials give coordinates that do not resolve.

### 59. `/reload` on 26.2: what actually happens
**Old:** n/a.
**New:** Verified empirically at stage 1 on Paper 26.2-124:
- A bare `/reload` is **Mojang's datapack reload** (`/help reload` reports "A Mojang
  provided command"). Bukkit's plugin reload is reachable only as `/bukkit:reload`.
- The datapack reload fires the `COMMANDS` handler again with `cause=RELOAD`. Commands keep
  working afterwards. This confirms that registration must be idempotent (ARCHITECTURE §8).
- `/bukkit:reload confirm` is **not** disabled by a `COMMANDS` handler registered in
  `onEnable`. It disables MyMenu, builds a **new plugin instance** in a new classloader,
  enables it, and fires the new instance's handler once with `cause=RELOAD`. The old
  handler does not fire. Paper logs a warning that Paper plugins do not support reloading.
**Why it matters:** #6 and ARCHITECTURE §8 left this open. The consequence is that server
owners *can* hot-reload MyMenu. `onDisable` therefore has to leave nothing behind: no
running storage executor, no scheduled tasks, no open inventories holding a stale
`MenuHolder`. A second instance may start in the same JVM moments later. The warning is
Paper's, so MyMenu adds no second one.

### 60. Library versions live once, in the build; the loader ignores the JSON's repositories
**Old:** n/a.
**New:** HikariCP and the MySQL driver are declared as `paperLibrary` in `build.gradle.kts`.
The `de.eldoria.plugin-yml` fork writes them to `paper-libraries.json` in the jar, and
`MyMenuLoader` reads **only the dependency list** from that file. The loader always resolves
against `MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR`.
**Why:** Hard-coding coordinates in the loader would mean a second copy of each version to
keep in step. The generated JSON also lists `repo.maven.apache.org` as a repository, and
using that would break Central's terms (ARCHITECTURE §11), so the file's repository map is
deliberately unused. Because of that, the build's "No mavenCentralProxy configured" warning
is harmless. `useDefaultCentralProxy()` would silence it, but it would also write proxy URLs
into the JSON that nothing reads. The original `net.minecrell.plugin-yml` has had no release
since 2023; the Eldoria fork is the maintained one, and it still uses the `net.minecrell`
package names in the DSL.

Shadow (`com.gradleup.shadow` 9.6.1, verified) is **not applied yet**. It exists only to
shade bStats, which arrives at stage 10.

---

## Template for new entries

```
### N. Short title
**Old:** What 1.0.4.3 did, or n/a.
**New:** What this version does.
**Why:** The reasoning, including what was rejected and the cost of the choice.
```
