# MyMenu 2.0.0 — Functional Specification

**Author:** ChaddTheMan
**License:** GPL-3.0
**Repository:** https://github.com/ChaddTheMan/MyMenu
**Predecessor:** MyMenu 1.0.4.3 (BukkitDev, January 2015, Minecraft 1.8.1)
**Revision:** 2 — incorporates `AUDIT.md` findings

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
| Minecraft version | Latest release line only (26.x) |
| Java | 25 (confirmed requirement for the 26.1+ line) |
| Plugin manifest | `paper-plugin.yml` |
| Command system | Brigadier, via Paper's `LifecycleEvents.COMMANDS` |
| Backward compatibility | **None.** No 1.x commands, no 1.x data files, no legacy material handling |

Spigot, CraftBukkit, Folia, and older Minecraft versions are out of scope.

---

## 3. Commands

One root command, `/mymenu`, aliased `/mm`. All arguments are Brigadier-typed.
Subcommands the sender lacks permission for do not appear in suggestions.

| Command | Permission | Player only |
|---|---|---|
| `/mymenu` | `MyMenu.help.player` | No |
| `/mymenu help [player\|admin]` | `MyMenu.help.player` / `MyMenu.help.admin` | No |
| `/mymenu help command <name>` | the permission of the named subcommand | No |
| `/mymenu list` | `MyMenu.admin.menu.list` | No |
| `/mymenu open <menu>` | `MyMenu.admin.menu.open` | Yes |
| `/mymenu open <menu> <player>` | `MyMenu.admin.menu.open.other` | No |
| `/mymenu edit <menu>` | `MyMenu.admin.menu.edit` | Yes |
| `/mymenu create <menu> [rows] [title...]` | `MyMenu.admin.menu.create` | Yes |
| `/mymenu delete <menu>` | `MyMenu.admin.menu.delete` | No |
| `/mymenu set <menu> [matchMode]` | `MyMenu.admin.menu.set` | Yes |
| `/mymenu unset <menu>` | `MyMenu.admin.menu.unset` | No |
| `/mymenu give <menu> [player]` | `MyMenu.admin.menu.give` | No |
| `/mymenu joinmenu <menu\|none>` | `MyMenu.admin.joinmenu` | No |
| `/mymenu info <menu>` | `MyMenu.admin.menu.info` | No |
| `/mymenu name <name...>` | `MyMenu.admin.item.name` | Yes |
| `/mymenu save` | `MyMenu.admin.save` | No |
| `/mymenu reload` | `MyMenu.admin.reload` | No |
| `/mymenu changelog [version]` | `MyMenu.admin.update` | No |
| `/mymenu update` | `MyMenu.admin.update` | No |

### 3.1 Argument detail

- **`<menu>`** — string argument with a suggestion provider listing existing menus.
- **`[rows]`** — integer constrained to `1..6`, parser-enforced. Default 3.
- **`[title...]`** — greedy string, `&` colour codes supported. Defaults to the menu name.
- **`[matchMode]`** — enum: `TAG_ONLY`, `TYPE`, `TYPE_AND_NAME`, `EXACT`. Default
  `TYPE_AND_NAME`.
- **`<player>`** — Paper's player argument type, with online-player suggestions.

`create` deliberately does **not** take a menu type. Chest is the default and the type is
changed in the editor, because an optional enum argument sitting before a greedy string
makes `/mymenu create shop Chest Shop` ambiguous.

### 3.2 Menu names

Menu names are restricted to `[a-z0-9_-]`, maximum 32 characters, and are lowercased on
input. This prevents `.` from breaking YAML paths and removes case-collision confusion.
**Titles** are unrestricted: full colour, Unicode, any length the client accepts.

### 3.3 `set`, `unset`, `give`

`/mymenu set <menu>` binds **the item currently in the player's main hand**, captured in
full: material, display name, lore, enchantments, custom model data, all metadata. There
is no material-name form.

`/mymenu unset <menu>` removes the binding.

