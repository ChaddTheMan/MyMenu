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

### 61. Models are immutable; the registry is copy-on-write
**Old:** `MyMenuMenu` and `MyMenuItem` were mutable and reachable from everywhere.
**New:** `Menu`, `MenuItem`, `ItemTemplate` and `BoundItem` are records with `with...`
methods. An edit builds a new `Menu`, and `MenuService` puts it into `MenuRegistry`. The
registry's write methods and its constructor are package-private to `service/`. Revisions are
held by the registry, not stamped on the `Menu`. Each registry write publishes a new
immutable snapshot through a `volatile` field.
**Why:** ARCHITECTURE §3 says models are "mutable from the main thread only" and have the
revision "stamped on the menu". But the rule that only `MenuService` mutates menus cannot be
enforced with mutable models. `Menu` is in `model/`, `MenuService` is in `service/`, and
package-private access does not cross packages, so any setter would have to be public.
Immutability makes the rule structural. It also makes two other things free: the
main-thread snapshot that #53 requires before the async hop is just a reference, and
registry reads become safe from any thread. That resolves ARCHITECTURE §8's open risk about
suggestions being computed off the main thread. The revision is not menu data (it is never
saved, and two equal menus loaded at different times must differ), so it lives next to the
menu rather than inside it.
**Cost:** every edit allocates a new menu and copies the registry map, which is trivial at
realistic menu counts. The `with...` methods are boilerplate. `MenuService.update` compares
the old and new menu and commits nothing when they are equal, so a no-op edit does not bump
the revision and invalidate open views. The enforcement boundary is the whole `service/`
package, not the one class, so that package should stay small.

