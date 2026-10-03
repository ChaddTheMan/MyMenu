# Stage 7.4 report: fixes from the stage 7.25 audit

Date: 2026-10-01. Paper API `26.2.build.124-stable`; test server Paper 26.2-129; Java 25.
Not committed.

## 1. Summary

All six code parts (A–F) are built. All the §8 repository text fixes are made, and the new
`DECISIONS.md` entries are #94–#98.

- `./gradlew clean build` is clean.
- The server enables cleanly and loads the menus in `run/`.
- A temporary probe ran 95 checks, and all 95 passed. It is now deleted.

Four things go beyond or against the prompt's text, each explained in §6:

- `CLOSE` also checks that a view-mode menu is open. The prompt's claim that `SessionManager.view`
  "already returns empty unless a MyMenu view-mode inventory is open" does not hold during the tick
  a menu is opened.
- The delay-cap refusal carries the slot and click key as well as the total and the limit.
- `MutationResult.Reason` became a sealed type.
- `ActionExecutor` has a private nested class, `Sequence`.

One new finding needs a decision. Under the rewritten rule 9, `PlayerInteractListener` still opens
a bound item's menu inside `PlayerInteractEvent` (§7). It was left unchanged, as §5 of the prompt
requires.

## 2. What was built

### Part A: action lists start on the next tick (`action/ActionExecutor.java`)
- `dispatch` keeps the click-time steps where #86 put them: the empty-list return, the pending
  check, the cooldown check and mark, and the click sound. It then:
  - returns if the list has nothing executable (only `DELAY`s), so nothing is left pending;
  - otherwise creates a `Sequence` and passes it to `schedule(sequence, NEXT_TICK)`.
    `schedule` calls `runTaskLater` and puts the task in the existing `pending` map in the same
    statement sequence, so the player is pending from the moment the list is scheduled.
- **Structural guarantee (point A.4).** The list walk is `Sequence.run()`, a private nested
  `Runnable`. A `Sequence` is created in exactly one place, `dispatch`, and that expression is the
  argument to `schedule`. Only the scheduler and the sequence itself (re-scheduling after a
  `DELAY`) ever hold a reference to one, so no caller has a sequence it could run inline. Nothing
  else in the class can reach `execute`, because `execute` needs a `Sequence`.
- Inside the walk, `MENU`, `BACK` and `CLOSE` act directly. `nextTick` is gone.
- Unchanged:
  - A throwing step stops the list and is logged.
  - `DELAY` reschedules the same `Sequence` with its position.
  - `pending` is removed first thing when the task fires, so it is never left set.
  - Only `onQuit` → `cancel` cancels a sequence.
  - The list is the one captured at the click (`List.copyOf` of an already immutable model list).
- Small addition: the walk now checks `isOnline()` before each step, not only when it resumes.
  This covers a quit caused by an earlier step, such as a kick command. Stage 6 had the check only
  in `resume` and in `nextTick`.
- The class-head comment was rewritten. It covers why the whole list waits (rule 9), why it is
  still not a loop (`DELAY`), pending from the click, and `CLOSE`'s scope.
- The `TODO(stage 7)` on `setMaxDepth` is removed.

### Part B: `CLOSE` closes only the list's own menu
- `Sequence.session` is captured in `dispatch` with `sessions.view(player).orElse(null)`.
- `close(sequence)` closes only when all three hold:
  1. `SessionManager.openHolder(player)` is a `MenuHolder` in `VIEW` mode;
  2. `sessions.view(player)` is non-null;
  3. that session `==` `sequence.session`.
- When nothing matches, `CLOSE` does nothing: no message and no log line.
- When `MENU` (`navigate`) or `BACK` returns `OPENED`, `sequence.followOpenMenu()` sets
  `sequence.session = sessions.view(player)`. This is the filled-in detail. `BACK`'s and `MENU`'s
  own behaviour is otherwise unchanged.

### Part C: one refusal gate (`service/MenuService.java`, `command/*`, `MyMenu.java`)
- `MenuService.gate()` is now public and returns `Optional<Reason>`. It checks, in this order:
  - `RELOAD_RUNNING`;
  - `NOT_LOADED`;
  - `STORAGE_DEGRADED`.
  This is the order the command edge used. Every mutator (`create`, `delete`, `update`,
  `changeLayout`) still asks the gate first, before computing anything.
