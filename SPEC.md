# MyMenu 2.0.0 — Functional Specification

**Author:** ChaddTheMan
**License:** GPL-3.0
**Repository:** https://github.com/ChaddTheMan/MyMenu
**Predecessor:** MyMenu 1.0.4.3 (BukkitDev, January 2015, Minecraft 1.8.1)

This document describes *what* the plugin does. `ARCHITECTURE.md` describes *how* it is
built. `DECISIONS.md` records *why* things differ from 1.0.4.3.

---

## 1. Purpose

MyMenu creates interactive chest-inventory menus ("menus") that players and admins open
in-game. Each slot in a menu holds an item that may display information, run commands,
send messages, or open another menu. Everything is configured in-game; editing files by
hand is supported but never required.

The original plugin's identity is preserved: **easy to use, everything done in game,
highly customisable, full colour support.**

---

## 2. Platform target

| Item | Value |
|---|---|
| Server software | Paper only |
| Minecraft version | Latest release line only (26.x at time of writing) |
| Java | Whatever the target Paper build requires (Java 25 for the 26.x line — **verify**) |
| Plugin manifest | `paper-plugin.yml` |
| Command system | Brigadier, via Paper's `LifecycleEvents.COMMANDS` |
| Backward compatibility | **None.** No 1.x commands, no 1.x data files, no legacy material handling |

Spigot, CraftBukkit, Folia, and older Minecraft versions are explicitly out of scope.

---

## 3. Commands

One root command, `/mymenu`, aliased `/mm`. All arguments are Brigadier-typed.
Subcommands the sender lacks permission for do not appear in suggestions.

| Command | Permission | Player only |
|---|---|---|
| `/mymenu` | `MyMenu.help` | No |
| `/mymenu help [player\|admin]` | `MyMenu.help.player` / `MyMenu.help.admin` | No |
| `/mymenu help command <name>` | as above | No |
| `/mymenu list` | `MyMenu.admin.menu.list` | No |
| `/mymenu open <menu>` | `MyMenu.admin.menu.open` | Yes |
| `/mymenu open <menu> <player>` | `MyMenu.admin.menu.open.other` | No |
| `/mymenu edit <menu>` | `MyMenu.admin.menu.edit` | Yes |
| `/mymenu create <menu> [type] [rows] [title...]` | `MyMenu.admin.menu.create` | Yes |
| `/mymenu delete <menu>` | `MyMenu.admin.menu.delete` | No |
| `/mymenu set <menu> [matchMode]` | `MyMenu.admin.menu.set` | Yes |
| `/mymenu unset <menu>` | `MyMenu.admin.menu.unset` | No |
| `/mymenu info <menu>` | `MyMenu.admin.menu.info` | No |
| `/mymenu name <name...>` | `MyMenu.admin.item.name` | Yes |
| `/mymenu save` | `MyMenu.admin.save` | No |
| `/mymenu reload` | `MyMenu.admin.reload` | No |
| `/mymenu changelog [version]` | `MyMenu.admin.update` | No |
| `/mymenu update` | `MyMenu.admin.update` | No |

### 3.1 Argument detail

- **`<menu>`** — a string argument with a suggestion provider listing menus that exist in
  the registry. Unknown names produce a Brigadier error pointing at the argument.
- **`[type]`** — enum: `CHEST`, `HOPPER`, `DISPENSER`, `DROPPER`. Default `CHEST`.
- **`[rows]`** — integer constrained to `1..6`. Only accepted when type is `CHEST`
  (or omitted). The constraint is enforced by the parser, not by runtime clamping.
- **`[title...]`** — greedy string, `&` colour codes supported. Defaults to the menu name.
- **`[matchMode]`** — enum: `TAG_ONLY`, `TYPE`, `TYPE_AND_NAME`, `EXACT`.
  Default `TYPE_AND_NAME`. See §7.
- **`<player>`** — Paper's player argument type, with online-player suggestions.

### 3.2 `set` behaviour

