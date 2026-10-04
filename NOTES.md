# NOTES — session state (overwritten each session)

## Current stage
Stage 7.45 (a bound item's menu opens on the next tick; `BACK` with no history) is **done,
reviewed and committed** (stage review 2026-10-03). Full report: `STAGE7.45-REPORT.md`. Next in the
plan: stage 7.5 (bound items and join behaviour). It has not started; its design is still being
settled in planning.

## Verified working (2026-10-03, Paper 26.2-129, Java 25)
- `./gradlew clean build` is clean. The only output besides the task list is plugin-yml's known
  "No mavenCentralProxy" notice (#60). `runServer` enables MyMenu with no warnings of its own,
  loads the menus in `run/`, answers console commands and stops cleanly.
- A temporary in-JVM probe (deleted) passed 32 of 32 checks. It used a stand-in `Player`, real
  scheduler ticks, and steps run from `ServerTickEndEvent`, outside any scheduler task, the way a
  real interact event runs:
  - Bound items: a matching click is cancelled and opens nothing inside the handler; the menu
    opens exactly one tick later. Two matching events in one tick are both cancelled and open once,
    the first request's menu. A non-matching click is not cancelled and schedules nothing. Nothing
    opens when the player is offline by the tick, when the menu was deleted, or when another screen
    is open, and the other screen is not closed. In each case the guard clears, so a new click opens
    again. The player's own inventory, with a survival view (`CRAFTING`) and a creative view
    (`CREATIVE` over a `CRAFTING` top), does not stop the open.
  - `BACK`: on the first menu of a session it closes the menu; `[MENU b, BACK]` returns; in
    `[DELAY 60, MENU b, PLAYER <screen>, BACK]` after the player closed the menu during the delay,
    the other screen stays open; with only deleted menus in its history it closes its own menu;
    with a chest open and no view session it does nothing.
  - `CLOSE` regression: it closes its own session, and leaves another plugin's screen open, both
    in the swap tick and after a delay.
  - Negative control: with the old `back` and with the screen check disabled, exactly the three
    expected checks failed.
- Q1 (what "no screen" looks like) answered from the server jar with `javap`: the top inventory
  reports `CRAFTING` in every game mode, and no plugin can create that type.

## Known broken / open
- **Not verified by eye.** No game client has connected yet. Everything below needs a player:
  - A bound item's menu should feel instant, although it opens one tick after the click.
  - Right-click, holding a bound item, a block that another plugin opens a screen for: that screen
    should stay open, with no flicker of the menu.
  - `[DELAY 60, MENU b, PLAYER <a command that opens another plugin's screen>, BACK]`, closing the
    menu during the delay: `b` opens, the other screen replaces it and stays open.
  - In adventure mode, right-clicking a door or chest with a bound item: the menu should open once
    (the server reads the arm swing as a second click; STAGE7.45-REPORT §5).
  - A real item from `/mymenu give`, used after `/mymenu unset`: it should open nothing, and the
    click should behave as it does for an ordinary item.
  - `[CLOSE, PLAYER <a command that opens another plugin's screen>]`: the menu closes and the
    other screen stays open.
  - `[PLAYER <that command>, CLOSE]`: the other screen stays open.
  - `[DELAY 100, CLOSE]` while the player opens a chest during the delay: the chest stays open.
  - `[MENU b, CLOSE]`, and `[DELAY 60, MENU b, CLOSE]` after the player closes the menu during
    the delay: `b` opens and then closes.
  - Double-click on a button with both `LEFT` and `DOUBLE_CLICK` lists: note which lists run and
    how often (STAGE7.4-REPORT §5, Q2). Under 7.4 a `DOUBLE_CLICK` in the same tick as its `LEFT`
    is refused as pending.
  - Whether the one-tick start of every list is noticeable. The click sound still plays at once.
  - Carried over: `open`, `edit`, `set`, `give`, `name`, the delete cascade's messages and
    closing, reload closing open menus, and tab suggestions as a real client receives them.
- **Pressing Q to drop one item from a stack of bound items probably opens the menu.** Inferred from
  the server code, not observed: the client swings its arm when it drops an item, and Paper reads
  that swing as a left-click on air. The design says dropping never opens the menu, so this is a
  defect against the design (STAGE7.45-REPORT §7). Planning decided the fix for stage 7.5: bound
  items MyMenu hands out become unstackable, so a drop empties the hand. Do not build it before
  the 7.5 prompt names it.
- `/mymenu update` replies that this build cannot check for updates (#90). Finish it at stage
  10 or remove the command.
- Nothing opens a menu on join yet; stage 7.5 builds it.
- No automated tests yet (SPEC §15.4).
- For stage 7.5: entity events call `BoundItemOpener.request`, the same path as the interact
  listener, so the one-open guard and the screen check apply to them unchanged.
- For stage 8:
  - The editor builds action lists through `ActionParser.parseList` (or `readList` for stored
    entries), never one `read` at a time. Every change reaches the model through `MenuService`,
    which refuses a touched list over the delay cap (#97). The `!` shorthand yields an empty node
    list, and the editor attaches the nodes (#85).
  - The editor must call `MenuService.gate()` before acting on a click, and show the reason
    through `Replies.refusal`. A refused layout change already names its slots (#98).
  - `EditCommand` opens the `EDIT` view. `EditSession` holds only its prompt so far, and the
    delete cascade and reload already end prompts.
  - The property editor must refuse an opaque item that does not deserialise (#74).
  - Phase A redesigns session validity; whether `SessionManager.view` should ignore the swap
    marker while a foreign screen is open was left to it (#95).
- `none` is reserved by the command layer, not by the model (#93). A hand-written menu named
  `none` works in every way except being chosen by `/mymenu joinmenu`.

## Needs the user's input
- Nothing. The stage 7.45 review is done. `SPEC.md` was corrected at the review where settled
  rulings since revision 2 contradicted it (§9, §9.2, §9.3, §12, §15.1). The drop-key fix is
  planned for stage 7.5.
