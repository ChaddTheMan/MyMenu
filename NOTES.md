# NOTES — session state (overwritten each session)

## Current stage
Stage 5 (`session/`, `listener/`, `render/VersionedMenu`, `MenuRegistry.lookup`, wiring in
`MyMenu`) is **written and verified but not committed**. It waits for the user's review. Stage 4 is
also still uncommitted. Stage 6 has not started. Full report: `STAGE5-REPORT.md`.

## Verified working (2026-09-19, Paper 26.2-124, Java 25)
- `./gradlew clean build` is clean. `runServer` starts, enables MyMenu, loads the four menus in
  `run/`'s `menus.yml`, and disables cleanly.
- A temporary probe (now deleted) passed 69 checks with proxy `Player`s and synthetic events, no
  game client: open creates a session only after a non-null open; cancelled open leaves no entry;
  view-permission hiding and fallback through the open path; every click and drag cancelled;
  own-key, `OTHER` fallback, `DOUBLE_CLICK` no-fallthrough; hidden slots refuse for the unpermitted
  player; a barrier slot still dispatches; stale click refused with a message and redrawn on the
  next scheduler pass with the history intact; deleted menu closes and drops the session; `EDIT`
  skips the stale check; navigate/back/history; every close reason but `OPEN_NEW` ends the session;
  foreign inventory over ours drops the session a pass later; a missed close cannot lock a player
  out; edit session with a pending prompt outlives its inventory; quit clears everything and cancels
  the prompt timeout; bound items by tag, `TYPE`, `TYPE_AND_NAME` (colour stripped), `EXACT`,
  `TAG_ONLY`, main hand only, left and right click, dead tag matches nothing, rendered-icon key does
  not open anything.
- API verified with `javap` against the 26.2 jar and the 26.2 javadoc: `InventoryCloseEvent.Reason`
  constants, `openInventory` is `@Nullable`, `closeInventory()` means `PLUGIN`, `ClickType` set.
- **Not verified by eye.** No one has yet opened a menu in a real client. Real packet ordering for
  `OPEN_NEW` then `InventoryOpenEvent`, and what a cancelled open leaves on screen, are still
  inferred from the API contract (STAGE5-REPORT.md lists what a client session should check).

## Known broken / open
- Nothing known broken. No automated tests yet (SPEC §15.4).
- `Bukkit.getCurrentTick()` did not move across a 23-task chain that took 120 ms; delay-0 tasks
  queued from inside a task run in the same tick. Do not key anything on the tick number
  (DECISIONS #77).
- **Stage 6:** implement `InventoryClickListener.Dispatcher` (the executor); it receives the
  click's own `ClickKey` and the list that was selected for it (own key or `OTHER`). Click sound
  and cooldown (SPEC §8.3) are not applied anywhere yet; they belong with the dispatcher. `MENU`
  and `BACK` should call `SessionManager.navigate` / `back`; check both against SPEC §9.3, which
  stage 5 did not read (`back` skips deleted menus and closes when the history is empty).
- **Stage 7:** bound-item opens and `SessionManager.open` check **no permission**; SPEC §4 was not
  read this stage. Add the gate where the command layer adds its own. `give` must stamp
  `PlayerInteractListener.boundItemKey()` (`mymenu:bound_item`, STRING = menu name). Reload:
  flush first; refuse on unwritten changes; re-apply `setBackupPolicy`/`setDebounce`; add the
  discard form (#71). `VersionedMenu` sits in `render/`; move it to `model/` when `model/` is next
  open (#76).
- **Stage 8:** `EditSession.PendingPrompt` carries only its timeout task; add what the prompt is
  for. Set it *before* closing the inventory, and clear it on resolve. Edit clicks are cancelled and
  otherwise ignored in `InventoryClickListener`. The property editor must refuse an opaque item that
  does not deserialise (#74).
- **Stage 9:** `TokenReplacer` per viewer (#72, #75); replace `viewer -> TokenReplacer.NONE` in
  `MyMenu`. Bound-item name matching parses the template name with no token replacement.
- **Stage 10:** hard-coded English in `MenuRenderer`, `InventoryClickListener` (stale messages),
  `DebouncedMenuWriter`, and `PlayerInteractListener` (unreadable bound item warning) moves to
  `messages.yml`.
- **Stage 11:** readable → `ItemStack` for MySQL is `ItemBuilder.build(descriptive,
  TokenReplacer.NONE)`. Consider excluding `protobuf-java` from the MySQL driver.

## Needs the user's input
- Review and commit stages 4 and 5.
- ARCHITECTURE §5 says `ViewSession` holds the current menu; the code derives it from the open
  holder (#79). ARCHITECTURE §7.5 still implies `ItemSerializer` deserialises (#73). Neither
  document was edited.
- Behaviour to accept or reject: all clicks and drags anywhere in the view are cancelled while a
  menu is open (#80), and open menus are force-closed on disable (#81).
- Behaviour change from stage 4 still pending a verdict (#73): anvil-renamed items capture as bytes.