`/mymenu set <menu>` binds **the item currently in the player's main hand**, captured in
full: material, display name, lore, enchantments, custom model data, and all other
metadata. There is no material-name form of this command.

`/mymenu unset <menu>` removes the binding.

---

## 4. Permissions

Node names are unchanged from 1.0.4.3, with one addition.

```
MyMenu.*
└── MyMenu.admin.*
    ├── MyMenu.help.admin
    │   └── MyMenu.help
    │       └── MyMenu.help.player
    ├── MyMenu.admin.reload
    ├── MyMenu.admin.save
    ├── MyMenu.admin.update
    ├── MyMenu.admin.menu.*
    │   ├── MyMenu.admin.menu.list
    │   ├── MyMenu.admin.menu.open
    │   ├── MyMenu.admin.menu.open.other
    │   ├── MyMenu.admin.menu.edit
    │   ├── MyMenu.admin.menu.create
    │   ├── MyMenu.admin.menu.delete
    │   ├── MyMenu.admin.menu.set
    │   ├── MyMenu.admin.menu.unset
    │   └── MyMenu.admin.menu.info
    └── MyMenu.admin.item.*
        └── MyMenu.admin.item.name

MyMenu.bypass.cooldown        (new in 2.0.0)
```

**No permission is required to open a menu with a bound item.** This is deliberate and
carried over from 1.x: it is how ordinary players use the plugin.

Per-item view permissions (§8.3) use arbitrary admin-chosen nodes and are not part of
this tree.

---

## 5. Files

```
plugins/MyMenu/
├── config.yml
├── messages.yml
├── menus.yml          (YAML storage backend only)
└── backups/
    └── menus-<timestamp>.yml
```

### 5.1 `config.yml`

```yaml
configVersion: 1

storage:
  type: YAML              # YAML | MYSQL
  mysql:
    host: localhost
    port: 3306
    database: mymenu
    username: mymenu
    password: ''
    tablePrefix: mymenu_
    poolSize: 6
    useSsl: false

updateCheck:
  enabled: true
  notifyOnJoin: true

navigation:
  maxDepth: 10

actions:
  maxTotalDelaySeconds: 30

editor:
  chatInputTimeoutSeconds: 60

menus:
  welcome:
    openOnJoin: false
    giveItemOnJoin: false
```

The `storage.type` setting affects **menu data only**. `config.yml` and `messages.yml`
are always files on disk.

### 5.2 `messages.yml`

Every user-facing string lives here. `&` colour codes are supported everywhere; a line
may instead use MiniMessage syntax if it begins with the marker `<!mm>`. Placeholders are
named in angle-free braces, e.g. `{menu}`, `{player}`, `{seconds}`.

### 5.3 `menus.yml`

```yaml
menus:
  welcome:
    inventoryName: '&4&lWelcome to MyMenu!'
    author: ChaddTheMan
    authorUuid: 00000000-0000-0000-0000-000000000000
    type: CHEST
    rows: 3
    boundItem:
      matchMode: TYPE_AND_NAME
      item:
        material: COMPASS
        displayName: '&b&lMenu'
    inventory:
      slots:
        s12:
          item:
            material: CLOCK
            displayName: '&e&lTime: Day'
            amount: 1
            itemLore:
              - '&7Sets the world time to day.'
            glow: false
          viewPermission: mymenu.welcome.time
          hiddenFallback:
            material: GRAY_STAINED_GLASS_PANE
            displayName: '&7Locked'
          clickSound: UI_BUTTON_CLICK
          cooldown: 3
          actions:
            LEFT:
              - type: CONSOLE
                value: 'time set day'
              - type: MESSAGE
                value: '&aTime set to day.'
              - type: CLOSE
            SHIFT_LEFT:
              - type: MENU
                value: 'timeoptions'
            OTHER:
              - type: MESSAGE
                value: '&7Left-click to set day.'
```

Slot keys are `s<index>`, zero-based, carried over from 1.x.