### 62. Click keys are MyMenu's own enum; click sounds are stored as keys
**Old:** n/a.
**New:** `MenuItem` maps `ClickKey` (MyMenu's enum) to action lists, not Bukkit's
`ClickType`. `clickSound` is an Adventure `Key`, not `org.bukkit.Sound`.
**Why:** ARCHITECTURE §1 and §3 specify `Map<ClickType, List<Action>>`, which cannot hold
SPEC §8.4's `OTHER` key. Verified against `paper-api 26.2.build.124-stable`: `ClickType` has
`LEFT, SHIFT_LEFT, RIGHT, SHIFT_RIGHT, WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT, MIDDLE,
NUMBER_KEY, DOUBLE_CLICK, DROP, CONTROL_DROP, CREATIVE, SWAP_OFFHAND, UNKNOWN` and no
`OTHER`. Mapping the Bukkit constants that have no key of their own (`CONTROL_DROP`,
`SWAP_OFFHAND`, the window-border clicks, `CREATIVE`, `UNKNOWN`) onto keys is a stage 6
decision. On 26.2, `org.bukkit.Sound` is an interface (`extends OldEnum<Sound>`), not an
enum, so storing it would tie the model to a registry lookup and a deprecated `valueOf`
path. A `Key` is plain data. Stage 3 decides the YAML spelling. SPEC §5.3 shows
`UI_BUTTON_CLICK`; storing `minecraft:ui.button.click` would be the direct form.
**Corrected 2026-09-18:** settled before stage 3; SPEC §5.3 and §8.3 now show the key form.
`menus.yml` holds keys only; a bare `UI_BUTTON_CLICK` in the file is a load error for that
slot. Accepting bare names is an editor input affordance (stage 8). Keys are not checked
against the sound registry, because resource packs add sounds the server does not know.

### 63. Layout changes that would strand items are refused
**Old:** `setRows()` computed a size from the unclamped parameter (ARCHITECTURE §12).
**New:** Every `Menu` guarantees that each occupied slot is inside its layout.
`MenuService.changeLayout` refuses with `ITEMS_OUTSIDE_LAYOUT` when a type or row change
would leave items outside the menu.
**Why:** Neither document says what happens when a six-row chest with items in row six
becomes three rows or a hopper. Dropping the items is silent data loss. Keeping them invisible
means the menu shows something different from what it holds, and the items reappear on a
later resize. Refusing makes the admin move or delete them first, which the property editor
supports. The invariant also means the renderer and storage never have to handle an
out-of-range slot.

### 64. `MenuService` talks to a narrow `MenuPersistence`, not to `MenuStorage`
**Old:** n/a.
**New:** `MenuService` depends on `MenuPersistence` (`isDegraded`, `markDirty(name)`,
`markDeleted(menu)`), declared in `service/`. The stage 3 debounced writer implements it over
ARCHITECTURE §7's `MenuStorage`.
**Why:** ARCHITECTURE §7.2 says a mutation "marks its menu dirty and schedules a write" but
does not say who owns the debounce. `MenuStorage` does I/O and returns futures. If
`MenuService` called it directly, either every edit would write immediately, undoing #48, or
the debounce timer would live in the mutation gate, mixing write policy into it. With the
split, `MenuService` only records intent and never handles a future. The interface is declared
by the service that calls it, so stage 3 depends on `service/` rather than the reverse.
`markDeleted` receives the removed `Menu` because the delete backup (SPEC §3.5) needs its
contents after the registry has dropped it.
**Corrected 2026-09-18:** at stage 3, `markDirty(name)` became `markDirty(Menu)`, for the
same reason `markDeleted` takes a `Menu`: the writer holds the newest version itself and never
reads the registry back. That also removed a construction cycle (writer → `MenuService` →
registry → writer). See #65.

### 65. `MenuStorage` as built differs from ARCHITECTURE §7's sketch
**Old:** n/a.
**New:** `loadAll()`, `saveAll(Collection<Menu>)` (add or replace each by name),
`delete(Menu)`, `isDegraded()`, `flush()`, `close()`. There is no `save(Menu)`, and `delete`
takes the menu rather than its name. `YamlMenuStorage` keeps its own image of what the file
should hold, confined to its thread, instead of reading the registry. `flush()` on storage
means "wait for the queue and retry a failed write once"; the pending batch lives in
`DebouncedMenuWriter`, whose own `flush()` submits it and then calls storage's.
**Why:** The sketch predates debouncing. A debounce window yields a batch, and for YAML each
call rewrites the whole file, so `save(Menu)` per menu would mean several rewrites per batch
and was dropped as unused. `delete(String)` could not write the delete backup, because the
registry has already dropped the menu (#64). The image lets a YAML write proceed without the
registry, which lives behind `MenuService`.

The writer's timer starts at the first change and is **not** restarted by later ones. A
classic debounce waits for a quiet gap, so someone editing faster than
`writeDebounceMillis` for ten minutes would write nothing for ten minutes. This one writes at
most one interval after the first unsaved change. The timer is a Bukkit task. That is safe even
though the scheduler stops during disable, because `flush()` cancels the timer and submits the
batch directly. Deletes are submitted before saves, so deleting and recreating a name within
one window lands in the right order.

### 66. Readable or serialized is decided once, at capture, by a round trip
**Old:** n/a (1.x stored a material name).
**New:** The writer never chooses a form; it writes whichever `ItemTemplate` variant it is
given. The choice is made when a real `ItemStack` becomes a template
(`ItemSerializer.capture`). Build the readable candidate: material, amount, custom name and
lore converted to `&`-code strings, glint override as `glow`. Turn it back into an
`ItemStack` and keep it only if `candidate.equals(original)`. Otherwise store the bytes. A
glint override of `true` is moved out of the bytes into `glow`, or turning glow off in the
editor would have no effect. A name or lore line starting with `<!mm>` also forces bytes, since
it would be re-parsed as MiniMessage at render time.
**Why:** `ItemStack#equals` compares type, amount and every data component, so the check
fails closed. Anything the readable fields cannot hold makes the stacks unequal, including
components added in future versions. A list of "unsupported components" would fail open and
need updating every release. A false "no" costs only readability; a false "yes" would lose
data, and the round trip cannot produce one.
**Cost, and a dependency on stage 9:** text uses Adventure's legacy serializer (`&`, `&#hex`)
with no italic handling. So an item whose name carries an explicit `italic: false` goes to
bytes. That is common for items made by other plugins. If `TextService` renders readable names
non-italic by default, capture must apply the same rule, or those items will be stored as
bytes needlessly. Verified at runtime on 26.2: plain, named-with-lore and glint-only items
came out readable; an enchanted sword, an `italic: false` name, `R&D` and a `<!mm>` name came
out as bytes. The sword's bytes deserialised back equal to the original minus its glint.

### 67. What a malformed `menus.yml` does
**Old:** One unknown material threw and stopped the whole load.
**New:** A problem in a menu's own fields (name, type, rows, author UUID, bound item) skips
that menu. A problem inside a slot skips that slot, and a slot outside the layout is caught
before the `Menu` constructor sees it, so the rest of the menu loads. An unknown key is
reported but the entry is kept. A YAML syntax error or a duplicate key loads nothing. **Any**
of these puts storage into the degraded state. Every message names the menu and the slot.
Both catch boundaries catch `RuntimeException`, not only the expected types.
**Why:** Unknown keys degrade because the next save would drop them silently; a typo such as
`itemlore` would lose the lore. Duplicate keys are rejected (`allowDuplicateKeys=false`)
because SnakeYAML otherwise lets the second copy silently win.

Menus are **built on the main thread**, not the storage thread. The YAML is read and parsed
off-thread, handed to the main thread to become models, then handed back. `Material.isItem()`
on 26.2 calls `asItemType()`, which is a registry lookup, and `ItemTemplate.Descriptive`'s
constructor calls it. The other threading rules forbid Bukkit API off the main thread, and the
registry's lookup cache is not known to be thread-safe.

Serialized items are **carried as bytes and not deserialised at load**. A blob that fails to
deserialise, perhaps because of a datapack enchantment that is not loaded yet, is kept and
saved back unchanged. It is not skipped. The renderer (stage 4) must handle a blob that fails.
Invalid base64 is still a load error.

Until stage 6, `ActionCodec.NONE` makes any stored action a load error for its slot. That
degrades rather than dropping the action. Empty action lists (`LEFT: []`, or a bare `LEFT:`)
load and save.

Verified 2026-09-18 with a hand-written file containing a bad material, `rows: 9`, a slot at
40 in a one-row chest, `serialized` beside `material`, an action, a typo key, a bare sound
name, the slot key `x5`, invalid base64, `amount: 500`, `AIR`, and the menu name `Bad Name`.
Twelve problems were logged, each naming its location. Four menus loaded, with exactly their
good slots. Edit and create were refused with `STORAGE_DEGRADED`. `menus.yml` was
byte-identical afterwards, and no backup was taken.

### 68. What a failed write leaves behind
**Old:** n/a.
**New:** A write is: render the text, copy the live file to `backups/` (subject to the rate
limit), write `menus.yml.tmp` and `force()` it, then atomically move it over `menus.yml`. If
any step fails, the live file has not been opened for writing and still holds the last good
save. The temp file is deleted if possible. Storage marks itself degraded, logs the cause, and
online holders of `MyMenu.admin.reload` are told. The failed changes stay in storage's image,
flagged unwritten. `flush()` at shutdown retries once. `loadAll()` refuses to run while the
image is unwritten, because re-reading would discard those changes; stage 7's reload must
respect that. A clean load clears the write-degraded flag.

A failed **backup** is treated as a failed write, and the live file is not replaced. A failed
**delete backup** leaves the menu in `menus.yml`, so it returns on the next load rather than
vanishing without its promised backup.
**Why:** SPEC §12 names runtime write failures as a cause of degradation but not what happens
to the edits. Keeping them and retrying at shutdown is the only choice that does not quietly
lose work. Refusing further edits keeps the pile of unsaved work from growing.

Verified 2026-09-18 by putting a directory at `menus.yml.tmp`. An empty directory: the write
failed, cleanup removed the directory, and the shutdown retry saved the edits, with a backup
identical to the pre-run file. A non-empty directory: both attempts failed, and `menus.yml` was
byte-identical afterwards. In both cases a delete and a create attempted after the failure were
refused, and the server stopped cleanly.

### 69. Config is read off the main thread; SPEC §5.1 has a duplicate key
**Old:** n/a.
**New:** `config.yml` is read by `PluginConfig.load` on the common fork-join pool during
enable, not by `JavaPlugin#getConfig()`. The bundled default contains only the keys that are
implemented so far (`storage.writeDebounceMillis`, `backups.keep`,
`backups.minIntervalSeconds`). `writeDebounceMillis` sits under the one `storage:` block. Bad
values fall back to defaults with a warning, and the file is never rewritten. Until the first
load lands, `MenuService` refuses mutations with a new reason, `NOT_LOADED`.
**Why:** `getConfig()` reads synchronously on whatever thread calls it, which would be a
second exception to hard rule 2. SPEC §5.1's example has two top-level `storage:` keys. That
is rejected under duplicate-key checking. With SnakeYAML's default settings the second block
silently replaces the first, which would lose `storage.type`. Keys are added to the default file in the
stage that reads them; shipping unread keys implies settings that do nothing. The load gate
exists because menus now arrive a tick or more after enable (the log shows them after
`Done`), and an edit in that window would be overwritten when the load landed.

### 70. Readable text is non-italic unless it says otherwise; capture matches
**Old:** n/a.
**New:** Readable text renders with italic explicitly disabled unless the text itself sets it.
`ItemSerializer.capture` builds its readable candidate the same way, so the round trip in #66
compares like with like.
**Why:** Minecraft italicises custom item names by default. That is almost never wanted, so
practically every plugin turns it off. `NOTES.md` flagged this as a stage 9 dependency of #66:
if stage 9 renders non-italic but capture does not, items carrying `italic: false` are stored as
bytes for a difference nobody asked for. Deciding it now means stage 9 inherits the constraint
rather than inventing a contradictory one. SPEC §10.2.

### 71. `/mymenu reload` has an explicit discard form
**Old:** n/a.
**New:** `/mymenu reload` accepts an explicit discard form. It drops unwritten changes and
re-reads from disk. The ordinary refusal message names that form.
**Why:** #68 blocks `loadAll()` while changes are unwritten, which is right for a transient
failure. If the fault is permanent (disk full, permissions, a stray file at the temp path), the
admin can neither write nor reload, and loses the changes at shutdown anyway. The lock needs a
deliberate way out. SPEC §12.

### 72. Displayed text is parsed first, then tokens are replaced inside the component
**Old:** 1.x applied wildcards to command strings only, then translated `&` codes over the
result, so colour codes in a substituted value took effect.
**New:** For displayed text (names, lore, titles, messages) the order is **parse, then
replace**. The admin-authored string is parsed into a `Component` first (`&`, `&#hex`, or
MiniMessage after `<!mm>`), with wildcard and placeholder tokens still literal text. The tokens
are then replaced inside that component with literal text values through Adventure's component
text replacement, so a substituted value becomes a text node and never passes through a parser.
Command values are separate: plain string substitution, then sanitisation, and no parsing at
all.
**Why:** SPEC §10 and ARCHITECTURE §4 step 3 and §10 originally gave the order as "wildcards →
PlaceholderAPI → colour translation". That puts parsing last, so it parses whatever was
substituted. It contradicted hard rule 11 and SPEC §10.1, and it was exploitable: a nickname
containing `&c&l`, or placeholder output containing a MiniMessage tag, would inject formatting
into text an admin wrote. The documents were corrected after stage 3 (commit `b62554e`). This
is a change of behaviour, not of wording. It also fixes the shape of the stage 4 text seam:
the renderer parses now and hands stage 9 a `Component` to replace tokens in, never a raw
string. The cost is that a token split across differently styled runs (`{PLA&cYER}`) is not
recognised, since replacement works node by node. Nobody writes that on purpose.

### 73. Capture and render share one build path; default-italic names now go to bytes
**Old:** n/a.
**New:** `render/ItemBuilder` owns template → `ItemStack` for both shapes, including reading
the bytes back. `ItemSerializer` keeps item → template (`capture`, `serialize`), and its
`deserialize` is gone. `capture` checks its round trip with `ItemBuilder.build`, the method the
renderer calls. So `storage` depends on `render`, not the other way round.
**Why:** #66's round trip means "stored readably only if it renders as this exact item". That
holds only if the round trip uses the renderer's conversion. A second copy would drift, and
#70's italic rule is exactly the kind of difference that would drift unnoticed.

The consequence reverses part of #66's verified list. A name that is italic only by
Minecraft's default, which is what an anvil rename produces, no longer round-trips: readable
text renders with italic off, so the rebuilt stack differs and the item goes to bytes. Explicit
`italic: false` names now come out readable. Explicit `italic: true` comes out readable as
`&o…`. Verified at runtime on 26.2.

Rejected: treating "italic unset" as `&o` and comparing visually rather than structurally.
The stored item would render with explicit italic, which looks identical but is not
`isSimilar` to the original, so an anvil-named item bound with `EXACT` would stop matching
the item the admin captured it from.

### 74. An unreadable stored item renders as a barrier, not an empty slot
**Old:** n/a (1.x stored material names).
**New:** When an opaque item's bytes fail to deserialise, or deserialise to nothing, its slot
shows a barrier named "Unreadable item". The rest of the menu renders normally. This applies to
a hidden fallback as well as to the icon. A warning naming menu, slot and cause is logged once
per stored item per renderer, not on every open. In edit mode the barrier's lore names the slot
and the cause, and says the data is kept. If a gated item's fallback is unreadable, the
permission marker says so, even though edit mode does not show fallbacks. The bytes are
untouched in storage, so the item comes back once whatever it depends on is back.
**Why:** #67 does not validate blobs at load, because a blob can fail only temporarily, for
example when a datapack enchantment has not loaded yet. An empty slot would hide the fault from
the admin. In view mode it would also look like "nothing here" over a slot that still has
actions. A barrier is visibly wrong without taking the menu down. **Open for stage 6:** whether
clicking a barrier runs the slot's actions. As built, the model still has them, and nothing in
rendering prevents it. **Open for stage 8:** the property editor cannot edit metadata it cannot
deserialise and must refuse that clearly.

### 75. `render` takes the revision and mode; the token seam is per viewer
**Old:** n/a.
**New:** `MenuRenderer.render(Menu, long revision, Player, ViewMode)`, not ARCHITECTURE §4's
`render(Menu, Player)`. The caller reads the menu and its revision from the registry in the
same main-thread tick. The renderer is constructed with a `Function<Player, TokenReplacer>`,
which is `viewer -> TokenReplacer.NONE` until stage 9.
**Why:** The holder needs the revision (§4.1, §4.3), and passing it in keeps the renderer free
of the registry. The alternatives were to reach into `MenuService` or to add a combined lookup
to the registry, and neither has another user yet. `TokenReplacer` takes and returns a
`Component`, so stage 9 cannot re-parse a substituted value without changing the interface
(#72).

**Correction (2026-09-19, stage 5):** the combined lookup now exists. `MenuRegistry.lookup` returns
`VersionedMenu` from one snapshot and `render` takes it (#76, ARCHITECTURE §4 revision 3).

---

### 76. `VersionedMenu` lives in `render/` for now, and the registry's bare revision lookup is gone
**Old:** n/a.
**New:** `MenuRegistry.lookup(name)` returns `Optional<VersionedMenu>` read from one snapshot, and
`MenuRegistry.revision(name)` no longer exists. `MenuRenderer.render` takes the record. The record
is in `render/`, so `service/` imports from `render/`.
**Why:** ARCHITECTURE §4 explains the pairing. What it does not say is where the type sits: its
natural home is `model/`, which stage 5 was not allowed to touch, and `render/` was the one open
package both sides already depend on. Removing `revision(name)` closes the only remaining way to
assemble a mismatched pair; it had no caller. Move the record to `model/` when that package is next
open; nothing else needs to change.

**Correction (2026-09-19, later the same session):** moved to `model/` as ARCHITECTURE §4 intends;
the importers changed and nothing else did.

### 77. The swap-in-flight marker is cleared by a scheduled task, not compared to a tick number
**Old:** n/a.
**New:** `markSwap` raises a flag and queues a delay-0 task that lowers it. Validity honours the
flag while it is up. Nothing in the plugin reads `Bukkit.getCurrentTick()`.
**Why:** The first draft stored the current tick and honoured the session while the tick matched.
The stage 5 probe chained 23 delay-0 tasks; they completed in 120 ms of wall time with the tick
counter reading 4 throughout, so a delay-0 task queued from inside a task runs in the same
scheduler pass, and "the same tick" covers everything queued behind the current task. The
reconcile task queued after an `OPEN_NEW` close saw the marker still up and never dropped the
replaced session, which is exactly the leak §5.1 forbids. A scheduled clear expires at the next
pass, which is the bound that was meant. Consequence for rule 9: a task queued from an event
handler runs at the next tick's heartbeat, because packets are processed after it, so "re-render
on the next tick" holds there; a task queued from inside another task runs after that task, in the
same tick.

### 78. `TELEPORT` never fires; every close reason but `OPEN_NEW` ends the session
**Old:** n/a.
**New:** `SessionManager.closed` keeps the session on `OPEN_NEW` and ends it on every other
reason, including constants added to the API later. A world change is not a close path at all.
**Why:** This resolves ARCHITECTURE §5.2's "unverified" note. The 26.2 javadoc marks `TELEPORT`
deprecated since 1.21.10 with the note that inventories are no longer closed on teleportation, so
a menu stays open across a teleport or world change and the session stays valid because the holder
is still open. Listing reasons that end a session would have left the next new constant leaking;
listing the one that does not cannot.

### 79. Sessions do not store the current menu
**Old:** n/a.
**New:** `ViewSession` holds the navigation history and the swap marker, nothing else; the current
menu is the open inventory's `MenuHolder` name. `EditSession` keeps the menu name only so validity
can require that the open `EDIT` view is for that menu.
**Why:** ARCHITECTURE §5 lists "current menu" among the view session's fields. A stored copy can
disagree with the holder, and when it does there is no way to say which one is right; the holder
is what the player is looking at. Reading a holder's name is identification (rule 4), not reading
menu state out of an inventory (rule 3).

### 80. Every click and drag while a menu is open is cancelled, whichever half of the screen it hits
**Old:** 1.x cancelled clicks in the menu inventory.
**New:** `InventoryClickListener` and `InventoryDragListener` cancel any click or drag whose view
has a `MenuHolder` on top, including clicks in the player's own inventory.
**Why:** SPEC §8.4 says clicks "inside a menu". Shift-clicks, number-key swaps, double-click
collection and drags all start in the bottom half and reach into the top, so cancelling by
clicked inventory alone leaks items. The cost is that a player cannot rearrange their inventory
while a menu is open.

### 81. Open menus are closed on disable
**Old:** n/a.
**New:** `onDisable` closes every player's `MenuHolder` inventory before flushing storage.
**Why:** Once the listeners are unregistered a menu is an ordinary chest whose items can be taken.
`/bukkit:reload` makes it worse: the fresh plugin instance loads a new `MenuHolder` class, so the
old holders are not `instanceof` it and the new listeners ignore them.

### 82. The storage codec reads whole action lists so the delay cap can be applied at parse time
**Old:** n/a.
**New:** `ActionCodec` gained `readList(entries, where)`, defaulting to one `read` per entry, and
`MenuYamlFormat.readActions` calls it once per click key instead of calling `read` in a loop.
`ActionParser` overrides it to sum the `DELAY` ticks and, when they exceed
`actions.maxTotalDelaySeconds`, shorten the delay that crosses the cap to what remains and zero
every later one, logging one warning naming the menu, slot and key. Zeroed delays stay in the
list as `DELAY 0` rather than being removed; the next save writes the clamped list.
**Why:** SPEC §9.2 caps the total per list, clamps stored lists with a warning, and forbids a
run-time check. The codec was per entry and the format assembled the list, so no entry could see
the total; clamping in the executor was the run-time check the spec forbids. `storage/` was off
the stage 6 allowlist and the widening was approved for exactly this. Keeping the zeroed entries
means the action count and order an admin wrote are preserved in the file, so what happened is
visible in the file rather than only in a log line. A `DELAY 0` still yields to the next tick.

### 83. `navigation.maxDepth` is enforced by the executor; `NavigationStack.MAX_DEPTH` is dead
**Old:** n/a.
**New:** Before a `MENU` action navigates, `ActionExecutor` reads the current session's history
size and, if it is at or above `maxDepth` (default 10), logs a warning and does nothing, as SPEC
§9.3 says. The stack therefore never holds more than `maxDepth` entries. Stage 5's
`NavigationStack.MAX_DEPTH = 32`, which silently drops the oldest entry, is unreachable while
`maxDepth` is at most 32 and should be deleted when `session/` is next open.
**Why:** Stage 5 wrote the stack before reading §9.3, and `session/` was off the stage 6
allowlist, so the spec's behaviour had to be added in front of the stack rather than inside it.
Two caps with different behaviour is one too many; the spec's is the one that stays. Until the
constant is removed, a `maxDepth` above 32 would silently fall back to the drop-oldest rule,
which is why the config key's upper bound should be checked when stage 7 reads it.

**Correction (2026-09-19, review of stage 6):** `MAX_DEPTH` is retained by design, not deleted.
`ActionExecutor.setMaxDepth` clamps its argument to 1..`NavigationStack.MAX_DEPTH` (now public)
with a warning, so the stack's ceiling is an enforced invariant and an admin setting a larger
`navigation.maxDepth` gets the ceiling rather than silently dropped history.

### 84. Pending action sequences are cancelled by a quit handler on `ActionExecutor` itself
**Old:** 1.x had no delays, so nothing to cancel.
**New:** `ActionExecutor implements Listener` and cancels the player's pending task on
`PlayerQuitEvent` at `MONITOR`. `PlayerQuitListener` is untouched and still handles sessions
only. The map of pending tasks is keyed by UUID and lives on the executor, not on `ViewSession`.
**Why:** ARCHITECTURE §9 lists "cancel pending sequences" under `PlayerQuitListener`. That file
was off the stage 6 allowlist, and keeping the map and the only code that removes entries from
it in one class is the better shape anyway: the thing that owns the state owns its cleanup, and
nothing else needs a reference to the executor. The map is deliberately not session state
because SPEC §9.2 requires a sequence to continue through death, and death ends the session.
ARCHITECTURE §9 should be read with this entry in mind; it was not edited.

### 85. The `!` shorthand produces an elevated command with no permission nodes
**Old:** 1.x opped the player, so there was no node list to carry.
**New:** `ActionParser.parseShorthand("!cmd")` yields `PLAYER_ELEVATED` with an empty
`permissions` list. `Action.ElevatedCommand` accepts the empty list. Nodes are attached
through the property editor in stage 8. An elevated command with no nodes runs exactly as a
`PLAYER` command would.
**Why:** SPEC §3.3 and ARCHITECTURE §10 define the four prefixes but no syntax for a node list
on the `!` line, and inventing one (a separator, a bracket list) would put grammar into chat
input that the editor already has a GUI for. An empty grant is harmless: the player gains
nothing they did not have.

### 86. A step that throws ends its list; sound and cooldown are taken before the list runs
**Old:** 1.x ran commands in a loop with no error handling; one exception aborted the click
with a stack trace and no cleanup.
**New:** `ActionExecutor` catches a `RuntimeException` from any step, logs it with the action
and player, and stops the list; the pending entry is never left set. The click sound plays and
the cooldown starts when the click is accepted, before the first action, and both apply to an
empty list too. A click during a pending sequence is dropped before the cooldown is consulted,
so it does not consume the cooldown.
**Why:** Later steps are written assuming the earlier ones ran (`take money` then `give item`),
so continuing past a failure is the wrong default. Taking the cooldown first is the only order
that cannot be gamed: if it were taken on success, a command that fails could be retried without
limit. The sound plays on acceptance because it is feedback for the click, not for the result.
`Player#performCommand` declares `CommandException`, which is unchecked, so the catch is the
only place a plugin's failing command surfaces.

**Correction (2026-09-19, review of stage 6):** two of the three choices were reversed on review;
SPEC §8.4 and §9.2 were updated to say so. An empty action list plays no sound and starts no
cooldown: it exists to say "this click does nothing" and to stop the `OTHER` fallback, and
feedback plus a penalty contradicts that, as well as differing from a slot with no keys at all.
A sequence ends as soon as nothing executable remains, so a trailing `DELAY` does not hold the
player pending with nothing to run. The throw-stops-the-list rule stands.

### 87. Reload retries a failed write before it loads
**Old:** n/a.
**New:** `/mymenu reload` flushes the pending batch, then asks storage to write again anything an
earlier write failed to persist, and only then loads. Both forms of reload do this; the discard
form discards only what that retry could not write.
**Why:** #68 makes `loadAll()` refuse while the image holds unwritten changes, and nothing cleared
that flag except a successful write — but no path attempted one after the failure, short of
shutdown. An admin who fixed the fault (freed the disk, removed the stray file) therefore still
could not reload, and the only way forward was #71's discard form, which would have thrown away
changes that had just become writable. With the retry, a fixed fault costs nothing: the work is
written and the reload proceeds. Observed in testing: the stray-file simulation cleared itself
when the failed write's own temp-file cleanup removed the obstruction, and the retry then saved
the change that would otherwise have been discarded.

### 88. Mutating commands are refused at the command edge, not only by `MenuService`
**Old:** 1.x had no degraded state and no reload gate.
**New:** `CommandSpec.mutating()` marks the commands SPEC §12 lists as refusable, and the tree
refuses them before the subcommand runs when storage is degraded, when menus have not loaded, or
while a reload is in flight.
**Why:** Two of them never reach `MenuService`: `joinmenu` writes `config.yml`, and `edit` only
opens a view. Hard rule 10 still stands — `MenuService` keeps its own check, and it is what
protects the model — but the edge check is what makes the whole SPEC §12 list behave alike and
explain itself in the same words. The reload condition is new: between the flush and the
`replaceAll`, an accepted edit would be made against menus the load is about to replace. Nothing
but commands can mutate today, so the edge covers every path. **Stage 8 must not bypass it:** an
editor click is a mutation, and it needs the same three checks.

### 89. Commands never open or close an inventory in the tick they run
**Old:** 1.x opened inventories straight from its command handlers.
**New:** `open`, `edit`, the delete cascade and reload all schedule their inventory work for the
next tick.
**Why:** A menu item can run `/mymenu open shop` as a player command, and action lists execute
inside `InventoryClickEvent`, where rule 9 forbids opening or closing an inventory. A command
cannot tell whether it was typed or clicked, so it always defers. The cost is one tick of delay
and a result message that arrives after the command returns; in the gap, a click on a menu that
is about to be deleted already fails the stale-view check.

### 90. `update` is registered and says it cannot check yet
**Old:** 1.x had an update checker that downloaded and replaced the jar, and documented a
`/mmupdate` command that did not exist.
**New:** `/mymenu update` exists from stage 7 and replies that this build cannot check for
updates. The checker itself is stage 10.
**Why:** SPEC §3.7 gives it a behaviour the plugin cannot have until `integration/` exists, and
the alternative — leaving it out of the tree — would have made the generated help and the
permission set change shape again at stage 10. A command that states its own limit is not the
same failure as 1.x's help documenting a command that never existed. **It must be finished at
stage 10**; if the checker slips, this command goes rather than lingers.

### 91. `joinMenu` and `storage.type` are read before the features that consume them exist
**Old:** 1.x had `OpenOnJoin` per menu and no storage choice.
**New:** `PluginConfig` reads `joinMenu` and `storage.type` at stage 7. `joinMenu` appears in the
bundled default file; `storage.type` does not.
**Why:** SPEC §5.1 says a key is added in the stage that reads it, because shipping unread keys
implies settings that do nothing. Both are read here, just not by the feature they name:
`joinmenu` writes `joinMenu` and `delete` warns when it names the menu being deleted, while
`storage.type` is compared across a reload so the refusal to switch backends at runtime can be
reported. Nothing opens a menu on join yet (SPEC §13 has no stage), so `joinMenu` is a setting an
admin can set and not yet see act; that is the lesser evil against `joinmenu` writing a key the
file never mentions. `storage.type` stays out of the default file because offering `MYSQL` there
would advertise a backend stage 11 has not built; a file that names it is honoured with a warning
and YAML.

### 92. The delete cascade does not cancel pending `MENU` actions
**Old:** n/a.
**New:** Deleting a menu closes its views, ends its edit sessions and prompts, purges it from
navigation stacks, and warns about `joinMenu` — but does not cancel action sequences that are
waiting on a delay and will open it. When such an action fires, the menu is gone, and the player
is told it does not exist.
**Why:** SPEC §3.5 asks for the cancellation. `ActionExecutor` exposes `cancel(UUID)` and nothing
that reports what a pending sequence still holds, so doing it properly needs a method in
`action/`, which stage 7 was scoped out of. The fallback is not dangerous: the executor already
handles a missing menu. Left undone deliberately, and the stage 7 report proposes the method.

**Correction (2026-09-19, review of stage 7):** dropped, not deferred. SPEC §3.5 no longer asks
for it, and `cancelTargeting` is not to be written. Every other item in the cascade prevents a
real problem — a stale view of a menu that is gone, an orphaned edit prompt, a `BACK` into a dead
menu — while this one prevented only a message: the executor already tells the player the menu
does not exist, and the navigation history is untouched either way. Paying for it would mean
`ActionExecutor` retaining every pending sequence's remaining steps purely so that something else
could ask what they contain, which is state kept for a query rather than for the work.
### 93. `none` is reserved by the command layer only, not by the model
**Old:** 1.x accepted any name and had no join-menu command.
**New:** `CommandTree` refuses `none` when validating a new menu name, saying why:
`/mymenu joinmenu none` uses the word to mean "no menu". `Menu.isValidName` deliberately still
accepts it, and a hand-written `menus.yml` may legitimately define a menu called `none`. Such a
menu loads, renders, saves, opens and deletes like any other; the single thing it cannot do is
be selected by `joinmenu`.
**Why:** The reservation is a property of one argument of one command, where `none` is a
sentinel value, not a property of menus. A menu named `none` is perfectly well-formed. Model
invariants should be the things that make a menu impossible to render or store — a slot outside
the layout, a material that is not an item — and a name is neither.

Putting the rule in `Menu` instead would also be actively harmful. A model constructor that
rejected the name would make an existing menu called `none` fail to construct, so the loader
would skip it and set the degraded state, and **editing would be disabled server-wide over a
name collision** whose only real effect is that one setting cannot point at that menu. The
punishment would be wildly out of proportion to the problem.

The command layer is also the only place a person can be told why a name was refused; a
constructor can only throw. Every name a player can invent arrives through that one validator,
so the reservation holds everywhere it matters. SPEC §3.2 stated the rule as a property of menu
names, which was wrong, and is being corrected.

---

## Template for new entries

```
### N. Short title
**Old:** What 1.0.4.3 did, or n/a.
**New:** What this version does.
**Why:** The reasoning, including what was rejected and the cost of the choice.
```
