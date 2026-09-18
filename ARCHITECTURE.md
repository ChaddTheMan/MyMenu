# MyMenu 2.0.0 — Architecture

**Revision:** 2 — incorporates `AUDIT.md` findings

How the plugin is built, and why. `SPEC.md` defines behaviour; this defines structure.
Where they disagree, `SPEC.md` wins and this file is wrong.

---

## 1. The central idea: the model is not the view

**A `Menu` is data. An `Inventory` is a picture of that data. They are never the same
object.**

MyMenu 1.0.4.3 held one `Inventory` per menu, shared by every viewer. (Note: 1.x did
*not* persist by reading items back out of that inventory — `saveInventoryItems` iterated
a `HashMap<Integer, MyMenuItem>` model. The shared inventory was a display problem, not a
persistence problem. An earlier draft of this document said otherwise and was wrong.)

Sharing one inventory makes four things impossible:

- **Per-viewer content.** View permissions and placeholders resolve differently per
  player. One shared inventory cannot show two different things.
- **A database backend.** Serialisation must work on data, not on a live UI object.
- **Reliable identification.** 1.x identified menus by comparing inventory *title
  strings*. `Inventory.getName()` no longer exists, and titles were never unique.
- **Testing.** Nothing could be verified without a running server.

```
MenuService ──gates mutations──> MenuRegistry ──owns──> Menu (model)
                                                          ├── MenuItem[] by slot
                                                          │    ├── ItemTemplate (incl. glow)
                                                          │    ├── Map<ClickType, List<Action>>
                                                          │    └── view permission,
                                                          │        cooldown, sound
                                                          ├── BoundItem + MatchMode
                                                          └── giveItemOnJoin

MenuRenderer: (Menu, Player, Mode) ──> Inventory  [transient, per viewer, never stored]

Revisions come from a single plugin-wide counter held by MenuRegistry, not from Menu.
```

---

## 2. Package layout

Root package: `me.chaddtheman.mymenu`

```
me.chaddtheman.mymenu
├── MyMenu.java                  plugin entry point
├── model/                       Menu, MenuItem, ItemTemplate, MenuType, MatchMode
├── service/                     MenuService, MenuRegistry, CooldownStore
├── action/                      Action, ActionType, ActionParser, ActionExecutor
├── render/                      MenuRenderer, MenuHolder, ItemBuilder
├── session/                     ViewSession, EditSession, SessionManager, NavigationStack
├── storage/                     MenuStorage, YamlMenuStorage, MySqlMenuStorage,
│                                ItemSerializer, BackupWriter
├── command/                     CommandTree (Brigadier), subcommand classes
├── listener/                    inventory and player listeners
├── text/                        TextService (colour, wildcards, PlaceholderAPI bridge)
├── config/                      PluginConfig, MessageService
└── integration/                 PlaceholderApiHook, BStatsService, UpdateChecker
```

**No static mutable state anywhere.** 1.x kept `static ArrayList` registries and, worse,
the entire in-progress item conversation in static fields, so two admins editing at once
corrupted each other's work. Everything here is instance state owned by the plugin object.

---

## 3. Model layer

`Menu` holds: name, title, author, author UUID, type, rows, bound item,
`giveItemOnJoin`, and a `Map<Integer, MenuItem>`.

`MenuRegistry` owns the menus by name and holds **one plugin-wide revision counter**. Every
mutation increments it and records the new value **beside** the menu, not inside it. A
per-menu counter would be useless for detecting deletion and would collide after a
delete-then-recreate.

The revision is deliberately not part of `Menu`: it is never persisted, and two identical
menus loaded at different times must still be distinguishable.

`MenuService` sits in front of the registry and is the only path by which menus are
mutated. It is where the degraded-state gate lives (§7.1), and it is what marks menus dirty
for the debounced writer (§7.2). Refusals come back as ordinary result values, not
exceptions, so every refused command can explain itself (SPEC §12).

`MenuService` depends on a narrow `MenuPersistence` interface (`isDegraded`, `markDirty`,
`markDeleted`) rather than on `MenuStorage`, which keeps debounce and file handling out of
the mutation gate.

### 3.1 Models are immutable