---

## 6. Item representation

An `item:` block is written in one of two mutually exclusive forms. A block never
contains both.

**Readable form** — used whenever the item can be fully expressed by these fields:

```yaml
item:
  material: OAK_SIGN
  displayName: '&b&lMyMenu'
  amount: 1
  itemLore:
    - '&7Line one'
  glow: false
```

**Serialized form** — used when the item carries anything the readable form cannot hold
(player-head textures, potion data, banner patterns, book contents, custom model data,
attribute modifiers, enchantments beyond the glow flag):

```yaml
item:
  serialized: 'H4sIAAAA...'
```

The MySQL backend always uses the serialized form. Hand-editing is a YAML-only affordance.

---

## 7. Bound items and match modes

Items handed out by the plugin carry a hidden persistent-data tag. Matching checks that
tag **first**; the match mode only applies to items the plugin did not dispense.

| Mode | Matches when |
|---|---|
| `TAG_ONLY` | The item carries MyMenu's tag for this menu |
| `TYPE` | Material matches |
| `TYPE_AND_NAME` | Material and display name match (**default**) |
| `EXACT` | `ItemStack.isSimilar()` — all metadata matches |

`TYPE_AND_NAME` is the default because `EXACT` breaks silently when a tool takes damage
or another plugin appends lore.

---

## 8. Menu behaviour

### 8.1 Types and sizes

| Type | Slots |
|---|---|
| `CHEST` | `rows × 9`, rows 1–6 |
| `HOPPER` | 5 |
| `DISPENSER` | 9 |
| `DROPPER` | 9 |

### 8.2 Rendering

Menus are rendered **per viewer**. Two players opening the same menu may see different
contents, because view permissions and placeholders resolve per player.

### 8.3 Per-item properties

| Property | Effect |
|---|---|
| `viewPermission` | Item renders only for players holding this node |
| `hiddenFallback` | Item rendered in place of a hidden item; if absent, the slot is empty |
| `clickSound` | Sound played to the clicker on a successful click |
| `cooldown` | Seconds between uses, per player. In memory only, cleared on restart. Bypassed by `MyMenu.bypass.cooldown` |
| `glow` | Applies the enchantment-glow effect without displaying enchantment text |

Hidden items never cause the menu to compact. Slot positions are stable for all viewers.

### 8.4 Clicks

Actions are keyed by click type. Recognised keys:

`LEFT`, `RIGHT`, `SHIFT_LEFT`, `SHIFT_RIGHT`, `MIDDLE`, `DROP`, `NUMBER_KEY`,
`DOUBLE_CLICK`, and `OTHER`.

`OTHER` is a **fallback, not an addition**: a click runs exactly one action list. If the
click type has its own key, that list runs. Otherwise `OTHER` runs. If neither exists,
nothing happens and the menu stays open.

All clicks inside a menu are cancelled. Items are never removed from a menu by a player.

---

## 9. Action types

| Type | Field | Effect |
|---|---|---|
| `PLAYER` | `value` | Player runs the command with their own permissions |
| `CONSOLE` | `value` | Console runs the command |
| `PLAYER_ELEVATED` | `value`, `permissions` | Player runs the command with the listed nodes temporarily granted |
| `MESSAGE` | `value` | Sends the player a message |
| `MENU` | `value` | Opens another menu |
| `BACK` | — | Returns to the previous menu in the navigation stack |
| `CLOSE` | — | Closes the menu |
| `DELAY` | `ticks` | Pauses before the next action in the list |

### 9.1 Elevation

`PLAYER_ELEVATED` grants the named permission nodes through a temporary
`PermissionAttachment`, dispatches the command, and revokes the attachment immediately
afterwards, including on exception. **The player is never opped, at any point, for any
reason.**

### 9.2 Delays and sequencing

A list containing a `DELAY` suspends and resumes on a later tick.