`/mymenu give <menu> [player]` hands out a tagged copy of the bound item. Without it,
`TAG_ONLY` would be reachable only through join behaviour.

### 3.4 `joinmenu`

`/mymenu joinmenu <menu|none>` sets `joinMenu` in `config.yml`. It exists because the
plugin promises that hand-editing files is never required, and `joinMenu` is otherwise
unreachable in-game. It is the only runtime writer of `config.yml`.

### 3.5 `delete`

Deleting writes a backup copy of the menu to `backups/` before removal. Delete backups are
**exempt from `backups.keep`** and are never pruned. There is no confirmation prompt: a
recoverable backup is more useful than a prompt people learn to click through.

Deleting then cascades. It:

- closes open views of that menu with a message;
- ends edit sessions on it and cancels their pending prompts;
- purges it from every navigation stack;
- cancels pending `MENU` actions targeting it;
- warns if `joinMenu` names it.

### 3.6 `save` and `reload`

Mutations mark a menu dirty and schedule a **debounced** write, so rapid edits coalesce
into one. `save` flushes all pending writes immediately and reports how many menus it
wrote. Under normal use there is usually nothing pending.

`reload` flushes pending writes and waits for the storage queue to drain **before**
re-reading, or it would read a half-written state. It then re-reads `config.yml`,
`messages.yml`, and all menu data, closes every open menu with a message, ends all edit
sessions, and cancels all pending action sequences. Flushing first is what makes the claim
"reload cannot discard unsaved work" true.

`reload` does **not** switch `storage.type` at runtime. Changing the backend requires a
server restart, and `reload` says so if it detects a change.

### 3.7 `changelog` and `update`

`changelog [version]` reads a `CHANGELOG.md` bundled in the jar.

`update` performs the version check immediately and reports the result. It never
downloads, installs, or replaces anything.

---

## 4. Permissions

Node names are unchanged from 1.0.4.3 except for three additions: `menu.give`,
`joinmenu`, and `bypass.cooldown`.

```
MyMenu.*                                    default: op
└── MyMenu.admin.*
    ├── MyMenu.help.admin
    │   └── MyMenu.help
    │       └── MyMenu.help.player          default: true
    ├── MyMenu.admin.reload
    ├── MyMenu.admin.save
    ├── MyMenu.admin.update
    ├── MyMenu.admin.joinmenu                (new)
    ├── MyMenu.admin.menu.*
    │   ├── MyMenu.admin.menu.list
    │   ├── MyMenu.admin.menu.open
    │   ├── MyMenu.admin.menu.open.other
    │   ├── MyMenu.admin.menu.edit
    │   ├── MyMenu.admin.menu.create
    │   ├── MyMenu.admin.menu.delete
    │   ├── MyMenu.admin.menu.set
    │   ├── MyMenu.admin.menu.unset
    │   ├── MyMenu.admin.menu.give          (new)
    │   └── MyMenu.admin.menu.info
    └── MyMenu.admin.item.*
        └── MyMenu.admin.item.name

MyMenu.bypass.cooldown                      (new) default: false, NOT a child of MyMenu.*
```

1.0.4.3 declared no defaults at all, making every node op-only including player help.
2.0.0 sets `MyMenu.help.player` to true and everything else to op.

`MyMenu.bypass.cooldown` is deliberately outside the `MyMenu.*` tree and defaults to
false, so ops experience cooldowns while testing them.

**No permission is required to open a menu with a bound item.** Carried over from 1.x:
it is how ordinary players use the plugin.

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
    ├── menus-<timestamp>.yml
    └── deleted-<menu>-<timestamp>.yml
```

### 5.1 `config.yml`

```yaml
configVersion: 1

serverName: 'My Server'   # source for the {SERVER} wildcard

joinMenu: ''              # menu opened on join; empty disables

storage:
  type: YAML              # YAML | MYSQL
  writeDebounceMillis: 2000
  mysql:
    host: localhost
    port: 3306
    database: mymenu
    username: mymenu
    password: ''
    tablePrefix: mymenu_
    poolSize: 6
    useSsl: false

