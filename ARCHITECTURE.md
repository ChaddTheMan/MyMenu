# MyMenu 2.0.0 — Architecture

This document explains how the plugin is built and why. `SPEC.md` defines behaviour;
this defines structure. Where the two disagree, `SPEC.md` wins and this file is wrong.

---

## 1. The central idea: the model is not the view

**The single most important rule in this codebase: a `Menu` is data. An `Inventory` is a
picture of that data. They are never the same object.**

MyMenu 1.0.4.3 conflated them. `MyMenuMenu` held one `Inventory` field, every viewer
shared that one object, and saving meant reading items back out of it. That design made
four things impossible:

- **Per-viewer content.** View permissions and placeholders resolve differently per
  player. One shared inventory cannot show two different things.
- **A database backend.** Serialisation has to work on data, not on a live UI object.
- **Reliable identification.** 1.x identified menus by comparing inventory *title
  strings*. `Inventory.getName()` no longer exists, and titles were never unique anyway.
- **Testing.** Nothing could be verified without a running server.

Every architectural decision below follows from separating them.

```
MenuRegistry ──owns──> Menu (model)
                        ├── MenuItem[] by slot
                        │    ├── ItemTemplate
                        │    ├── Map<ClickType, List<Action>>
                        │    └── view permission, cooldown, sound, glow
                        └── BoundItem (ItemStack + MatchMode)

MenuRenderer: (Menu, Player) ──> Inventory   [transient, per viewer, never stored]
```

---

## 2. Package layout

Root package: `me.chaddtheman.mymenu`