- Total delay across one list is capped by `actions.maxTotalDelaySeconds` (default 30).
- A pending sequence is **cancelled** if the player logs out.
- A pending sequence **continues** through death and world changes.
- If the player clicks the same item again while a sequence is pending, the new click is
  **ignored**.

### 9.3 Navigation

The plugin keeps a per-player navigation stack.

- `MENU` **pushes** a new entry, even if that menu is already in the stack.
- `BACK` pops one entry. With an empty stack it closes the menu.
- Depth is capped by `navigation.maxDepth` (default 10). Exceeding it refuses the action
  and logs a warning.
- Closing a menu outright clears the stack.

---

## 10. Text substitution

Order of substitution: native wildcards, then PlaceholderAPI (if installed), then colour
translation.

Native wildcards, carried over from 1.x:

| Wildcard | Value |
|---|---|
| `{PLAYER}` | Viewer's name |
| `{WORLD}` | Viewer's world name |
| `{LOCATION-X}` `{LOCATION-Y}` `{LOCATION-Z}` | Viewer's block coordinates |
| `{SERVER}` | Server name from `server.properties` |

`{SERVER}` returning the *software* name was a 1.x bug and is fixed here.

**PlaceholderAPI** is a soft dependency. If present, all display names, lore, and message
action text are passed through it. If absent, placeholder tokens are left as written.

Colour: `&` codes everywhere, including hex `&#RRGGBB`. MiniMessage is available on any
string prefixed `<!mm>`.

---

## 11. Editing

In-game editing is the primary interface. `/mymenu edit <menu>` opens the menu in edit
mode.

### 11.1 Placing items

Placing an item into a slot from your own inventory sets that slot's item, captured in
full. Typing a material name is available as a fallback for items you do not have.

### 11.2 Property editor

Right-clicking a placed item opens a nested GUI. Each slot is a property control:

- Display name
- Lore (opens a further screen: one slot per line, click to edit, shift-click to delete,
  an add control at the end)
- Amount (left-click increases, right-click decreases)
- Actions (per click type, with an action-type selector)
- Cooldown
- View permission
- Glow toggle
- Click sound
- Delete item

Menu-level settings (title, rows, type, bound item, join behaviour) are reached from an
equivalent control while editing the menu itself.

### 11.3 Text input

Free text is collected by chat input: the menu closes, the prompt is shown, and the
previous screen reopens when input completes or is cancelled.

Chat input must have a timeout (`editor.chatInputTimeoutSeconds`), a working cancel
(`mmcancel`), and automatic cleanup on disconnect. Editor state is **per player**; two
admins editing simultaneously must never interfere.

### 11.4 Visibility of edits

Changes are written to the model immediately. Other players with the menu already open
see the old rendering until they reopen it. Edits are not pushed to open screens.

### 11.5 Saving

Saving writes to a temporary file, rotates the previous file into `backups/`, then moves
the temporary file into place. The live file is never written in-place.

---

## 12. Join behaviour

Per menu, in `config.yml`:

- `openOnJoin` — the menu opens when a player joins.
- `giveItemOnJoin` — the player receives the bound item if they do not already have one
  carrying the tag.

---

## 13. Metrics and updates

- **bStats** for anonymous usage statistics. Requires a numeric plugin ID from
  https://bstats.org — **left as a placeholder until registered.**
- **Update check** queries `https://api.github.com/repos/ChaddTheMan/MyMenu/releases/latest`
  and compares against the running version. It **notifies only**. The plugin never
  downloads or replaces itself.

---

## 14. Explicitly out of scope for 2.0.0

Deferred, in rough priority order:

1. Single-menu export and import
2. `SERVER` action type for proxy networks (BungeeCord/Velocity)
3. Per-menu command aliases (`/shop`)
4. Unit tests via MockBukkit
5. Economy costs and per-menu/per-item use limits
6. Dynamic menus with generated content and pagination
7. Live-refreshing menus
8. Animated items
9. Per-world and per-group menu scoping beyond per-item view permissions

Permanently rejected: a scripting language for actions.
