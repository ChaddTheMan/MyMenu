# NOTES — session state (overwritten each session)

## Current stage
Stage 7 (`command/`, the two new config keys plus `joinMenu` and `storage.type` in
`PluginConfig`, the reload discard form in `storage/`, wiring in `MyMenu`) is **written,
verified and reviewed, but not committed**. Stage 6 is committed (ffa653f). Full report:
`STAGE7-REPORT.md`, whose addendum records what the review changed. Stage 8 has not started.

## Verified working (2026-09-19, Paper 26.2-125, Java 25)
- `./gradlew clean build` is clean. `runServer` starts, enables MyMenu with no warnings of its
  own, loads the menus in `run/`, answers commands, and stops cleanly.
- Driven from the server console across six server runs: `help`, `help admin`,
  `help command <name>`, `list`, `info`, `changelog`, `changelog <version>`, `update`, `save`,
  `create`, `delete`, `joinmenu <menu>`, `joinmenu none`, `reload`, `reload discard-unsaved`,
  and the player-only refusals for `edit`, `set`, `name`, `open`, `give`. Bad arguments
  (invalid name, reserved name `none` in either case, unknown menu, unknown player, unknown
  command) all answer politely.
- `create` works from the console: the menu is made with no author recorded, and the reply
  leaves out the "open it for editing" hint a player gets.
- `joinmenu` rewrites only its own line in `config.yml`; the rest of the file, comments
  included, is untouched. A reload picks up new values, clamps `navigation.maxDepth` 99 to 32
  with a warning, and reports a changed `storage.type` instead of switching.
- `/minecraft:reload` re-fires the `COMMANDS` handler; commands and help still work afterwards.
- Degraded by a bad entry at load: `list`, `info` and `changelog` keep working while `delete`,
  `joinmenu` and editing are refused with the reason, and `save` reports instead of writing.
  Fixing the file and reloading clears it.
- Degraded by a permanent write failure (a non-empty directory where the temp file goes):
  reload retries the write, fails, refuses, and names the discard form; the discard form
  retries, discards, re-reads, lists which menus went back to their last save, and leaves
  editing working again once the fault is removed.
- An in-JVM probe (since deleted) passed 15 checks: tab completion for subcommands, menu names
  with prefix filtering, match modes, `none`, `discard-unsaved`, `help command`, and the `mm`
  alias; permission filtering of the subcommand list as nodes are granted and revoked; and the
  three stage 6 corrections at runtime — an empty action list plays no sound and starts no
  cooldown, and a trailing `DELAY` leaves nothing pending.

## Known broken / open
- **Not verified by eye.** No client has connected. Nothing below has been seen by a player:
  `open`, `edit`, `set`, `give`, `name`, the delete cascade's messages and inventory closing,
  reload's closing of open menus, and suggestions as a real player's client receives them.
- `/mymenu update` replies that this build cannot check for updates (#90). Finish it at stage
  10 or remove the command.
- Nothing opens a menu on join yet: `joinMenu` can be set and read, but SPEC §13 has no stage.
- No automated tests yet (SPEC §15.4).

## Known behaviour worth remembering
- `none` is reserved by the command layer, not by the model (#93). `/mymenu create none` is
  refused, but a hand-written `menus.yml` may define a menu called `none` and it works in every
  way — it simply cannot be chosen by `/mymenu joinmenu`. This is deliberate: making the name
  invalid in `Menu` would skip such a menu at load and degrade storage, disabling editing
  server-wide over a name.

## Stage 8
- **A tagged item must open its menu only while that menu still has a bound item.** As built,
  `give` hands out copies that keep working after `unset`, because `PlayerInteractListener`
  treats the tag as the whole answer, so an admin cannot revoke access. The tag names *which*
  menu; the binding is what says an item may open one at all. SPEC §7 is updated, and the fix
  is in `listener/`, which stage 8 may touch. `UnsetCommand`'s reply currently tells the admin
  that dispensed copies keep working — correct it in the same change.
- The editor is a mutation path outside the command tree, so it must repeat the three checks in
  `CommandTree#refuseWhileLocked` — degraded, not loaded, reload running — or a click will
  change a menu a reload is about to replace (#88).
- `EditCommand` already opens the `EDIT` view, so stage 8 starts from an open edit inventory and
  an `EditSession` whose only content is its prompt. Add what the prompt is for beside
  `EditSession.PendingPrompt`; the delete cascade already clears prompts and messages the admin,
  and reload ends them.
- Action input goes through `ActionParser.parseList(lines, where)` or `ActionParser.read`, never
  anywhere else; the `!` shorthand yields an empty node list and the editor attaches nodes (#85).
- The property editor must refuse an opaque item that does not deserialise (#74).

## Needs the user's input
- Review and commit stage 7. It is not committed, as asked.