```
me.chaddtheman.mymenu
├── MyMenu.java                  plugin entry point
├── model/                       Menu, MenuItem, ItemTemplate, MenuType, MatchMode
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
kept the entire in-progress item conversation in static fields, meaning two admins
editing at once corrupted each other's work. Everything here is instance state owned by
the plugin object and passed to what needs it.

---

## 3. Model layer

`Menu` holds: name, title (raw, with colour codes), author, author UUID, type, rows,
bound item, and a `Map<Integer, MenuItem>` of occupied slots.

`MenuItem` holds: an `ItemTemplate`, a `Map<ClickType, List<Action>>`, an optional view
permission, an optional hidden-fallback template, a click sound, a cooldown, and a glow
flag.

`ItemTemplate` is the thing that knows how to become an `ItemStack`. It has two shapes:

- **Descriptive:** material, display name, amount, lore, glow. Text fields hold raw
  strings with colour codes and placeholders **unresolved**.
- **Opaque:** a serialised `ItemStack` byte array.

The distinction exists because most menu items are simple enough for humans to edit in
YAML, and a minority (player heads, potions, banners) are not. Resolution happens at
render time, never at load time, because the same template renders differently per viewer.

Models are mutable but only from the main thread.

---

## 4. Rendering

`MenuRenderer.render(Menu, Player)` returns a fresh `Inventory`:

1. Create the inventory with a `MenuHolder` (see below) and the resolved title.
2. For each slot, if the item has a view permission the player lacks, render the
   hidden-fallback template if present, otherwise leave the slot empty. **Never compact.**
3. Build the `ItemStack` from the template, resolving wildcards and PlaceholderAPI per
   viewer, translating colours last.
4. Apply glow if set: add an enchantment and the flag that hides enchantment text.
5. Stamp the plugin's persistent-data tag on every rendered item.

Rendering is pure with respect to the model. It reads and never writes.

### 4.1 Identification via `MenuHolder`

`MenuHolder implements InventoryHolder` and carries a reference to the `Menu` and the
viewing `Player`. Every listener identifies a menu by:

```java
if (event.getInventory().getHolder() instanceof MenuHolder holder) { ... }
```

This replaces title-string matching entirely. It is exact, survives duplicate titles,
cannot be spoofed by a player renaming something, and does not depend on any removed API.

---

## 5. Sessions

`SessionManager` holds two per-player maps, both cleared on quit.

**`ViewSession`** — which menu is open, and the navigation stack. `MENU` actions push;
`BACK` pops; closing clears. Depth is capped by config.

**`EditSession`** — which menu is being edited, which slot and property are in flight,
and any pending chat-input prompt with its timeout task.

The 1.x "You are already using/editing a menu!" bug came from a global player list whose
removal path was conditional on a title lookup succeeding. When it failed, the entry
leaked and the player was locked out until relog. The rules here:

- Session removal is unconditional on `InventoryCloseEvent` and `PlayerQuitEvent`.
- No code path may leave a session behind on an exception. Cleanup lives in `finally`.
- Chat-input prompts always carry a timeout task, and the task is cancelled when the
  prompt resolves.

---

## 6. Actions

`Action` is a small immutable object: a type plus its fields. `ActionParser` converts
both stored YAML and the prefix shorthand typed in-game (`$` console, `\` message,
`!` elevated, bare for player) into the same typed object. Storage is always the explicit
`type:` field; the prefixes exist only at the input boundary.

`ActionExecutor` runs a list for a player. Because `DELAY` exists, execution cannot be a
simple loop:

- The executor walks the list, running actions until it hits a `DELAY`.
- On a delay it schedules the remainder and returns.
- A pending sequence is tracked per player so a repeat click can be ignored.
- Logout cancels pending sequences. Death and world change do not.
- Total delay per list is validated against the cap **when parsed**, not when run.

`PLAYER_ELEVATED` uses a temporary `PermissionAttachment`, removed in a `finally` block.
Opping and de-opping is forbidden: an exception between the two leaves a player opped.

---

## 7. Storage

```java
public interface MenuStorage {
    CompletableFuture<Collection<Menu>> loadAll();
    CompletableFuture<Void> save(Menu menu);
    CompletableFuture<Void> delete(String name);
    CompletableFuture<Void> saveAll(Collection<Menu> menus);
    void close();
}
```

Two implementations, selected by `storage.type`.

**`YamlMenuStorage`** writes `menus.yml` using the descriptive form where possible and
the opaque form where necessary. Saving is temp-file → rotate previous into `backups/`
→ atomic move. The live file is never written in place, because a crash mid-write
destroys the only copy of a server's menus.

**`MySqlMenuStorage`** uses HikariCP for pooling and always stores items in the opaque
serialised form. Schema is created on first connect and versioned in a `schema_version`
table.

### 7.1 Threading

**All storage input and output happens off the main thread. No exceptions.**

Bukkit's API is single-threaded and effectively none of it is safe to touch from another
thread. So the rule is two-sided:

- I/O runs async and returns a `CompletableFuture`.
- Anything touching the Bukkit API hops back to the main thread before running.

1.x wrote config files *during load*, inside the loading loop, on the main thread. That
is exactly the pattern to avoid.

### 7.2 Item serialisation

`ItemSerializer` uses Paper's byte-array serialisation, which passes through the game's
own data converters and therefore survives version upgrades. Hand-rolled NBT or
`ConfigurationSerializable` round-trips are not used: both lose data on modern items.

---

## 8. Commands

Brigadier lives **only at the edge**. The command tree handles parsing, type conversion,
permission gating via `.requires()`, and suggestions. Subcommand classes receive
already-resolved, typed parameters:

```java
void execute(CommandSender sender, Menu menu, int rows, String title);
```

not

```java
boolean onCommand(CommandSender sender, String[] args);
```

Two benefits. The generics-heavy builder code is confined to one file that declares the
tree; everything else is plain readable Java. And subcommand logic never parses, never
validates types, and never checks permissions, which means it is short and testable.

Registration happens in `onEnable` through
`getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, ...)`, with `mm`
passed as an alias to `Commands.register(...)`. **Verify the exact signatures against the
javadocs for the target Paper version rather than trusting this file.**

Note: registering a `COMMANDS` lifecycle handler disables Bukkit's `/reload` server-wide.
This is expected and acceptable. `/mymenu reload` is unaffected.

Help text is **generated from the subcommand registry**, not hand-written. 1.x had 14 KB
of hand-maintained help strings that had already drifted from the real command set.

---

## 9. Listeners

| Listener | Responsibility |
|---|---|
| `InventoryClickListener` | Cancel all clicks in a menu; dispatch by click type |
| `InventoryDragListener` | Cancel drags across menu slots |
| `InventoryCloseListener` | Unconditional session cleanup |
| `PlayerInteractListener` | Bound-item detection and menu opening |
| `PlayerJoinListener` | `openOnJoin`, `giveItemOnJoin`, update notification |
| `PlayerQuitListener` | Session cleanup, cancel pending action sequences |

**Listeners hold no per-event state in fields.** 1.x stored the event's player, inventory,
and slot as instance fields on singleton listener objects. Within one synchronous handler
that happens to work, but `PlayerJoinListener` then used `this.player` inside a delayed
task, so two players joining within five seconds sent both messages to the same person.
Everything is a local variable or a parameter here.

Click handling must account for click types that did not exist in 1.8: off-hand swap,
number-key hotbar swap, double-click item collection, and drag events. Cancel first,
interpret second.

---

## 10. Text

`TextService` owns the substitution order: native wildcards → PlaceholderAPI → colour
translation. Colour uses Adventure components; `&` codes and `&#RRGGBB` hex are parsed
with the legacy serialiser, and strings prefixed `<!mm>` are parsed as MiniMessage.