**Revised at stage 2.** An earlier draft made models mutable and main-thread-only, relying
on convention to keep mutation inside `MenuService`. Java visibility cannot express that —
package-private does not span `service/` into `model/` — so the rule was unenforceable.
Models are therefore immutable, and a change means building a new instance and swapping it
into the registry.

Three consequences:

- The "only `MenuService` mutates" rule is structural rather than conventional.
- Registry reads are thread-safe, which settles §8's open question about whether Paper
  computes command suggestions off the main thread.
- The whole-registry snapshot §7 needs before its async hop becomes a copy of references.
  It cannot tear, and it is free.

**Immutability must be deep, or it is a lie that reads as a guarantee.** Bukkit's
`ItemStack` is mutable: it is `clone()`d on construction and on every read. Every
collection — lore, action lists, the slot map — is copied into an unmodifiable view rather
than exposed directly.

**The registry publishes copy-on-write snapshots through a `volatile` field.** Each write
builds a new immutable map and publishes it. This beats a `ConcurrentHashMap` here because
the registry is read constantly and written rarely, and because a reader holding one
snapshot sees a **consistent** view — a reader iterating a concurrent map can observe a
half-applied change.

**That pattern is safe only with a single writer.** Read-modify-write of the volatile field
from two threads loses updates with no visible symptom. Writes happen on the main thread,
through `MenuService`, and nowhere else. If that ever stops being true, this pattern must
change with it.

The registry's write methods and constructor are package-private to `service/`, so the
enforcement boundary is that package. Keep it small.

Thread-safe *model* reads do not relax anything else: calling the Bukkit API off the main
thread remains forbidden.

`MenuItem` holds: an `ItemTemplate`, a `Map<ClickKey, List<Action>>`, an optional view
permission, an optional hidden-fallback template, a click sound, and a cooldown.

`ClickKey` is MyMenu's own enum, **not** Bukkit's `ClickType`, which has no `OTHER`
constant and so cannot express SPEC §8.4's fallback key. Mapping from `ClickType`:
`CONTROL_DROP` → `DROP`; `WINDOW_BORDER_LEFT`, `WINDOW_BORDER_RIGHT`, `SWAP_OFFHAND`,
`CREATIVE` and `UNKNOWN` → `OTHER`.

The click sound is stored as a **namespaced key** (`minecraft:ui.button.click`), because
`Sound` is a registry-backed interface on 26.2 rather than an enum. Bare names are accepted
case-insensitively on input.

**Glow lives on `ItemTemplate`, not on `MenuItem`.** It is an item property, and putting
it in one place is what lets it apply identically to descriptive and opaque templates.

`ItemTemplate` knows how to become an `ItemStack`, in two shapes:

- **Descriptive:** material, display name, amount, lore. Text fields hold raw strings with
  colour codes and placeholders **unresolved**.
- **Opaque:** a serialised `ItemStack` byte array.

Glow lives on `ItemTemplate` and is applied at render time via the enchantment glint
override, so it works identically for both shapes and never forces the opaque form.

Resolution happens at render time, never at load time, because the same template renders
differently per viewer.

Models are immutable; see §3.1.

---

## 4. Rendering

`MenuRenderer.render(Menu, Player)` returns a fresh `Inventory`:

1. Create it with a `MenuHolder` carrying the **menu name**, the current global revision,
   and the **view mode** (`VIEW` or `EDIT`).
2. In `VIEW` mode, if the viewer lacks the item's view permission, render the
   hidden-fallback template if present, otherwise leave the slot empty. **Never compact.**
   In `EDIT` mode, view permissions are **not applied** — an admin must be able to see an
   item to edit it — and permission-gated items are marked in their lore.
3. Build the `ItemStack`, resolving wildcards and PlaceholderAPI per viewer, translating
   colour last.
4. Apply the glint override if `glow` is set.
5. Stamp the **rendered-icon** persistent-data key on every item.

Rendering is pure with respect to the model: it reads and never writes.

### 4.1 Identification via `MenuHolder`

```java
if (event.getInventory().getHolder() instanceof MenuHolder holder) { ... }
```

Exact, survives duplicate titles, cannot be spoofed, and does not depend on removed API.

### 4.2 Two tag keys, not one