backups:
  keep: 10                # rotating save backups; delete backups are exempt
  minIntervalSeconds: 300 # at most one save backup per interval

updateCheck:
  enabled: true
  notifyOnJoin: true

navigation:
  maxDepth: 10

actions:
  maxTotalDelaySeconds: 30

editor:
  chatInputTimeoutSeconds: 60
```

`serverName` exists because modern `server.properties` has no server-name key and
`Server#getName()` returns the software name — the 1.x bug this replaces.

`joinMenu` is a **single value**, not a per-menu flag. 1.x allowed several menus to set
`OpenOnJoin` and silently let the last one win.

The bundled default file contains only the keys implemented so far; a key is added in the
stage that reads it, because shipping unread keys implies settings that do nothing. Bad
values fall back to defaults with a warning, and the file is never rewritten.

`storage.type` affects **menu data only**. `config.yml` and `messages.yml` are always
files on disk.

### 5.2 `messages.yml`

Every user-facing string. `&` colour codes are supported everywhere; a line may instead
use MiniMessage syntax if it begins with `<!mm>`. Placeholders use braces: `{menu}`,
`{player}`, `{seconds}`.

### 5.3 `menus.yml`

```yaml
menus:
  welcome:
    inventoryName: '&4&lWelcome to MyMenu!'
    author: ChaddTheMan
    authorUuid: 00000000-0000-0000-0000-000000000000
    type: CHEST
    rows: 3
    giveItemOnJoin: false
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
          clickSound: minecraft:ui.button.click
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

`giveItemOnJoin` lives on the menu, not in `config.yml`, because it is menu data and
must travel with the menu when storage is MySQL.

---

## 6. Item representation

An `item:` block takes one of two mutually exclusive forms. Never both in one block.

**Readable form** — used whenever the item is fully expressible by these fields:

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
attribute modifiers, real enchantments):

```yaml
item:
  serialized: 'H4sIAAAA...'
