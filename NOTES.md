# NOTES — session state (overwritten each session)

## Current stage
Stage 7.4 (fixes from the stage 7.25 audit) is **committed**, after its stage review on
2026-10-02. Full report: `STAGE7.4-REPORT.md`. Next in the plan: stage 7.45 (what 7.4's file limits
left out: `PlayerInteractListener` opens a bound item's menu on the next tick, and `BACK` with no
history closes only under `CLOSE`'s conditions), then 7.5 (bound items and join behaviour).
Neither has started.

## Verified working (2026-10-01, Paper 26.2-129, Java 25)
- `./gradlew clean build` is clean. The only output besides the task list is plugin-yml's known
  "No mavenCentralProxy" notice (#60). `runServer` enables MyMenu with no warnings of its own,
  loads the menus in `run/`, answers console commands and stops cleanly.
- A temporary in-JVM probe (deleted) passed 95 of 95 checks, using a stand-in `Player` that records
  messages, commands, opens and closes:
  - Action lists: the player is pending at once and nothing has run yet; the list runs on the
    next tick; a second click in the same tick is refused without using up the cooldown; cancelling
    (the quit path) before the tick stops the list; delay-only and empty lists leave nothing
    pending; a throwing step stops the list and clears pending; `DELAY` resumes.
  - `CLOSE` decisions: same session, a menu the player opened themselves, another plugin's
    screen, `[MENU b, PLAYER <other screen>, CLOSE]` inside the swap tick, `[MENU b, CLOSE]`,
    a delayed `MENU` after the player closed the menu, `[CLOSE, PLAYER <other screen>]`, no menu
    open, and `[BACK, CLOSE]`.
  - The gate: create, delete, update and changeLayout are refused with the reload reason while
    a reload runs, and the command edge (`delete`, `joinmenu`) gives the same words. The flag comes
    down after a successful reload, a refusal over unwritten changes, the discard form, a load of
    invalid YAML, and an exception thrown inside the reload chain.
  - A refused shrink names its slots, for example "in slots 13, 20 and 26" or "in slot 8".
  - Tagged `give` copies open their menu only while it has a bound item, and never fall through
    to another menu's match mode.
  - The delay cap: an over-cap change is refused with the total and the limit in seconds. Changes
    to other slots, other keys or the title of a menu that holds an over-cap list are accepted, and
    a hand-written over-cap list still loads clamped with a warning.
- From the console: the new `unset` reply, and `help command give`, `delete` and `unset`.

## Known broken / open
- **Not verified by eye.** No game client has connected yet. Everything below needs a player:
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
- **Rule 9 is not yet met by `PlayerInteractListener`.** It opens a bound item's menu inside
  `PlayerInteractEvent`; rewritten rule 9 says to schedule that. Stage 7.45 fixes it.
- **`BACK` with no history closes whatever screen is open.** In `[DELAY 60, MENU b, PLAYER warps,
  BACK]`, after the player closed the menu during the delay, `BACK` closes the warps screen, because
  the swap marker keeps the new session valid. Found by reading the code at the 7.4 review; not
  observed. Stage 7.45 fixes it.
- `/mymenu update` replies that this build cannot check for updates (#90). Finish it at stage
  10 or remove the command.
- Nothing opens a menu on join yet; stage 7.5 builds it.
- No automated tests yet (SPEC §15.4).
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
- `none` is reserved by the command layer, not by the model (#93). A hand-written menu named
  `none` works in every way except being chosen by `/mymenu joinmenu`.

## Needs the user's input
- Nothing from stage 7.4. The stage review (2026-10-02) confirmed the `CLOSE` holder check (#95)
  and the wording of #89's remaining reason, and moved the `PlayerInteractListener` fix to stage
  7.45.