Bound items and rendered icons carry **different** persistent-data keys. A creative-mode
player can clone an icon out of an open menu; sharing one key would turn every clone into
a menu opener.

### 4.3 Stale-view checking

On click, the holder's menu name is looked up in the registry. Absent means deleted;
present with a different revision means changed. Either refuses the click and re-renders
**on the next tick** — re-rendering means opening an inventory, which §6 forbids doing
inside a click handler.

An earlier draft had the holder keep the `Menu` object and a per-menu counter. That cannot
detect delete or reload, because both leave the old object untouched with its revision
intact.

`EDIT` mode skips the check entirely. Every edit bumps the revision, so an editing admin
would otherwise invalidate their own next click.

---

## 5. Sessions

`SessionManager` holds `ViewSession` (current menu, navigation stack) and `EditSession`
(menu being edited, slot and property in flight, pending prompt with its timeout task).

### 5.1 Derive state, do not trust tracked state

1.x's "You are already using/editing a menu!" lockout was not really caused by its
conditional close handler. It was caused by a leaked map entry becoming **authoritative**:
the plugin asked "is there an entry for this player?" rather than "is this player
actually in a menu right now?"

So: **a session is valid only if the player currently has an inventory open whose holder
is a `MenuHolder`, or is in a bounded transient state.** Validate on read. A leaked entry
cannot lock anyone out, because the check consults reality.

This applies to `EditSession` as much as `ViewSession`. A leaked edit session would produce
the same "already editing" lockout 1.x had.

**Transient states must be bounded, or they are just tracked flags wearing a disguise.**
Two exist: a *menu swap in flight*, valid for one tick only, and a *pending chat prompt*,
valid until its timeout task fires. Both expire on their own.

The navigation stack is also validated rather than trusted. A missed close would otherwise
leave entries that send `BACK` to a menu from a previous session, so §5.2's cleanup does
carry correctness weight for the stack even though it does not for the lockout.

### 5.2 Close handling is nuanced, not unconditional

An earlier draft required unconditional cleanup on `InventoryCloseEvent`. That is
self-contradictory: a `MENU` action, opening the property editor, and starting a chat
prompt all close the current inventory as a side effect. Applied literally, the rule would
clear the navigation stack on every `MENU` action and destroy every chat prompt as it
started.

Cleanup therefore branches on `InventoryCloseEvent.getReason()`, with `OPEN_NEW`
distinguishing "replaced by another inventory" from a real close.

**Closing to start a chat prompt is not `OPEN_NEW`**, so the reason alone is not enough.
The prompt state is recorded *before* the plugin closes the inventory, and the close
handler checks for it.

**Unverified:** `InventoryCloseEvent.getReason()` and `Reason.OPEN_NEW` were not confirmed
against current Paper during the audit. Verify before relying on them; §5.1 keeps the
lockout safe either way.

**Unconditional rules that do still hold:** no session survives `PlayerQuitEvent`; no code
path leaves a session behind on an exception (cleanup in `finally`); every chat prompt
carries a timeout task, cancelled when the prompt resolves.

### 5.3 Create sessions after the open succeeds

`Player#openInventory` returns null if another plugin cancelled the open. 1.x created its
session *before* opening, so a cancelled open leaked a session with no close event to
clean it up.

---

## 6. Actions