PlaceholderAPI is accessed only through `PlaceholderApiHook`, which checks whether the
plugin is enabled and no-ops if not. No PlaceholderAPI class is referenced anywhere else,
so the plugin loads fine without it.

---

## 11. Build

**Gradle**, with:

- `xyz.jpenilla.run-paper` — `./gradlew runServer` downloads Paper, installs the built
  jar, and starts a test server. This is the core development loop.
- A `paper-plugin.yml` generation plugin, so version and dependencies live in one place.
- Shadow, used **only** to shade and relocate bStats, which requires it.

**`paper-plugin.yml`** declares a `loader` class implementing `PluginLoader`, which
resolves HikariCP and the MySQL driver from Maven at load time via
`MavenLibraryResolver`. These are not shaded. No `bootstrapper` is declared; commands
register in `onEnable`.

**Verify all plugin versions and coordinates against current documentation.** Several of
these have moved namespaces over the past few years.

### 11.1 `.gitignore`

The repository was created with GitHub's Java template, which ignores `*.jar`. That will
also ignore `gradle/wrapper/gradle-wrapper.jar`, which **must be committed** or the
wrapper breaks for anyone cloning. Add an explicit negation, plus `.gradle/`, `build/`,
and `run/`.

---

## 12. Bugs from 1.0.4.3 that must not be reproduced

Found by reading the decompiled source. Listed so nobody "faithfully ports" them.

| Bug | Original |
|---|---|
| Static conversation state | `MyMenuItemConversation` kept slot, menu, player, lore, and commands in `static` fields — two simultaneous editors corrupted each other |
| Session leak | `InventoryCloseListener` removed the player only if a title lookup succeeded, permanently locking players out of menus |
| Row clamping | `setRows()` clamped `this.rows` but computed `size` from the unclamped parameter, so `rows: 8` produced a 72-slot `createInventory` call and threw |
| Broken constructors | Four `MyMenuItem` constructors called `this.item.getItemMeta()` before assigning `this.item` |
| Enchantment clearing | `itemMeta.getEnchants().clear()` on an immutable map |
| Wrong colour character | `MyMenuInfo` called `translateAlternateColorCodes('$', ...)` |
| Wrong index check | `removeMenu(String)` validated `index` where it should have validated `configIndex` |
| Delayed-task state | `PlayerJoinListener` used an instance field inside a 100-tick task |
| Load-time writes | Config was mutated and saved during loading, on the main thread |
| No shutdown save | `onDisable()` cleared state without persisting |
| Wrong material map | Seven flower aliases mapped to `WOOL`; `mossy cobblestone wall` mapped to `WOOD_STEP` |
| Bound-item matching | Matched on `getType()` alone, so any compass opened a compass-bound menu |
| Duplicate manifest keys | `plugin.yml` contained the entire `commands:` block twice |
| Global command squat | `/update` was registered as a top-level command |