```

`glow` lives **inside the `item:` block** and is the only field valid alongside
`serialized:`. It is a rendering flag applied through the enchantment glint override, not a
real enchantment, so it never forces the serialized form and works identically for both
shapes.

Serialized items remain editable in the property editor: the stack is deserialized, its
metadata changed, and re-serialized. Wildcards and placeholders resolve against the
deserialized metadata at render time, like any other item.

The MySQL backend always uses the serialized form. Hand-editing is a YAML-only
affordance.

---

## 7. Bound items and match modes

Items handed out by the plugin carry a hidden persistent-data tag. Matching checks that
tag **first**; the match mode applies only to items the plugin did not dispense.

| Mode | Matches when |
|---|---|
| `TAG_ONLY` | The item carries MyMenu's bound-item tag for this menu |
| `TYPE` | Material matches |
| `TYPE_AND_NAME` | Material and display name match, colour stripped (**default**) |
| `EXACT` | `ItemStack.isSimilar()` — all metadata matches |

1.0.4.3 supported the equivalents of `TYPE` and `TYPE_AND_NAME`, with `TYPE` as the
default. The change in 2.0.0 is the default, plus capturing the whole ItemStack instead
of a material name.

`TYPE_AND_NAME` compares plain text with colour stripped, which is the forgiving
behaviour that motivated making it the default.

**Bound items and rendered menu icons use different tag keys.** Creative-mode players can
clone a rendered icon from an open menu; if the keys were shared, every cloned icon would
become a menu opener.

Where several menus would match the same held item, the first by menu name wins, and the
condition is logged once at load time.

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
| `hiddenFallback` | Rendered in place of a hidden item; if absent, the slot is empty |
| `clickSound` | Sound played to the clicker on a successful click. Stored as a namespaced key (`minecraft:ui.button.click`); bare names accepted case-insensitively on input |
| `cooldown` | Seconds between uses, per player. In memory only, cleared on restart. Bypassed by `MyMenu.bypass.cooldown` |
| `glow` | Enchantment glint override, with no real enchantment |

Hidden items never cause the menu to compact. Slot positions are stable for all viewers.

### 8.4 Clicks

Actions are keyed by click type. Recognised keys:

`LEFT`, `RIGHT`, `SHIFT_LEFT`, `SHIFT_RIGHT`, `MIDDLE`, `DROP`, `NUMBER_KEY`,
`DOUBLE_CLICK`, `OTHER`.

`OTHER` is a **fallback, not an addition**: at most one list runs per click. If the click
type has its own key, that list runs; otherwise `OTHER` runs; otherwise nothing happens
and the menu stays open.

**`DOUBLE_CLICK` never falls through to `OTHER`.** It runs only when explicitly keyed.
The client sends a `LEFT` click before a `DOUBLE_CLICK`, so mapping both means both run.

All clicks and drags inside a menu are cancelled, in both view and edit mode. Items are
never removed from a menu by a player. Edit mode interprets the cancelled click as an
editing operation rather than ignoring it; see §11.

### 8.4.1 Shrinking a menu

Changing a menu's type or row count so that occupied slots would fall outside the new
bounds is **refused**, and the refusal names the offending slots. Dropping those items is
data loss; keeping them unrendered creates state that exists in storage and appears
nowhere. The admin clears the slots first.

### 8.5 Stale views

A rendered inventory records the **menu name** and a revision drawn from a single
plugin-wide counter. On click, the name is looked up in the registry:

- absent → the menu was deleted;
- present with a different revision → the menu changed since rendering.

Either refuses the click with a message and re-renders **on the next tick**.

Holding the `Menu` object and a per-menu counter would not work: delete and reload leave
the old object untouched, so its revision would still match, and a delete followed by a
recreate could produce the same number again. A global counter and a name lookup avoid
both.

**Edit mode is exempt.** Every edit bumps the revision, so an editing admin would
invalidate their own next click.

---

## 9. Action types

| Type | Fields | Effect |
|---|---|---|
| `PLAYER` | `value` | Player runs the command with their own permissions |
| `CONSOLE` | `value` | Console runs the command |
| `PLAYER_ELEVATED` | `value`, `permissions` | Player runs the command with the listed nodes temporarily granted |
| `MESSAGE` | `value` | Sends the player a message |
| `MENU` | `value` | Opens another menu |
| `BACK` | — | Returns to the previous menu in the navigation stack |
| `CLOSE` | — | Closes the menu |
| `DELAY` | `ticks` | Pauses before the next action in the list |

`PLAYER` actions dispatch through `Player#performCommand`, which does not fire the
command preprocess event.

### 9.1 Elevation

`PLAYER_ELEVATED` grants the named nodes through a temporary `PermissionAttachment`,
dispatches the command, and revokes the attachment in a `finally` block. **The player is
never opped, at any point, for any reason.**

### 9.2 Delays and sequencing

A list containing a `DELAY` suspends and resumes on a later tick.

- Total delay per list is capped by `actions.maxTotalDelaySeconds` (default 30). Stored
  lists exceeding the cap are **clamped with a warning**, never rejected.
- A pending sequence is cancelled when the player logs out.
- A pending sequence continues through death and world change.
- Only one sequence runs **per player** at a time. A click while one is pending is
  ignored.

### 9.3 Navigation

A per-player navigation stack.

- `MENU` **pushes**, even if the target is already in the stack. It works after a delay
  even if the player closed the menu.
- `BACK` pops one entry. With an empty stack it does nothing.
- Depth is capped by `navigation.maxDepth` (default 10). Exceeding it refuses the action
  and logs a warning.
- Closing a menu outright clears the stack.

`MENU`, `BACK`, and `CLOSE` are scheduled for the next tick, because opening or closing
an inventory from inside a click handler is not supported.

---

## 10. Text substitution

Substitution order: native wildcards, then PlaceholderAPI (if installed), then colour
translation.

| Wildcard | Value |
|---|---|
| `{PLAYER}` | Viewer's name |
| `{WORLD}` | Viewer's world name |
| `{LOCATION-X}` `{LOCATION-Y}` `{LOCATION-Z}` | Viewer's block coordinates |
| `{SERVER}` | `serverName` from `config.yml` |

