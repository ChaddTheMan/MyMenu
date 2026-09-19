# NOTES — session state (overwritten each session)

## Current stage
Stage 6 (`action/`, `service/CooldownStore`, the `ActionCodec.readList` widening in `storage/`,
wiring in `MyMenu`) is **written and verified but not committed**. It waits for the user's review.
Stages 4 and 5 are committed (27fe485, 19f78b1; the `VersionedMenu` move and document updates in
43d584f). Stage 7 has not started. Full report: `STAGE6-REPORT.md`.

## Verified working (2026-09-19, Paper 26.2-124, Java 25)
- `./gradlew clean build` is clean. `runServer` starts, enables MyMenu, loads the four menus in
  `run/`'s `menus.yml` (unchanged from stage 5), and disables cleanly on `stop`.
- A temporary probe (now deleted, run before the review corrections below) passed 123 checks
  with proxy `Player`s backed by a real `PermissibleBase`, synthetic close and quit events through the real `PluginManager`, and a
  stage-6 fixture holding every action type. Covered: all eight types parse from the file and
  round-trip through `YamlMenuStorage.saveAll` then `loadAll` with no `&id` aliases; lowercase
  `type` accepted; a list over the delay cap is clamped at load with one warning and reloads
  without one; the prefix shorthand, the sanitiser, and every parser rejection; the full list on
  one click (message parsed, player command, console command through a real registered command,
  elevated command with the node present during dispatch and gone after, click sound, delay
  suspends and resumes, then `MENU`, `BACK`, `CLOSE` next tick); a click during a pending
  sequence ignored; an elevated command that throws still revokes and stops the list; a sequence
  continues through a `DEATH` close and is cancelled by quit; `DELAY 0` yields one tick; `MENU`
  with nothing open opens fresh; `MENU` to a missing menu messages and stays put; depth grows to
  10 then two more hops are refused with warnings; ten `BACK`s pop to 0 and one more closes;
  cooldowns per player, per menu, per slot, expire after their seconds, skipped by
  `MyMenu.bypass.cooldown`, and **felt by an op** (the node is declared `FALSE` in
  `build.gradle.kts`); `setOp` never called.
- API verified with `javap` against the 26.2 jar: `Player#performCommand`, `addAttachment(Plugin)`,
  `PermissionAttachment#remove`, `Bukkit.dispatchCommand`, `Audience#playSound(Sound)`,
  `Sound.sound(Key, Source, float, float)`, `BukkitScheduler#runTaskLater`, `Server#getCommandMap`.
- **Changed after the probe, verified by build and a clean start only:** an empty action list
  plays no sound and starts no cooldown; a trailing `DELAY` ends the sequence instead of holding
  the player pending; `setMaxDepth` clamps to 1-32 with a warning. No probe has run against the
  corrected code.
- **Not verified by eye.** No one has opened a menu in a real client. Real `performCommand`
  behaviour (the proxy recorded it), the sound actually reaching a client, and the
  `PlayerQuitEvent` firing order between Paper's own cleanup and our `MONITOR` handler are
  inferred from the API contract.

## Known broken / open
- Nothing known broken. No automated tests yet (SPEC §15.4).
- Hard-wired until stage 7 reads them from `config.yml`: `actions.maxTotalDelaySeconds` (30,
  `ActionParser.DEFAULT_MAX_TOTAL_DELAY_SECONDS`, setter ready) and `navigation.maxDepth` (10,
  `ActionExecutor.DEFAULT_MAX_DEPTH`; the setter clamps to 1-32, the retained
  `NavigationStack.MAX_DEPTH` ceiling, #83).
- ARCHITECTURE §9 still places sequence cancellation in `PlayerQuitListener`; it is on
  `ActionExecutor` (#84). Not edited.
- `Bukkit.getCurrentTick()` is not a usable clock from inside tasks (DECISIONS #77).
- **Stage 7:** no permission is checked when opening a menu; bound-item opens share
  `SessionManager.open`. Add the gate in the command layer only. `give` must stamp
  `PlayerInteractListener.boundItemKey()` (`mymenu:bound_item`, STRING = menu name). Reload:
  flush first; refuse on unwritten changes; re-apply `setBackupPolicy`/`setDebounce`, and now
  `ActionParser.setMaxTotalDelaySeconds`/`ActionExecutor.setMaxDepth`; add the discard form (#71).
- **Stage 8:** action input goes through `ActionParser.parseList(lines, where)` (shorthand plus
  the clamp) or `ActionParser.read` for explicit entries; never build `Action`s from chat any
  other way. The `!` shorthand yields an empty node list; the editor attaches nodes (#85).
  `EditSession.PendingPrompt` carries only its timeout task; add what the prompt is for. The
  property editor must refuse an opaque item that does not deserialise (#74).
- **Stage 9:** replace `ActionTextResolver.parsingOnly(items::parse)` in `MyMenu` with the
  `TextService` implementation; `command(raw, viewer)` returns a substituted string and the
  executor sanitises it unconditionally, so substitute only, never parse. Also `TokenReplacer`
  per viewer (#72, #75).
- **Stage 10:** hard-coded English in `ActionExecutor` (cooldown wait, missing menu),
  `MenuRenderer`, `InventoryClickListener`, `DebouncedMenuWriter`, `PlayerInteractListener`.
- **Stage 11:** readable → `ItemStack` for MySQL is `ItemBuilder.build(descriptive,
  TokenReplacer.NONE)`. `ActionCodec.readList` must be used there too, not `read` in a loop.

## Needs the user's input
- Review and commit stage 6 (review corrections applied 2026-09-19: empty lists silent, trailing
  delay ends the sequence, `maxDepth` clamped, changelog line added).