- The reload flag moved into `MenuService`:
  - `beginReload()` returns `false` if a reload is already running;
  - `endReload()` lowers it.
  `ReloadCommand.running` and `isRunning()` are gone.
- `ReloadCommand.execute` raises the flag with `beginReload()`. It lowers it first thing in the
  final `whenCompleteAsync` stage, and in a `catch` around building the chain. Question 1 (§5)
  goes through each exit path.
- `CommandTree.refuseWhileLocked` asks `menus.gate()` and throws `fail(Replies.refusal(reason, ""))`.
  The tree no longer takes a `BooleanSupplier`.
- `Replies.refusal` is the one source of wording, and the reload text is unchanged.
- The class-head comments of `MenuService` and `ReloadCommand` explain the gate and the flag.

### Part D: a refused shrink names the slots
- `changeLayout` returns `Refused(new ItemsOutsideLayout(menu.slotsOutside(type, rows)))`. The
  record sorts and copies the slots and rejects an empty list.
- `Replies.refusal` says "That would leave items outside menu 'x', in slots 13, 20 and 26. Move or
  remove them first." With one slot it says "in slot 8". Slots are counted from 0.
- Shape of the result type (#98):
  - `MutationResult` is still the sealed `Applied | Refused`.
  - `Reason` is now a sealed interface.
  - `Reason.Plain` is the enum of plain reasons, which gains `RELOAD_RUNNING`.
  - `ItemsOutsideLayout` and `DelayOverCap` are records.
  - `CreateCommand`, `SetCommand` and `DeleteCommand` compile unchanged.

### Part E: `unset` revokes `give` copies (`listener/PlayerInteractListener.java`)
- `match`: a tagged item gives
  `registry.find(tagged).filter(menu -> menu.boundItem() != null).map(Menu::name).orElse(null)`.
  It never falls through to the match-mode loop.
- Javadoc, rewritten:
  - the tag says *which* menu, and the binding says *whether* it may open;
  - an unbound or missing menu opens nothing;
  - rebinding re-enables old copies, which is expected; the rest is an open question (O23).
- `unset` reply: "Copies handed out with /mymenu give no longer open it."
- `give` help: "Give a player a copy of a menu's bound item; it opens the menu while the menu has a
  bound item."

### Part F: the total-delay cap at mutation time
- After computing the new menu, `MenuService.update` calls `overDelayCap(current, next)`, before
  committing. The check:
  - walks the new menu's slots in ascending order, and each slot's click keys in enum order;
  - skips any list `equals` to the list at the same slot and key in the current menu;
  - sums the `DELAY` ticks of each list it does not skip;
  - refuses with the first total over `cap × 20`, as `DelayOverCap(slot, key, totalTicks,
    limitSeconds)`. The comparison is the same as `ActionParser.clampTotalDelay`'s: a total equal
    to the cap is allowed.
- `create` builds an empty menu, so it has nothing to judge. `changeLayout` goes through `update`.
- Reply: "The LEFT actions on slot 3 of menu 'x' would pause for 35 seconds in total; the limit is
  30 seconds." Seconds are exact decimals, for example 32.5.
- `MenuService(persistence, maxTotalDelaySeconds)` gets the default from `MyMenu`.
  `MyMenu.applyConfig` now also calls `menuService.setMaxTotalDelaySeconds`, so the cap follows
  every reload.
- No change to the model records, to the parser's clamping, or to `ActionParser`'s public methods.

### §8: repository text fixes
Each item was checked against the code after parts A–F.

| Item | Done |
|---|---|
| `ActionExecutor` `TODO(stage 7)` | removed |
| `ActionParser` `TODO(stage 7)` | removed |
| `ActionParser` "The same clamp covers shorthand input" | corrected: `parseList` applies the clamp, but shorthand has no `DELAY`. Added that `read` applies no cap and that `MenuService` refuses over-cap lists |
| `DeleteCommand` javadoc | "What is not cancelled" replaced by a pointer to #92; the click-event reason replaced by the #89 note (deferral kept) |
| `OpenCommand` javadoc | same #89 note |
| `CommandSpec` | class doc and `@param mutating` state all three conditions |
| `HelpCommand` line | "Refused while menus are loading or reloading, or while menu storage is degraded." |
| `InventoryCloseListener` javadoc | the cursor can never hold anything: every click and drag is cancelled, nothing is placed from the cursor, and the editor works slot first |
| `build.gradle.kts` comment | 26.3 stable on 2026-09-20; the project stays on 26.2 until stage 8 is finished. Versions not changed |
| `CHANGELOG.md` | drag line removed; `none` line corrected (only `/mymenu create` refuses it); `joinmenu` line untouched; three `Fixed` lines added (E, B, A) |
| `DECISIONS.md` notes | dated 2026-10-01 on #32, #33, #37, #45, #52, #53, #58, #65, #66, #69, #74, #80, #88, #89, **and #38** (§6) |
| `DECISIONS.md` entries | #94 (A, with #89's deferral kept), #95 (B, with the filled-in detail and the holder check), #96 (C), #97 (F), #98 (the result shape) |
| `ARCHITECTURE.md` §1 | `Map<ClickKey, List<Action>>` |
| `ARCHITECTURE.md` §2 | the twelve classes F21 lists, added |
| `ARCHITECTURE.md` §6 | the "`MENU`, `BACK`, `CLOSE` are scheduled" paragraph replaced by the event-handler rule (#94) |
| `ARCHITECTURE.md` §7 | interface gains `retryUnwritten` and `discardUnwritten` |
| `ARCHITECTURE.md` §7.1 | the single gate and the cap check |
| `ARCHITECTURE.md` §8 | a new short paragraph on edge refusal through the gate (§8 had no refusal passage to edit) |
| `ARCHITECTURE.md` §9 | the cursor removed from the table row; the event-handler rule added, plus one sentence naming the `PlayerInteractListener` exception |
| `NOTES.md` | rewritten |

## 3. What was verified, and how

1. **Build.** `./gradlew clean build`: `BUILD SUCCESSFUL`, with no compiler warnings. The only
   other output is plugin-yml's known "No mavenCentralProxy configured" notice (#60).
2. **Server.** `./gradlew runServer` with commands piped to the console, twice:
   - once with the probe;
   - once after the probe was deleted, from a clean build.
   The second run enabled MyMenu with no warnings of its own, loaded 4 menus from `run/`, answered
   `mymenu list`, `mymenu unset welcome` and `mymenu reload`, and stopped cleanly with
   "Menu storage stopped".
3. **Probe.** A temporary `command/Stage74Probe`, wired into `onEnable`, ran 95 checks across
   about 300 ticks: **95 passed, 0 failed**. It sat in `command/` so it could call the
   package-private `Replies.refusal`. It is deleted, and its wiring is removed (grep confirms no
   trace).

   The probe's stand-in `Player` was a `java.lang.reflect.Proxy`. It records messages, commands,
   `openInventory` and `closeInventory`, and models the open top inventory. Where a real client
   would fire `InventoryCloseEvent`, the probe called `SessionManager.closed` itself, with
   `OPEN_NEW` or `PLAYER`.

   - **A (17 checks).**
     - After `dispatch`, the player is pending and no action has run.
     - On the next tick the list ran and the player is no longer pending.
     - A second `dispatch` in the same tick, on a slot with a 5 s cooldown, was refused. The
       following tick the same slot was accepted, which proves the refused click did not take the
       cooldown. A third click then got the cooldown message.
     - `cancel` before the tick: the list never ran.
     - `[DELAY 5, DELAY 3]` and `[]` leave nothing pending.
     - `[MESSAGE, PLAYER <throws>, MESSAGE]`: the first message ran, the last did not, pending was
       cleared, and the warning with its stack trace was logged.
     - `[MESSAGE, DELAY 3, MESSAGE]`: pending in between, then finished.
   - **B (10 checks plus 8 setup opens).** Closing is observable on the stand-in, so these checks
     cover both the decision and the `closeInventory` call. What a real client sees is not
     covered.

     | Case | Result |
     |---|---|
     | Same session, `[CLOSE]` | closed |
     | Player opened `probe-b` themselves during `[DELAY 2, CLOSE]` | nothing closed |
     | Another plugin's screen during `[DELAY 2, CLOSE]` | nothing closed |
     | `[MENU b, PLAYER <opens other screen>, CLOSE]` | other screen kept; `sessions.view()` **was present** at that moment, which reproduces the swap-tick window |
     | `[MENU b, CLOSE]` | b closed |
     | `[DELAY 2, MENU b, CLOSE]` after the player closed the menu during the delay | b opened (new session), then closed |
     | `[CLOSE, PLAYER <other screen>]` | menu closed, other screen stays |
     | No menu open, `[CLOSE]` | nothing |
     | `[BACK, CLOSE]` after `[MENU b]` | back to a, then closed |

   - **C (31 checks).**
     - With `beginReload()` up: a second `beginReload` is refused, and `create`, `delete`,
       `update` and `changeLayout` each return `RELOAD_RUNNING`.
     - `Replies.refusal(RELOAD_RUNNING)` is exactly "A reload is running; try again when it has
       finished."
     - Commands dispatched through `Server.createCommandSender(feedback)` (verified with `javap`):
       `mymenu delete probe-b` and `mymenu joinmenu none` both replied with exactly those words,
       `mymenu reload` replied "A reload is already running.", and probe-b still existed.
     - Then each reload exit path, each followed by a gate check:
       - success;
       - refusal over unwritten changes: a non-empty directory at `menus.yml.tmp`, an edit, then a
         flush that failed;
       - the discard form with the obstruction still present;
       - obstruction removed, then a create and a successful write;
       - a load of invalid YAML (degraded; the flag is down);
       - the file restored and reloaded;
       - a sender that throws on "Reloaded…", so `install` throws: the flag is down, "Reload
         failed" is logged and reported, and an edit then succeeds.
   - **D (6 checks).**
     - Items at 0, 13, 20 and 26 in a 3-row chest.
     - Chest with 1 row → `[13, 20, 26]`, and the reply names "slots 13, 20 and 26".
     - Hopper → `[13, 20, 26]`.
     - Chest with 2 rows → "slots 20 and 26".
     - A single slot → "in slot 8."
     - A layout that fits → applied.
   - **E (6 checks).** A `PlayerInteractEvent` was constructed and passed straight to
     `onInteract`.
     - A tagged copy opens `probe-e` while it is bound.
     - After `unset`, the tagged copy opens nothing, although `probe-f` binds the same material
       by `TYPE`.
     - An untagged compass still matches `probe-f`.
     - After rebinding, the old copy opens again (documented).
     - A tag naming a missing menu opens nothing.
   - **F (11 checks).**
     - A new slot with `[DELAY 700, …]` is refused: `DelayOverCap(3, LEFT, 700, 30)`, reply "35
       seconds … limit is 30 seconds".
     - Lengthening 100 → 100+550 is refused ("32.5 seconds").
     - Exactly 600 ticks is accepted.
     - With the cap lowered to 10, these are accepted: a new other slot, a title change, and a new
       `RIGHT` list on the over-cap slot. Changing the over-cap `LEFT` list itself is refused with
       limit 10.
     - A hand-written `probecap` menu, added to `run/` for the run, loaded clamped to 600 ticks,
       with the warning "menu 'probecap' slot 0 LEFT: the delays add up to 700 ticks, over the
       30-second cap; clamped to 600 ticks". A title change to that menu was accepted.
4. **Console.** Replies seen:
   - `mymenu unset probe-unset` → "Menu 'probe-unset' no longer has a bound item. Copies handed out
     with /mymenu give no longer open it."
   - `mymenu help command give` → `/mymenu give <menu> [<player>]`, "Give a player a copy of a
     menu's bound item; it opens the menu while the menu has a bound item.", and the permission.
   - `mymenu help command delete` → usage, description, permission, and "Refused while menus are
     loading or reloading, or while menu storage is degraded."
   - The same output for `unset`.
5. **Grep** for each stale text in §8 across `src`, `build.gradle.kts`, `CHANGELOG.md`,
   `ARCHITECTURE.md` and `NOTES.md`. These searches return 0 matches:
   - `TODO(stage 7)`, "same clamp covers shorthand", "What is not cancelled", "may be running
     inside a menu", "and actions run inside";
   - "Refused while menu storage is degraded", "commands while storage is degraded";
   - "returns that to them", "can only hold what the player brought";
   - "alpha-only", "dragging a real item", "Menus cannot be named", "return cursor items";
   - `Map<ClickType`, "always opens it", "still open it while", "the whole answer";
   - `reloading.getAsBoolean`, `ITEMS_OUTSIDE_LAYOUT`, `isRunning`.
6. **Test data.** `run/plugins/MyMenu` was copied to the scratchpad before the probe run. After
   each run it was restored, and `diff -r` confirmed it identical: once after the probe, and once
   after the second run, whose `unset welcome` had changed it.

### Only inferred, not observed
- The behaviour with a real client (all of NOTES.md's "needs a client" list).
- **The "next tick" timing comes from the Bukkit scheduler, not a real click.** The probe called
  `dispatch` from a scheduled task, not from inside a real `InventoryClickEvent`. What it observed
  is that `dispatch` itself runs nothing, and that the list ran by the next probe step.
- That sequences continue through death and world change. That code is unchanged and was not
  exercised.
- That building the reload chain cannot throw in practice. The `catch` exists, but nothing
  provoked it.
- **Q2's client-side packet behaviour** (§5).

## 4. Hard-rule check for the changed code
- **Rule 1.** No static mutable state was added. The new constants are `static final` primitives.
- **Rule 2.** No new I/O.
- **Rule 7.** `ActionExecutor` holds no new per-event state. A `Sequence` is held only by its
  scheduled task. The `pending` map is the existing owned state (#84).
- **Rule 9.** `dispatch` no longer runs actions (A). The open exception is
  `PlayerInteractListener` (§7).
- **Rule 10.** All three checks now sit in the one gate. Every mutator asks the gate first, and
  the edge asks the same gate.

## 5. Answers to the two questions

### Q1. Where does the reload flag live, and how is it cleared on every exit path?
**Owner: `MenuService`** (`reloading`, `beginReload()`, `endReload()`). Why:

- The gate then depends on nothing above it.
- The stage 8 editor needs only the service.
- Reading the flag from `ReloadCommand` would need a supplier wired in after construction:
  `MenuService` is built first and is a constructor argument of `ReloadCommand`, so the two would
  be circular.

**The chain:**

```
beginReload → supplyAsync(config) → thenComposeAsync(reload, main) → whenCompleteAsync(endReload first, main)
```

`whenComplete` runs whether the stage before it completed normally or exceptionally. Every failure
in an earlier stage reaches it as an exceptional completion. The paths:

| Exit path | How it reaches `endReload` | Exercised at runtime |
|---|---|---|
| Success | `install` completes normally | yes |
| Refused: unwritten changes | `loadAll` throws `UnwrittenChangesException` → exceptional | yes (non-empty dir at the temp path) |
| Discard form | discard, then load, then `install` → normal | yes |
| Failed load: unreadable or invalid file | storage turns it into a degraded load (`YamlMenuStorage.readFile`) → normal | yes (invalid YAML) |
| Failed load: `loadAll` throws for any other reason | exceptional, same as the unwritten case | mechanism exercised by the unwritten case |
| Exception in `PluginConfig.load` or in `reload()` (main thread) | that stage completes exceptionally | not provoked separately; same mechanism |
| Exception in `install` | exceptional | yes (throwing sender) |
| `refused()` throws inside the completion stage | the flag is already down: `endReload` is its first statement | inferred |
| Building the chain throws synchronously | `catch (RuntimeException)` → `endReload`, rethrow | inferred (not provokable) |

Flush and retry failures are deliberately swallowed by `.exceptionally(...)` (unchanged), so they
continue the chain rather than end it.

**Residual case: a reload in flight when the plugin is disabled.** `MyMenu`'s `mainThread`
executor drops tasks once `!isEnabled()`, so the completion stage never runs and the flag stays up.
The flag belongs to the instance being disabled, and `/bukkit:reload` builds a new `MenuService`,
so nobody is locked out.

**Point C.2: the reload's own steps are not refused by the gate.** Checked by reading, not
assumed:

- `storage/` never calls `MenuService`. The grep shows only `DebouncedMenuWriter` implementing
  `service.MenuPersistence`. So the flush and `retryUnwritten` cannot reach the gate.
- `replaceAll` does not call `gate()`, and its javadoc now says why.

At runtime, every successful reload in the probe ran `replaceAll` with the flag raised and
installed the menus. For example, "Reloaded config.yml and 10 menu(s)", with the probe's menus
present afterwards.

### Q2. Does part A change what a double-click does?
**Yes, sometimes.** Established from Paper's code:

- **Paper delivers `DOUBLE_CLICK` even when the cursor is empty.** Checked with `javap -c` on
  `ServerGamePacketListenerImpl.handleContainerClick` in `run/versions/26.2/paper-26.2.jar`
  (26.2-129):
  - The packet's `ContainerInput.PICKUP_ALL` (switch case 7, mapped through
    `ServerGamePacketListenerImpl$1`) sets `ClickType.DOUBLE_CLICK` and `InventoryAction.NOTHING`.
  - Only if the server-side carried stack is non-empty does it work out `COLLECT_TO_CURSOR`.
  - `InventoryClickEvent` is then built and `callEvent` invoked for every input except
    `QUICK_CRAFT`, with no test on the action.
  - In a MyMenu screen the server's carried stack is always empty, so the event fires as
    `DOUBLE_CLICK` / `NOTHING`.
- **Each click packet is handled on the main thread, in arrival order.** The handler begins with
  `PacketUtils.ensureRunningOnSameThread`.
- **Inferred: two packets can land in the same tick.** The scheduler heartbeat runs once per tick,
  so two click packets processed before the same heartbeat count as the same tick for part A. I did
  not measure packet-to-tick timing.

**From memory of the vanilla client, not verified** (no client jar here; flag this):

- A double-click sends `LEFT` on the first press and `LEFT` again on the second press, then
  `DOUBLE_CLICK` (`PICKUP_ALL`) on the second **release**, if the second click comes within about
  250 ms on the same slot.
- So a double-click is `LEFT, LEFT, DOUBLE_CLICK`, not one `LEFT` and one `DOUBLE_CLICK` as SPEC
  §8.4's sentence suggests.
- Because the client predicts the first pickup, a second press made before the server's resync
  arrives may start a drag instead of sending `LEFT`.

**Effect of part A.** The second `LEFT` and the `DOUBLE_CLICK` are separated by how long the
button is held on the second click, which may be less than one tick.

- If both arrive before the same heartbeat, the `DOUBLE_CLICK` is now refused as pending.
- Before 7.4 the `LEFT` list ran inside its own event, so a same-tick `DOUBLE_CLICK` ran whenever
  the `LEFT` list had no `DELAY`.
- If they fall in different ticks, both run as before.
- Unchanged: an item cooldown (shared by all keys on the slot) refuses the `DOUBLE_CLICK` either
  way.

`DOUBLE_CLICK` is **not** special-cased.

**For the client session:** give a button `LEFT: [MESSAGE L]` and `DOUBLE_CLICK: [MESSAGE D]`.
Double-click it about ten times with quick and slow releases, and count L and D.

## 6. Divergences from the prompt, and why

1. **`CLOSE` also requires an open view-mode `MenuHolder` (part B, point 2).** The prompt says
   `view` "already returns empty unless a MyMenu view-mode inventory is open". That is not true in
   the tick a menu is opened: `ViewSession.markSwap` keeps the session valid until the next tick
   (#77), whatever is open by then. In `[MENU b, PLAYER warps, CLOSE]`, identity alone matches, and
   `CLOSE` would close the warps screen, against the ruling. The probe reproduced the window (B4).
   The extra check makes the ruling hold. Recorded in #95, and listed in NOTES for your
   confirmation.
2. **`DelayOverCap` carries the slot and click key** as well as the total and the limit. A change
   can touch several lists, and without them the reply could not say which one is over. #97 says
   this is my addition.
3. **`MutationResult.Reason` became a sealed interface** with `Reason.Plain` (enum) and two
   records. The prompt said "gains a value"; it does gain `RELOAD_RUNNING`, in `Plain`. The
   `ITEMS_OUTSIDE_LAYOUT` constant became the `ItemsOutsideLayout` record. Callers outside my
   allowlist (`CreateCommand`, `SetCommand`) compile unchanged. Recorded in #98.
4. **A new nested class, `ActionExecutor.Sequence`.** The prompt allows no new classes "unless a
   part cannot be built without one". Point A.4 asks for the scheduler-only reach to be
   structural, and a private nested `Runnable` that is only ever handed to the scheduler is how I
   made it so. It is not a new file.
5. **The per-step `isOnline()` check** in the walk (§2, Part A). Before, the check ran only on
   resume and in `nextTick`.
6. **A dated note on #38,** which is not in §8's list. #94 supersedes it, and CLAUDE.md requires a
   note on any entry that a later one supersedes.
7. **Entry #98** in addition to the expected A, B, C and F entries. No entry for D or E: they bring
   code into line with SPEC and decide nothing it does not cover.
8. **`MenuService`'s constructor** gained the cap argument, mirroring `ActionParser` and
   `ActionExecutor`. `applyConfig` sets the configured value before the first load.
9. **`ActionParser` comment.** As well as correcting the shorthand sentence, I added that `read`
   applies no cap and that `MenuService` refuses over-cap lists (F18).
10. **`OpenCommand` and `DeleteCommand` comments** keep #89's deferral and give as the remaining
    reason that "a command cannot tell what it was dispatched from". That is #89's own
    reasoning, with the menu-click case removed. #89's DECISIONS note records only that the
    deferral is kept, as ruled. Listed in NOTES for confirmation.
11. **CHANGELOG** has a third `Fixed` line for part A, besides E and B: commands from a click start
    just after it. This is user-visible timing.
12. **The `Dispatcher` interface** was not changed.

## 7. Findings for planning

- **`PlayerInteractListener` and rule 9.** `onInteract` calls `sessions.open(...)` inside
  `PlayerInteractEvent`. Under the rewritten rule 9, opening a screen from a handler is scheduled
  for the next tick. The prompt allowed this listener to change only as part E said, so it is
  unchanged. It is recorded in ARCHITECTURE §9 and NOTES. Stage 7.5, which owns this listener, is
  the natural place to fix it.
- **ARCHITECTURE §6** still has the bullet "Total delay is validated at parse time and clamped
  with a warning, not at run time." It is accurate about loading. The mutation-time refusal is
  described in §7.1 (#97). I left the bullet alone because the prompt limited §6 to the
  event-handler passage.
- **SPEC rev 2** still carries the replaced texts: the §9.3 last paragraph, the §9 `CLOSE` row,
  and §8.4's "the client sends a `LEFT` click before a `DOUBLE_CLICK`" (Q2 suggests two `LEFT`s).
  Not edited, as instructed.
- **Nearby deferred features:** none looked cheap enough to note.

## 8. Files changed

- **Code:**
  - `MyMenu.java`
  - `action/ActionExecutor.java`, `action/ActionParser.java`
  - `command/CommandSpec.java`, `CommandTree.java`, `DeleteCommand.java`, `GiveCommand.java`,
    `HelpCommand.java`, `OpenCommand.java`, `ReloadCommand.java`, `Replies.java`,
    `UnsetCommand.java`
  - `listener/InventoryCloseListener.java`, `listener/PlayerInteractListener.java`
  - `service/MenuService.java`, `service/MutationResult.java`
  - `build.gradle.kts` (comment only)
- **Documents:** `ARCHITECTURE.md`, `CHANGELOG.md`, `DECISIONS.md`, `NOTES.md`, and this report.
- `CLAUDE.md` shows as modified in `git status`. It was modified before this stage started, and I
  did not touch it.
- The probe class was created and deleted. `run/plugins/MyMenu` was restored identical.