Wildcard names are **case-sensitive and upper-case**. Message placeholders in
`messages.yml` are lower-case (`{player}`). They are separate systems and do not
interchange.

**Substitution applies to display names, lore, message text, and command values.** 1.x
applied wildcards to command strings only; the primary use case was
`$give {PLAYER} diamond 64`, and omitting command values would break it.

Colour translation is **not** applied to command values. 1.x translated `&` inside
commands, mangling any command containing an ampersand.

### 10.1 Injection safety

**Substitution happens after parsing, never before.**

- Substituted values are inserted as data and are never re-parsed as MiniMessage, so
  player-controlled text (nicknames, placeholder output) cannot inject markup.
- Values substituted into command strings are sanitised: newlines, carriage returns,
  semicolons, and leading slashes are stripped.

Without this, a player with a crafted nickname could inject arbitrary commands into a
`CONSOLE` action. This is command injection and is treated as a security requirement, not
a nicety.

### 10.2 Colour

`&` codes everywhere, including hex `&#RRGGBB`. MiniMessage is available on any
admin-authored string prefixed `<!mm>`.

**Readable text renders with italic explicitly disabled** unless the text sets it. Minecraft
italicises custom item names by default, which is almost never what an admin wants, and
practically every modern plugin turns it off. The item-capture check (§6) builds its readable
candidate the same way, so that an item carrying `italic: false` still round-trips to the
readable form instead of being pushed into bytes for a difference nobody asked for.

---

## 11. Editing

In-game editing is the primary interface. `/mymenu edit <menu>` opens the menu in edit
mode.

### 11.1 Edit-mode rendering

An edit view differs from a normal view in three ways:

- **View permissions are not applied.** An admin who lacks an item's node must still be
  able to see and edit it. Hidden-only items are rendered with a marker in their lore.
- Clicks are interpreted as editing operations rather than actions. No action list ever
  runs in edit mode.
- The stale-view check is skipped (§8.5).

### 11.2 Placing items

Placing an item into a slot **copies** it. The admin's own item is never consumed.

- An occupied slot is replaced.
- Shift-click is cancelled.
- An item left on the cursor when the editor closes is returned to the admin's inventory,
  never dropped. 1.x dropped a real item into the world and deleted the menu entry.
- Moving an item between slots is an operation in the property GUI, replacing 1.x's
  middle-click move, which lost data.

Typing a material name remains available for items the admin does not have.

### 11.3 Property editor

Right-clicking a placed item opens a nested GUI, one control per slot:

- Display name
- Lore (further screen: one slot per line, click to edit, shift-click to delete, add
  control at the end)
- Amount (left-click up, right-click down)
- Actions (per click type, with an action-type selector)
- Cooldown
- View permission
- Glow toggle
- Click sound
- Move to another slot
- Delete item

Menu-level settings (title, rows, type, bound item, `giveItemOnJoin`) are reached from an
equivalent control while editing the menu itself.

### 11.4 Text input

Free text is collected by chat input: the menu closes, the prompt is shown, and the
previous screen reopens when input completes or is cancelled.

- Chat events are asynchronous; handling hops to the main thread.
- The typed message is cancelled so it never reaches public chat. Other players' chat is
  **not** suppressed to the admin; 1.x's modal conversation blacked out incoming chat,
  which is worse than the problem it solved.
- A timeout (`editor.chatInputTimeoutSeconds`), a working `mmcancel`, and automatic
  cleanup on disconnect are all mandatory.
- Editor state is **per player**. Two admins editing simultaneously must never interfere.

Paper's Dialog API may be a better mechanism than chat input. Evaluate at implementation
time; chat input is the fallback design.

### 11.5 Visibility of edits

Changes reach the model immediately and storage shortly after, via the debounced write.
Players with the menu already open keep the old rendering until they reopen it, and §8.5's
stale-view check prevents them acting on it.

### 11.6 Saving

