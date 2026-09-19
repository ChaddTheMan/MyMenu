# Stage 5 report — sessions and inventory listeners

Date: 2026-09-19. Paper 26.2 build 124, Java 25. Nothing committed.

## What was built

| File | Role |
|---|---|
| `render/VersionedMenu` | Menu plus the revision it was read at, as one type (Phase A) |
| `render/MenuRenderer` | `render(VersionedMenu, Player, ViewMode)` (signature only) |
| `service/MenuRegistry` | `lookup(name)` returns `Optional<VersionedMenu>` from one snapshot; `revision(name)` removed |
| `session/NavigationStack` | Bounded history (32 entries, oldest dropped) |
| `session/ViewSession` | History and the swap marker; no current-menu field |
| `session/EditSession` | Menu name, swap marker, `PendingPrompt(BukkitTask timeout)` shape for stage 8 |
| `session/SessionManager` | Derived validity, `open`/`navigate`/`back`/`refresh`, reason-aware `closed`, unconditional `endAll`, `closeAll` for disable |
| `listener/InventoryClickListener` | Cancel every click in the view; stale check; slot → model item → key → list; `Dispatcher` seam for stage 6 |
| `listener/InventoryDragListener` | Cancel every drag in the view |
| `listener/InventoryCloseListener` | Hands `MenuHolder` closes and their reason to the session manager |
| `listener/PlayerInteractListener` | Bound-item matching, tag first then mode; main hand; both click types; owns `mymenu:bound_item` |
| `listener/PlayerQuitListener` | `endAll` on quit and kick |
| `MyMenu` | One `ItemBuilder`, one `MenuRenderer`, one `SessionManager`; listeners registered; `onDisable` closes open menus first |

## API facts verified (javap on the 26.2 jar, plus the 26.2 javadoc)

- `InventoryCloseEvent.Reason`: `UNKNOWN`, `TELEPORT`, `CANT_USE`, `UNLOADED`, `OPEN_NEW`, `PLAYER`,
  `DISCONNECT`, `DEATH`, `PLUGIN`. `OPEN_NEW` is "opening new inventory instead". `TELEPORT` is
  deprecated since 1.21.10 and never fires: inventories are not closed on teleport.
- `HumanEntity.openInventory(Inventory)` is `@Nullable` (jspecify). `closeInventory()` delegates to
  `closeInventory(PLUGIN)`.
- `ClickType` has exactly the constants ARCHITECTURE §3 maps. `PlayerInteractEvent.getHand()`
  returns `EquipmentSlot`.
- `Bukkit.getCurrentTick()` exists but is not used (see findings).

## Verification

`./gradlew clean build` is clean. `runServer` without the probe: MyMenu enables, loads the four
menus in `run/`, and disables cleanly on `stop`.

**No game client was driven.** A temporary in-server probe (deleted afterwards, never committed)
ran 69 checks with `java.lang.reflect.Proxy` players and synthetic events fired through the real
`PluginManager`, so the real listeners, session manager and renderer ran; only the player and the
`InventoryView` were fake. The proxy mimicked the server's contract: an open fires `OPEN_NEW` for
the previous menu view, then a cancellable `InventoryOpenEvent`, and returns `null` when cancelled.

What the probe covered, all passing:

- Open creates a session only after a non-null open; a cancelled open leaves no entry; unknown
  menu refused.
- Through the open path: view-permission item hidden, fallback shown, corrupt item as barrier,
  rendered-icon key present and bound-item key absent; the permitted player sees the real items.
- Every click in the top or bottom inventory cancelled; drag cancelled.
- Own key dispatches; `RIGHT` falls to `OTHER`; `DOUBLE_CLICK` never falls through; hidden slots do
  not dispatch for the unpermitted player and do for the permitted one; a barrier slot dispatches
  its three actions; an empty slot dispatches nothing.
- Stale (changed): click refused, one message, not dispatched, not redrawn inside the click; redrawn
  with the new revision on the next pass with the session intact. Stale (deleted): message, closed
  next pass, session gone. `EDIT` view: no stale check, no message, no dispatch, not redrawn.
- Navigate pushes history; back pops and reopens; back with no history closes and ends the session.
- `PLAYER`, `PLUGIN`, `DEATH`, `DISCONNECT`, `UNKNOWN` each end the session.
- A foreign inventory replacing ours: valid during the swap, gone on the next pass.
- A missed close (entry left behind, no event): invalid on read; reopening works with fresh history.
- Edit session with a pending prompt survives a `PLUGIN` close; without the prompt it is gone;
  quit clears both maps and cancels the prompt's timeout task.
- Bound items: `TYPE_AND_NAME` on right-click air and left-click block from the main hand, event
  denied; off hand ignored; name compared with colour stripped; wrong or missing name rejected;
  `TYPE` ignores the name; `EXACT` matches a rebuilt copy and rejects extra lore; `TAG_ONLY`
  ignores an untagged item; a tagged item opens its menu whatever the material; a tag for a deleted
  menu matches nothing, not even by mode; an item carrying only the rendered-icon key opens
  nothing; `PHYSICAL` ignored; an unreadable opaque bound item logs once and breaks nothing.

What the probe could not cover, and a client session should:

- Whether the menu renders as expected by eye (title, lore, glint, italics).
- The real order of `OPEN_NEW` then `InventoryOpenEvent` inside `openInventory`, and what a
  cancelled open leaves on screen. The code is correct under either order because validity is
  derived, but the report of the sequence in Phase A is from the API contract, not observation.
- Real close packets (Escape), death, kick and disconnect through the actual server paths.
- Attempting to take an item out with a real client (shift-click, number key, drop, drag).

## Findings

1. **`Bukkit.getCurrentTick()` is not a usable "one tick" clock from inside tasks.** The probe's
   23 chained delay-0 tasks ran in 120 ms with the counter at 4 throughout. Delay-0 tasks queued
   from inside a task run in the same scheduler pass. The first draft keyed the swap marker on the
   tick number and leaked replaced sessions; the marker is now cleared by a scheduled task.
   DECISIONS #77.
2. **`TELEPORT` never fires.** World change is not a close path. Every reason but `OPEN_NEW` ends
   the session, so future constants cannot leak. DECISIONS #78.
3. **Dispatcher contract.** The seam receives the click's own `ClickKey` and the list selected for
   it, which may be the `OTHER` list. Stage 6 should not re-derive the list.

## Divergences recorded in DECISIONS

#76 `VersionedMenu` placement, #77 swap marker, #78 close reasons, #79 no current-menu field,
#80 whole-view cancellation, #81 close on disable, plus a dated correction on #75.

## Left for later stages (also in NOTES.md)

- No permission is checked on a bound-item open or in `SessionManager.open`; SPEC §4 was outside
  this stage's reading list. Stage 7 adds the gate.
- Click sound and cooldown are not applied; they belong with the stage 6 dispatcher.
- `back` skips deleted menus and closes on empty history; check against SPEC §9.3 in stage 6.
- Hard-coded English in the two stale messages and the bound-item warning; stage 10.
- `VersionedMenu` should move to `model/` when that package is next open.
