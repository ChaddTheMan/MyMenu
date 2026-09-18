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
rendering, per-item permissions, usage limits) is architectural, not additive. Retrofitting
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
Accepted costs: Paper-only, stricter classloader isolation, and no `/reload`.

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
**New:** Identical, plus `MyMenu.bypass.cooldown`.
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
**Old:** `MyMenuMenu` held one `Inventory`, shared by all viewers; saving read items back
out of it.
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
**Old:** Bound items stored a material name only, and matching compared `getType()`, so
any compass opened a compass-bound menu.
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

## Template for new entries

```
### N. Short title
**Old:** What 1.0.4.3 did, or n/a.
**New:** What this version does.
**Why:** The reasoning, including what was rejected and the cost of the choice.
```