Mutations are **debounced** (`storage.writeDebounceMillis`, default 2000) so a run of
rapid edits — ten clicks on the amount control — produces one write rather than ten.

A write is: copy the live file into `backups/`, write a temporary file, atomically move the
temporary file over the live file. The live file is never written in place, and there is
never a moment with no live file.

Backup policy is separate from save policy. At most one save backup is taken per
`backups.minIntervalSeconds` (default 300), and `backups.keep` (default 10) bounds
retention. Without both, saving on every edit would push every useful backup out of
retention within a minute. **Delete backups are exempt from `keep` and are never pruned.**

Under MySQL there is no live file to copy. Save backups do not apply; delete backups are
written as standalone YAML files in `backups/`.

---

## 12. Degraded state

Storage enters a **degraded** state when either:

- an entry cannot be parsed at load time (unknown material, malformed slot, missing
  section) — the entry is skipped and logged with menu name and slot; or
- a write fails at runtime (I/O error, database unreachable) — logged, and online admins
  are notified.

While degraded:

1. **Mutations are refused before they reach the model**, not at the storage boundary. A
   mutation that changed memory and then failed to persist would be discarded by the next
   reload, contradicting §3.6.
2. Refused mutations include editing, `create`, `delete`, `set`, `unset`, and `joinmenu`.
   `save` reports the condition instead of writing.
3. **Read-only commands keep working**: `list`, `info`, `open`, `help`, `changelog`. Players
   can still use existing menus.
4. Every refused command explains why.
5. `/mymenu reload` re-checks and clears the state if the data now loads and writes.

**Unwritten changes block a reload, and that needs an escape hatch.** When a write has failed,
the changes stay in memory flagged unwritten, and reloading would discard them — so reload
refuses. If the underlying fault is permanent (disk full, permissions, a stray file where the
temp file goes), the admin is otherwise stuck: unable to write, unable to reload, and losing
the changes at shutdown regardless. `/mymenu reload` therefore accepts an explicit discard
form that drops the unwritten changes and re-reads from disk, and the ordinary refusal message
names it.

This exists because the alternative is silent data loss: skip a bad entry, save later, and
the skipped menus are gone.

---

## 13. Join behaviour

- `joinMenu` in `config.yml` names the single menu opened on join, 20 ticks after join.
  It is a **per-server** setting and deliberately stays out of menu data, so servers
  sharing a MySQL database can open different menus. If it names a menu that does not
  exist, a warning is logged once at load and nothing opens on join.
- Any menu with `giveItemOnJoin: true` gives its bound item, if the player does not
  already hold an item carrying that menu's tag. Tag matching replaces 1.x's exact
  ItemStack comparison, which included stack size. If such a menu has **no bound item**, a
  warning is logged at load and nothing is given.
- A player who already has an active session is skipped, as in 1.x.
- The update notification, if enabled, is sent 100 ticks after join, to the joining player
  specifically.

---

## 14. Metrics

**bStats plugin ID: 34120.** Registered; no placeholder remains.

bStats collects server environment data automatically (server software and version,
Minecraft version, plugin version, player count, online mode, Java version, OS and
architecture, core count, approximate country). Nothing needs to be written for those.

### 14.1 Privacy rule

**bStats data is public — anyone can view the charts.** Therefore:

- **Shapes, never values.** Action *types* are reported; action *values* are never
  reported. A stored command string can contain player names, addresses, or an admin's
  private command syntax.
- **No names of any kind.** Not menu names, not permission nodes, not item display names,
  not the server name.
- **Nothing per-player.**

Every chart below reports counts and enum constants only. If a proposed future chart
cannot be expressed that way, it does not get added.

### 14.2 Charts

Chart IDs are fixed and must match exactly on both sides (see §14.3).