`Action` is a small immutable object. `ActionParser` converts both stored YAML and the
in-game prefix shorthand (`$` console, `\` message, `!` elevated, bare for player) into
the same typed object. Storage is always the explicit `type:` field; prefixes exist only
at the input boundary.

`ActionExecutor` runs a list for a player. `DELAY` means execution cannot be a simple loop:

- Walk the list until a `DELAY`, then schedule the remainder and return.
- One pending sequence **per player**. A click while one is pending is ignored.
- Logout cancels pending sequences. Death and world change do not.
- Total delay is validated at parse time and clamped with a warning, not at run time.

**`MENU`, `BACK`, and `CLOSE` are scheduled for the next tick.** Opening or closing an
inventory from inside `InventoryClickEvent` is unsupported and misbehaves.

`PLAYER` dispatches via `Player#performCommand`. `PLAYER_ELEVATED` uses a temporary
`PermissionAttachment` removed in a `finally` block. Opping and de-opping is forbidden: an
exception between the two leaves a player opped.

---

## 7. Storage

```java
public interface MenuStorage {
    CompletableFuture<Collection<Menu>> loadAll();
    CompletableFuture<Void> save(Menu menu);
    CompletableFuture<Void> delete(String name);
    CompletableFuture<Void> saveAll(Collection<Menu> menus);
    boolean isDegraded();
    void flush();          // write everything pending, block until the queue drains
    void close();
}

The executor is **single-threaded**, which gives write ordering for free: two rapid
mutations to the same menu cannot land out of order.

`save(Menu)` under YAML rewrites the whole file, so the writer needs every menu. Since
models are immutable (§3.1), that snapshot is a copy of references and cannot tear. The
earlier requirement to take it on the main thread before the async hop no longer applies.
```

**`YamlMenuStorage`** writes `menus.yml` using the descriptive form where possible and the
opaque form where necessary. Write order is: **copy the live file into `backups/`, write a
temp file, atomically move the temp file over the live file.** Rotating the live file out
first would leave a moment with no live file at all. `backups.keep` bounds retention.

**`MySqlMenuStorage`** uses HikariCP and always stores items in the opaque form. Schema is
created on first connect and tracked in a `schema_version` table. Single-writer only.

### 7.1 Degraded state

Set by a load-time parse failure or a runtime write failure. Storage reports the condition;
**`MenuService` enforces it, refusing mutations before they reach the model.**

An earlier draft enforced it in storage. That is too late: the model has already changed,
so the edit lives in memory until the next reload silently throws it away — the exact
data-loss shape the rule exists to prevent.

Read paths are unaffected. Players keep using existing menus; `list`, `info`, and `open`
keep working.

### 7.2 Debounced writes

A mutation marks its menu dirty and schedules a write after
`storage.writeDebounceMillis`. A run of rapid edits coalesces into one write.

This is why `flush()` exists, and it changes the shutdown story in §7.4: there *is* pending
work, so `onDisable` flushes before draining. `/mymenu reload` also flushes and waits
before re-reading, or it would read a half-written file.

### 7.3 Backups

Backup policy is separate from save policy: at most one save backup per
`backups.minIntervalSeconds`, retention bounded by `backups.keep`. Debounced writes alone
would still push every useful backup out of retention during a long editing session.

Delete backups are standalone files, exempt from `keep`, never pruned. Under MySQL there is
no live file to copy, so save backups do not apply and only delete backups are written.

### 7.4 Threading

**All storage I/O happens off the main thread.** I/O returns a `CompletableFuture`;
anything touching the Bukkit API hops back to the main thread first.

Storage owns **its own `ExecutorService`**, not Bukkit's scheduler, because the scheduler
stops accepting tasks during disable.

**The single documented exception:** `onDisable` calls `flush()`, then `shutdown()` and
`awaitTermination` with a bounded timeout, logging loudly if it expires. This is mandatory,
not best-effort: `/bukkit:reload` can start a second plugin instance moments later (§8), and
a surviving executor from the old instance would write to the same files as the new one. It writes pending
debounced work and drains the queue. It does not walk the registry performing a full save.

Debouncing is what makes the flush necessary; an earlier draft claimed there was nothing to
flush, which was true only while every mutation wrote immediately.

### 7.5 Item serialisation

`ItemSerializer` uses Paper's byte-array serialisation, which runs through the game's own
data converters and survives version upgrades. Hand-rolled NBT and
`ConfigurationSerializable` round-trips both lose data on modern items.

---

## 8. Commands

Brigadier lives **only at the edge**. The tree handles parsing, type conversion,
permission gating via `.requires()`, and suggestions. Subcommand classes receive resolved,
typed parameters:

```java
void execute(CommandSender sender, Menu menu, int rows, String title);
```

not `boolean onCommand(CommandSender sender, String[] args)`.

The resolved type varies by subcommand: `create` receives a validated *name* rather than a
`Menu`, because its menu does not exist yet.

This confines the generics-heavy builder code to one file and leaves subcommand logic
short, readable, and testable. It also removes the largest duplication in the original,
where all 13 command classes repeated the same sender, permission, and argument-count
checks.

Registration happens in `onEnable` via
`getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ...)`, with `mm`
passed as an alias to `Commands.register(...)`.

**Confirmed at stage 1 on Paper 26.2:** a `COMMANDS` handler registered from `onEnable`
does **not** disable Bukkit's plugin reload. An earlier draft claimed it did.

What actually exists on 26.2:

- A bare `/reload` is Mojang's **datapack** reload. Bukkit's plugin reload is reachable only
  as `/bukkit:reload`.
- The datapack reload re-fires the `COMMANDS` handler (cause `RELOAD`), so **registration
  must be safe to run twice.**
- `/bukkit:reload` constructs a **new plugin instance in a new classloader**. Only the new
  instance registers; Paper logs its own warning about the practice.

The last point matters beyond commands. Two copies of the plugin can briefly exist in one
JVM: the old instance's `onDisable` runs, then a fresh instance loads. So **`onDisable`
must stop everything the plugin started** — the storage executor, scheduled tasks, pending
debounced writes — or a leaked thread from the old copy writes to the same files as the new
one. This is what makes §7.4's shutdown discipline load-bearing rather than tidy, and it is
a second and independent reason for the no-static-state rule in §2: static fields are
exactly where classloader leaks originate.

`/mymenu reload` is unaffected by any of this.

**Resolved at stage 2.** This was an open risk while models were mutable: if Paper computes
suggestions off the main thread, the `<menu>` provider would read a main-thread-only
registry. Immutable models plus a concurrent map (§3.1) make that safe regardless of which
thread Paper uses.

Help text is **generated from the subcommand registry**. 1.x had 14 KB of hand-maintained
help that had already drifted: it documented `/mmupdate`, which never existed, and
described `null`/`unset` for unbinding when the code accepted `none`/`null`.

---

## 9. Listeners

| Listener | Responsibility |
|---|---|
| `InventoryClickListener` | Cancel all clicks; revision check; dispatch by click type |
| `InventoryDragListener` | Cancel drags across menu slots |
| `InventoryCloseListener` | Reason-aware session handling; return cursor items |
| `AsyncChatListener` | Editor text input: cancel the message, hop to the main thread |
| `PlayerInteractListener` | Bound-item detection and menu opening |
| `PlayerJoinListener` | `joinMenu`, `giveItemOnJoin`, update notification |
| `PlayerQuitListener` | Session cleanup, cancel pending sequences |

**Listeners hold no per-event state in fields.** 1.x stored the event player, inventory,
and slot as instance fields on singleton listeners, then used `this.player` inside a
100-tick delayed task, so two players joining within five seconds both received one
message.

**Players are tracked by UUID, never by name.** 1.x used names.

Bound-item interaction fires once per hand; only the main hand is handled, and the event
is cancelled. Both left- and right-click open a menu, matching 1.x.

Click handling must account for types that did not exist in 1.8: off-hand swap,
number-key hotbar swap, double-click collection, and drag events. Cancel first, interpret
second.

---

## 10. Text

`TextService` owns the order: native wildcards → PlaceholderAPI → colour translation,
across display names, lore, message text, **and command values**.

**Substitution happens after parsing, never before.** Substituted values are inserted as
data and never re-parsed as MiniMessage. Values entering command strings are sanitised
(newlines, carriage returns, semicolons, leading slashes). This is command injection
defence, not polish: placeholder output and nicknames are player-controlled.

Colour translation is not applied to command values.

PlaceholderAPI is reached only through `PlaceholderApiHook`, which no-ops when absent.
**Paper plugins have isolated classloaders, so this additionally requires declaring
PlaceholderAPI in `paper-plugin.yml` under `dependencies.server` with `load: BEFORE`,
`required: false`, `join-classpath: true`.** Without that entry the hook cannot see the
class at all.

---

## 11. Build

**Gradle**, with `run-paper` for `./gradlew runServer`, a `paper-plugin.yml` generator,
and Shadow used **only** to shade and relocate bStats. `CHANGELOG.md` is bundled into the
jar as a resource, since `/mymenu changelog` reads it.

**`paper-plugin.yml`** declares:

- a `loader` implementing `PluginLoader`, resolving HikariCP and the MySQL driver through
  `MavenLibraryResolver`. **Use `MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR`** —
  using Maven Central directly as a CDN breaks Central's terms of service.
- `dependencies.server` entry for PlaceholderAPI as described in §10.
- no `bootstrapper`.

**Known cost:** the loader runs before config is read, so every server downloads the MySQL
driver even when using YAML. Accepted; the alternative is a separate addon jar, which is
worse for a plugin this size.

**Verified at stage 1 (2026-09-18).** `plugin-yml` is the maintained fork
`de.eldoria.plugin-yml.paper` (the original has had no release since 2023), `run-paper`
3.1.0, Shadow `com.gradleup.shadow` 9.6.1 (not added until stage 10, when bStats arrives),
`paper-api` `26.2.build.124-stable` — note the `-stable` suffix, which older tutorials omit
and which therefore fails to resolve. Library versions live once, as `paperLibrary`
declarations in the build; `generateLibrariesJson` writes them to `paper-libraries.json` and
the loader reads only the dependency list from it, always fetching through the Central
mirror. The JSON's repository list is ignored, which is why a "no CentralProxy configured"
warning is harmless.

### 11.1 `.gitignore`

GitHub's Java template ignores `*.jar`, which would also ignore
`gradle/wrapper/gradle-wrapper.jar` — that **must be committed** or the wrapper breaks for
anyone cloning. Add an explicit negation, plus `.gradle/`, `build/`, and `run/`.

---

## 12. Bugs from 1.0.4.3 that must not be reproduced

Verified against the decompiled source during the pre-implementation audit.

| Bug | Original |
|---|---|
| Static conversation state | Slot, menu, player, lore, and commands in `static` fields — two simultaneous editors corrupted each other |
| Session leak | Close listener removed the player only if a title lookup succeeded, locking players out until relog |
| Session created before open | `openInventory` can be cancelled; the session then leaked with no close event |
| Row clamping | `setRows()` clamped `this.rows` but computed `size` from the unclamped parameter. `0` produced size 0 and `-1` produced size −9 |
| Broken constructors | Four `MyMenuItem` constructors called `this.item.getItemMeta()` before assigning `this.item` |
| Wrong colour character | `MyMenuInfo` called `translateAlternateColorCodes('$', ...)` |
| Delayed-task state | `PlayerJoinListener` used an instance field inside a 100-tick task |
| Load-time writes | Config mutated and saved during loading, on the main thread, once per menu |
| First-run config clobber | `reload()` ran before `saveDefaultConfig()`, overwriting the freshly copied default |
| Two objects, one file | `configFile` and `menuConfigConfigFile` both pointed at `config.yml` with separate in-memory copies |
| Join settings never persisted | `saveMenuConfigList` rebuilt from disk before writing, discarding in-memory changes |
| Delete's file write did nothing | `MyMenuDelete` held a stale `FileConfiguration`; only the preceding `updateToConfig()` had any effect |
| Update check blocked the main thread | `thread.join()` from a sync task, 5 s connect timeout, no read timeout |
| String version comparison | `!equalsIgnoreCase`, so an older remote version counted as an update |
| One bad entry aborted all loading | Unknown material, missing `slots`, or missing `menus:` threw and stopped the load |
| Middle-click move lost data | Closing the editor with an item on the cursor dropped it and deleted the menu entry, which the close-time save persisted |
| Players tracked by name | Not UUID |
| First match wins, silently | Several menus bound to the same material |
| Wrong material map | Seven flower aliases mapped to `WOOL`; `mossy cobblestone wall` mapped to `WOOD_STEP` |
| Colour codes in commands | `&` translated inside command strings, mangling commands containing `&` |
| Duplicate manifest keys | `plugin.yml` contained the entire `commands:` block twice |
| Global command squat | `/update` registered as a top-level command |
| No permission defaults | Every node op-only, including player help |

Two items an earlier draft listed are real but unreachable, and are recorded here only so
nobody re-adds them as concerns: `getEnchants().clear()` on an immutable map (items were
always freshly built without enchantments) and `removeMenu`'s wrong index check
(`setConfigMenu` ignores negative indices).