| Chart ID | Type | Value | Question it answers |
|---|---|---|---|
| `storage_backend` | Simple pie | `YAML` or `MYSQL` | Is the MySQL backend worth maintaining? |
| `menu_count` | Simple pie | Bucket: `1`, `2-5`, `6-10`, `11-25`, `26+` | Does this need pagination or per-menu files? |
| `items_per_menu` | Advanced pie | Bucket → count of menus: `1-5`, `6-15`, `16-30`, `31-54` | Are people filling large menus or using small ones? |
| `menu_types` | Advanced pie | `MenuType` → count | Were hopper and dispenser menus worth adding? |
| `action_types` | Advanced pie | `ActionType` → count | Which of the eight types justify their code? Is `PLAYER_ELEVATED` used? |
| `match_modes` | Advanced pie | `MatchMode` → count of bound menus | Was `TYPE_AND_NAME` the right default? |
| `optional_features` | Advanced pie | Feature → count of items using it: `click_types`, `view_permission`, `cooldown`, `glow`, `hidden_fallback`, `click_sound` | Which 2.0 additions were actually adopted? |
| `placeholderapi` | Simple pie | `Yes` or `No` | Does the soft dependency matter? |
| `total_menus` | Single line | Menu count on this server | Total menus in existence across all servers |

Counts are computed from the in-memory registry at submission time. No storage access, no
main-thread work of consequence.

### 14.3 Charts must also be registered on the website

Adding a custom chart is **two steps**: supply the data in code, and create the chart on
the plugin's bStats page with the **same chart ID**. A chart that exists only in code
shows "No data to display" indefinitely, with nothing in the server log to explain why.

The IDs in §14.2 are the contract. Create all nine on the website before release.

### 14.4 Opt-out

bStats' server-wide toggle in `plugins/bStats/config.yml` is the only opt-out. MyMenu adds
no second switch, because two toggles imply granularity that does not exist and create a
confusing state where one says yes and the other no.

---

## 14A. Update checking

Written fresh. 1.x's 536-line `Updater` targeted the defunct BukkitDev API and was built
around downloading and installing a replacement jar; none of it is reusable.

1. HTTP GET `https://api.github.com/repos/ChaddTheMan/MyMenu/releases/latest`, off the main
   thread, with connect **and** read timeouts and a `User-Agent` header (GitHub rejects
   requests without one).
2. Read `tag_name` only. Paper bundles Gson, so no extra dependency.
3. Strip the leading `v`: tag `v2.1.0` → version `2.1.0`.
4. Compare **semantically**: split on dots, compare segments numerically, so `2.10.0` is
   newer than `2.9.0`. 1.x used `!equalsIgnoreCase`, so any difference counted as an
   update, including an older remote version.
5. If newer: log at startup, and notify holders of `MyMenu.admin.update` on join.

Required handling:

- **404 means no published releases**, not an error. The repository has none until 2.0.0
  ships; treat it as "nothing to report" and stay silent.
- **GitHub's unauthenticated limit is 60 requests per hour per IP.** Shared hosts put many
  servers behind one address. Cache the result, check at most once every few hours
  regardless of restarts, and treat a rate-limit response as "unknown" at debug level.
- `/releases/latest` excludes drafts and pre-releases, which is the wanted behaviour.

It **notifies only**. The plugin never downloads, installs, or replaces anything.

---

## 15. Explicitly out of scope for 2.0.0

1. Single-menu export and import
2. `SERVER` action type for proxy networks (BungeeCord/Velocity)
3. Per-menu command aliases (`/shop`)
4. Unit tests via MockBukkit
5. Economy costs and per-menu/per-item use limits
6. Dynamic menus with generated content and pagination
7. Live-refreshing menus
8. Animated items
9. Per-world and per-group menu scoping beyond per-item view permissions
10. Cross-server synchronisation over MySQL

### 15.1 Opening a menu with a bound item

A bound item opens its menu on **either left- or right-click**, matching 1.x, from the
**main hand only**. Paper fires the interaction event once per hand; the off-hand event is
ignored. The event is cancelled so the item's normal behaviour does not also fire.

---

**MySQL is single-writer in 2.0.0.** Several servers sharing one database will overwrite
each other's edits, and nothing signals other servers to reload.

Permanently rejected: a scripting language for actions.
