# Stage 7.45 report: the bound item's menu opens on the next tick, and `BACK` with no history

Written 2026-10-03. Prompt: `STAGE7.45-PROMPT.md`. It was read from the repository root at the user's
direction, because the path named in the session prompt did not exist. Nothing is committed.

## 1. Summary

**Part A.** A bound item's menu now opens on the tick after the click.
- `PlayerInteractListener` still decides and cancels inside the event, unchanged, then calls the new
  `listener/BoundItemOpener`.
- The opener keeps at most one open pending per player; the first request wins.
- On the next tick it clears that entry first and looks the player up by UUID. It opens the menu only
  if the player is online and has no screen open other than their own inventory.
- Every skip is silent.
- ARCHITECTURE §9 no longer lists any handler as an exception to rule 9.

**Part B.** `SessionManager.back` no longer closes anything.
- When it finds nothing to return to, it reports `NO_HISTORY`.
- `ActionExecutor` then applies `CLOSE`'s decision through the same `close(sequence)` method.

**Verification:**
- `./gradlew clean build` is clean, and `runServer` starts, answers commands and stops cleanly.
- A temporary in-JVM probe passed **32 of 32** checks.
- A negative control broke both fixes on purpose and failed exactly the three checks that cover them.
- Q1 and Q2 are answered from the Paper 26.2-129 server jar with `javap`; client-side behaviour is
  marked as inferred.

## 2. What was built

### Part A
- **`listener/BoundItemOpener`** (new, public, final, not a `Listener`):
  - **`request(Player, String)`** returns at once if the player's UUID is already pending. Otherwise
    it calls `runTask` (delay 0; from an event handler that is the next tick) and then adds the UUID.
  - **The task** removes the UUID as its first step and looks the player up with
    `getServer().getPlayer(UUID)`, which finds only online players (verified with `javap`:
    `CraftServer.getPlayer(UUID)` reads the player list). It stops if the player is not found or
    `hasScreenOpen` is true. Otherwise it calls `sessions.open(player, menu, VIEW)` and ignores the
    result. Nothing is sent and nothing is logged in any case.
  - **`hasScreenOpen`** is one private method:
    `getOpenInventory().getTopInventory().getType() != CRAFTING`. Its javadoc carries Q1's evidence.
  - **The class header** covers what A10 asks for:
    - why the open waits a tick;
    - why another screen stops it, and why nothing is said;
    - why the guard exists;
    - why it needs no quit handler;
    - that this is the one path by which a bound item opens its menu.
- **`PlayerInteractListener`:**
  - Its constructor takes a `BoundItemOpener` instead of the `SessionManager`.
  - `onInteract` keeps every early return, the match and `setCancelled(true)`, then calls
    `opener.request(...)`. A comment there says why the second event of a double-firing click is still
    cancelled (A2).
  - The header no longer says the listener opens the menu.
- **`MyMenu`** builds the opener and passes it to the listener. That is the only change there.

### Part B
- **`SessionManager.back`:** the `player.closeInventory()` call is gone. The javadoc of `back()` and
  of `OpenResult.NO_HISTORY` now says that nothing is opened or closed, and that closing is the
  caller's decision. `OpenCommand`'s "Only back() reports this" stays true; `OpenCommand` is unchanged.
- **`ActionExecutor`:** the `BACK` step is now `back(sequence)`:
  - `OPENED` adopts the session, as before.
  - `NO_HISTORY` calls the existing `close(sequence)`, so the condition exists once.
  - `CANCELLED` does nothing, as before.

  The class header's `CLOSE` paragraph is extended for `BACK` (B4).

### Documents
- **`DECISIONS.md`:** #99 and #100 appended. No earlier entry is superseded; I searched for claims
  about `BACK` closing, `NO_HISTORY`, `PlayerInteractListener` and the rule 9 exception.
- **`ARCHITECTURE.md` §9:** the table row, the paragraph that named the exception, and the bound-item
  paragraph are rewritten. §5 and §6 are unchanged; no sentence there became false.
- **`CHANGELOG.md`:** two `Fixed` lines added in `[Unreleased]`.
- **`NOTES.md`:** overwritten. The client-session items the prompt lists are added, and the two
  "Known broken" items this stage fixes are pruned.

## 3. What was verified, and how

1. **Build.** `./gradlew clean build`: `BUILD SUCCESSFUL`, with no compiler warnings. The only other
   output is plugin-yml's known "No mavenCentralProxy configured" notice (#60).
2. **Server.** `./gradlew runServer` ran three times:
   - **with the probe;**
   - **with the negative control** (point 4);
   - **after the probe was deleted, from a clean build**, with console commands piped in.

   On the final run:
   - MyMenu enabled with no warnings of its own and logged "Loaded 4 menu(s) from menus.yml".
   - `mymenu list` answered "Menus (4): hopperdemo …, probe …, shop …, welcome …".
   - `mymenu reload` answered "Reloaded config.yml and 4 menu(s)."
   - `stop` shut the server down cleanly; see §3.6.
3. **Probe.** A temporary `listener/Stage745Probe` was wired into `onEnable`. It ran 32 checks over
   about 90 ticks: **32 passed, 0 failed**. It is deleted, and its wiring is removed; a grep of `src`
   for its name and markers returns nothing.

   **How it was built:**
   - **Stand-in `Player`.** As at 7.4, a `java.lang.reflect.Proxy`. It records `openInventory`,
     `closeInventory`, `performCommand` and messages, and models the open top inventory.
   - **The player's own inventory** is a stand-in `Inventory` whose type is `CRAFTING`. The stand-in
     view reports `CREATIVE` over it when the probe sets creative mode, matching what Q1 found in
     `CraftInventoryView.getType()`.
   - **Close events.** Where a real server fires `InventoryCloseEvent`, the probe called
     `SessionManager.closed` itself, with `OPEN_NEW`, `PLAYER` or `PLUGIN`.
   - **The UUID lookup.** The opener looks players up through `plugin.getServer().getPlayer(UUID)`,
     and the server cannot find a stand-in. So the probe built its own `BoundItemOpener` and
     `PlayerInteractListener` with a proxied `Plugin`. That proxy's `Server` answers `getPlayer(UUID)`
     with the stand-in while it is "online", and passes every other call to the real server and
     plugin, including `getScheduler`. The production class has no test hook.
   - **Timing.** Steps ran from a `ServerTickEndEvent` handler, outside any scheduler task. A task
     queued there runs at the next tick's heartbeat, as one queued from a real interact event does
     (#77). Running steps from a scheduled task would have let the opener's task run in the same pass
     and hidden the one-tick wait. Events were built as `PlayerInteractEvent(RIGHT_CLICK_BLOCK, block
     at 0,100,0, HAND)` with a compass whose `bound_item` tag names the menu. They were passed straight
     to `onInteract`.
   - **Menus.** The probe created `probe-a` to `probe-d` through `MenuService`, bound `probe-a` to
     `probe-c` with `TAG_ONLY`, and deleted them all at the end.

   **Part A (19 checks):**

   | Case | Result |
   |---|---|
   | Matching click | cancelled (`useItemInHand` = `DENY`); nothing opened inside the handler |
   | The tick after | `probe-a` opened, `getCurrentTick()` exactly 1 later |
   | Two matching events in one tick (`probe-a`, then `probe-b`) | both cancelled; nothing inside the handler; next tick exactly one open, `probe-a` |
   | Non-matching click (tag names no menu) | not cancelled (`DEFAULT`); nothing opened next tick |
   | Player offline before the tick | no open; back online, a new click opened, so the guard was cleared |
   | Menu deleted before the tick | the click was cancelled; no open, no message; a new click opened |
   | Another screen opened before the tick | no open, no close, no message; that screen still open; a new click opened after it closed |
   | Own inventory, creative view (`CREATIVE` over a `CRAFTING` top) | menu opened |
   | Own inventory, survival view (`CRAFTING`) | menu opened |
   | A later click after the guard cleared | opened again |

   **Part B (8 checks):**

   | Case | Result |
   |---|---|
   | `[BACK]` on the first menu of a session | closed it |
   | `[MENU b, BACK]` | `b` opened, then returned to `a`; nothing closed |
   | `[DELAY 60, MENU b, PLAYER <opens other screen>, BACK]`, menu closed by the player during the delay | pending through the delay; then `b` opened, the other screen replaced it, and it **stayed open** |
   | `BACK` whose history holds only a deleted menu | closed its own menu |
   | `BACK` with a chest open and no view session | nothing happened (setup and result checked) |

   **Regression (3 checks):**

   | Case | Result |
   |---|---|
   | `[CLOSE]` on its own session | closed |
   | `[PLAYER <other screen>, CLOSE]` (the swap tick) | other screen stayed |
   | `[DELAY 2, CLOSE]`, other screen opened during the delay | nothing closed |

   At the end, `trackedEntries()` was 0: the stand-in left no session behind.
4. **Negative control.** To show the probe can fail, I restored the old `player.closeInventory()` in
   `back` and made `hasScreenOpen` always false, then ran it again. Exactly the expected checks failed
   (29 passed, 3 failed):
   - both A6 checks: `[foreign, open:probe-a]`, so the menu replaced the other screen;
   - B3: `[open:probe-b, cmd:probe-screen, foreign, close:CHEST]`, so the old `back` closed the other
     plugin's screen. That reproduces the 7.4 review's finding, which until now was only read in the
     code.

   Both mutations were reverted; a grep confirms no marker is left.
5. **Test data.** `run/plugins/MyMenu` was copied to the scratchpad before the first run. It was
   restored after each run, and `diff -r` confirmed it identical each time. The probe's deletes had
   added `deleted-probe-*.yml` backups and rewritten `menus.yml`.
6. **The final run's shutdown.** "Disabling MyMenu", "Menu storage stopped" and a normal server stop,
   with `BUILD SUCCESSFUL` from Gradle.

### Only inferred, not observed
- **What a real server reports for a real player's view.** The probe's stand-in reports `CRAFTING`
  because I built it to. The evidence that a real player does is Q1's reading of the server code
  (§5). The client session checks it by eye.
- **Everything about the client.** That the menu feels instant; whether a real right-click on another
  plugin's block screen flickers; how often each double-firing case happens. Q2's double-firing cases
  rest on the server code plus how I believe the vanilla client sends packets. The client is not in
  the server jar and was not checked.
- **That a real `PlayerInteractEvent` behaves as the constructed one did.** The probe called
  `onInteract` directly with events it built, as 7.4's probe did.

## 4. Every call that opens or closes a screen (verification 4)

| Call | Where | Runs on |
|---|---|---|
| `player.openInventory` | `SessionManager.openView`, `openEdit` (private) | only through the four public methods below |
| `SessionManager.open` | `BoundItemOpener.open` | the task from `request` (`runTask`) |
| `SessionManager.open` | `OpenCommand.open` (used by `open` and `edit`) | `runTask` |
| `SessionManager.navigate` | `ActionExecutor.open` (`MENU`) | `Sequence.run`, reached only from the scheduler (#94) |
| `SessionManager.back` | `ActionExecutor.back` (`BACK`) | `Sequence.run`; it now only opens |
| `SessionManager.refresh` (opens, or `closeInventory` for a deleted menu) | `InventoryClickListener.refreshNextTick` | `runTask` |
| `player.closeInventory` | `ActionExecutor.close` (`CLOSE`, and `BACK` with nothing to return to) | `Sequence.run` |
| `player.closeInventory` | `DeleteCommand.cascade` | `runTask` |
| `SessionManager.closeAll` → `closeInventory` | `ReloadCommand.install` | `thenAcceptAsync(…, mainThread)`, a main-thread continuation; the executor calls `runTask` |
| `SessionManager.closeAll` → `closeInventory` | `MyMenu.onDisable` | the plugin's disable callback, not an event handler |

No call opens or closes a screen inside an event handler. `SessionManager.closed`, the close
listener's bookkeeping, opens and closes nothing; its reconcile is scheduled. Nothing in `src` calls
`openBook`, `openSign` or `showDialog`.

## 5. Answers to the two questions

### Q1. How the code tells that a player has no screen open

**The check:** `player.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING`
means no screen. That covers the player's own inventory and, as the ruling says, nothing open.

**Evidence, observed in code.** Paper 26.2-129 server jar, read with `javap`. That is the jar
`runServer` runs:
- I found it at `run/versions/26.2/paper-26.2.jar`. Its SHA-256 matches the entry in the paperclip
  jar's `META-INF/versions.list`.
- The run-paper cache, `~/.gradle/caches/run-task-jars/paper/jars/26.2/129.jar`, holds only the
  paperclip jar with a binary patch.

The jar shows:
1. `CraftHumanEntity.getOpenInventory()` returns `getHandle().containerMenu.getBukkitView()`.
2. `Player.closeContainer()`, `closeContainer(Reason)` and `closeUnloadedInventory(Reason)` all set
   `containerMenu = inventoryMenu`, and the player constructor does the same. So with nothing open,
   the view is the player's own `InventoryMenu`.
3. `InventoryMenu` passes a 2×2 grid to `AbstractCraftingMenu` (`CRAFTING_GRID_WIDTH = 2`,
   `HEIGHT = 2`). Its `getBukkitView()` builds a `CraftInventoryView` over a `CraftInventoryCrafting`
   of that grid.
4. `CraftInventory.getType()` returns `CRAFTING` for a `CraftingContainer` of fewer than 9 slots,
   `WORKBENCH` for 9 or more, and `CRAFTER` for a `CrafterBlockEntity`. It returns `CRAFTING` nowhere
   else.
5. `CraftInventoryView.getType()` returns the top inventory's type, with one exception: if that type
   is `CRAFTING` and the player's game mode is `CREATIVE`, it returns `CREATIVE`.
6. `CraftServer.createInventory(holder, type)` refuses any type with `isCreatable() == false`. The
   API's `InventoryType` javadoc lists `CREATIVE`, `CRAFTING` and `MERCHANT` as not creatable.

**What `getOpenInventory()` reports with nothing open:**

| | View `getType()` | Top inventory `getType()` |
|---|---|---|
| Survival, adventure, spectator | `CRAFTING` | `CRAFTING` |
| Creative | `CREATIVE` | `CRAFTING` |

The top inventory's type does not depend on game mode, so it is what the check tests. Testing the
view's type would need both `CRAFTING` and `CREATIVE`.

**It does not mistake a real screen for "none":**
- A crafting table reports `WORKBENCH` and a crafter `CRAFTER` (point 4).
- A chest or any other block container reports its own type.
- A plugin's screen, including every MyMenu menu, is made with `createInventory` or a `MenuType`.
  Neither can produce `CRAFTING` (point 6; `MenuType.CRAFTING` builds a 3×3 table, which is
  `WORKBENCH`).
- A merchant reports `MERCHANT`, and another player's inventory opened as a screen reports `PLAYER`.

**Screens the check cannot see,** because they are not inventory views. Reported only; no code was
added for them:
- a book opened with `Player.openBook` or Adventure's `openBook`;
- a sign editor (`openSign`, `openVirtualSign`);
- a dialog (`showDialog`);
- the demo and win screens (`showDemoScreen`, `showWinScreen`).

Client-only screens send the server nothing either: chat, the pause menu, advancements, the
client-side creative inventory. The creative inventory is meant to count as the player's own
inventory. The others cannot be seen.

**Inferred:** that a real client's view matches this. I believe it does, because the server builds
the view and the client never reports opening its own inventory. The client session checks it by eye.

### Q2. Which single clicks fire more than one main-hand `PlayerInteractEvent` on Paper 26.2

**Observed in the server code.** A full scan of the jar for `callPlayerInteractEvent` found the
event fired from these places; the block classes fire only `PHYSICAL`, which the listener ignores:
- `ServerPlayerGameMode.useItemOn`, for a right-click on a block (`RIGHT_CLICK_BLOCK`);
- `ServerGamePacketListenerImpl.handleUseItem`, for the item-use packet (`RIGHT_CLICK_AIR`, or
  `RIGHT_CLICK_BLOCK` when its own ray trace hits a block);
- `ServerPlayerGameMode.handleBlockBreakAction`, on `START_DESTROY_BLOCK` (`LEFT_CLICK_BLOCK`, from
  two exclusive branches);
- `ServerGamePacketListenerImpl.handleAnimate`, on **every arm-swing packet**. It ray-traces blocks
  and entities from the player's eye.
  - It fires `LEFT_CLICK_AIR` when the trace hits nothing, or hits an entity beyond attack reach
    (outside creative).
  - It fires `LEFT_CLICK_BLOCK` when the player is in adventure mode and the trace hits a block.
- `BoatItem.use`, a second `RIGHT_CLICK_BLOCK`, but only after `handleUseItem`'s event allowed the item
  to be used. MyMenu denies it for a matched item, so for a bound item this never fires.
- The pressure-plate family: `FarmlandBlock`, `PressurePlateBlock`, `WeightedPressurePlateBlock`,
  `TripWireBlock`, `RedStoneOreBlock`, `TurtleEggBlock`, `BigDripleafBlock`, `SculkSensorBlock` and
  `SculkShriekerBlock`. All `PHYSICAL`.

**The server already de-duplicates the prompt's example.** `useItemOn` records `firedInteract`, the
block position, the hand, a copy of the item, and whether the item use was denied.
`handleUseItem` then ray-traces from the player's eye. If the trace hits the **same block**, with the
same hand and an equal item, it fires no event and reuses the recorded result. So a cancelled
right-click on a block, followed by the client's item use, also cancels that item use, with one
event.

**Single clicks that fire two events.** These are the server code combined with the vanilla client's
packet order as I understand it. The client order is **inferred**, not verified:
1. **Right-click on a block, when `handleUseItem`'s ray trace does not land on the block the client
   named:** at the edge of reach, or when position or rotation differ slightly. Then
   `RIGHT_CLICK_BLOCK`, then `RIGHT_CLICK_AIR`, or a second `RIGHT_CLICK_BLOCK` on another block.
2. **Any click the client answers with an arm swing, when the swing's ray trace misses:**
   - Left-click on a block at the edge of reach: `LEFT_CLICK_BLOCK` from `START_DESTROY_BLOCK`, then
     `LEFT_CLICK_AIR` from the swing.
   - A right-click the client predicts as a success with a client-side swing, such as throwing a
     snowball or ender pearl into the air: `RIGHT_CLICK_AIR`, then `LEFT_CLICK_AIR`.
3. **Adventure mode, right-click on a block the client predicts as interactive** (a door, lever or
   chest): `RIGHT_CLICK_BLOCK`, then `LEFT_CLICK_BLOCK` from the swing, because adventure-mode swings
   that hit a block fire `LEFT_CLICK_BLOCK`.
4. **Stage 7.5's entity events.** No entity packet fires `PlayerInteractEvent` itself.
   - A right-click on an entity sends the entity interaction (`PlayerInteractAtEntityEvent`,
     `PlayerInteractEntityEvent`).
   - If the client does not count that as used, it then sends the item use, which fires
     `RIGHT_CLICK_AIR`, or `RIGHT_CLICK_BLOCK` if a block lies behind the entity within reach.

   So one click on an entity can reach `BoundItemOpener.request` two or three times. The guard
   collapses them within a tick.

**Pairs split across a tick.** Some pairs may arrive in different ticks: packets from one click are
usually handled together, but not always. Then the first event's open has already run when the
second request's task looks, so the screen check skips it, because MyMenu's own menu is now the open
screen. The guard and the screen check together mean a click opens once either way.

## 6. Divergences from the prompt, and why

- **The prompt's location.** The session prompt named
  `C:\dev\ClaudeFiles\stage7.45-prompt\STAGE7.45-PROMPT.md`. That path did not exist, so the user put
  the file in the repository root and told me to use it. It is untracked; move it or ignore it before
  committing.
- **The order of the guard's two steps in `request`.** A4 reads "the UUID is added and the open is
  scheduled". I schedule first and add after. Both run on the main thread before the handler returns,
  so the task cannot run between them. The order means a `runTask` that throws cannot leave the
  player blocked. This mirrors `ActionExecutor.schedule`. A comment says so.
- **The probe's opener was a second instance.** The opener in `onEnable` was not the one under test.
  The probe built its own with a proxied `Plugin`, for the UUID lookup (§3.3). It is the same class
  and the same code.
- **Reading beyond §1.** Each of these was either allowed or needed for a named task:
  - The API jar and its sources jar, for javadoc and signatures (CLAUDE.md asks for `javap`).
  - The server jar in `run/versions/` (the Q1 exception).
  - The method signatures of `MenuService`, `MenuRegistry`, `Menu`, `MenuItem`, `ItemTemplate`,
    `MatchMode`, `ClickKey`, `Action` and `ActionTextResolver`, to build the probe ("Reading a class
    to learn its API is always allowed").
  - The `[Unreleased]` section of `CHANGELOG.md`, which I had to edit.
  - A targeted search of `DECISIONS.md` for superseded entries (§12).
  - A few lines around each screen call site in `InventoryClickListener`, `DeleteCommand`,
    `ReloadCommand` and `MyMenu.onDisable`, for verification 4.
  - The file listing of `run/plugins/MyMenu`, to back it up.
- **`DECISIONS.md` format.** #99 and #100 each end with a **Cost:** line, as #94 does, beyond the
  template's Old/New/Why.
- **`SPEC.md` now differs from the code, by ruling.** §9.3 still says "`BACK` pops one entry. With an
  empty stack it **closes the menu and ends the session**." That wording is replaced by ruling 2 and
  #100. It was not edited, as instructed.
  - §9.3's last paragraph ("`MENU`, `BACK`, and `CLOSE` are scheduled for the next tick…") was
    already superseded by #94 before this stage.
  - §15.1 stays true.
  - Nothing in `CLAUDE.md` turned out wrong.

## 7. Findings for planning

1. **The drop key probably opens the menu.** Inferred, not observed.
   - As I understand the vanilla client, pressing Q (drop) also swings the arm. `handleAnimate` reads
     that swing as `LEFT_CLICK_AIR`, using the item **left in the hand** after the drop.
   - So dropping one item from a stack of bound items, while looking at air, would open the menu. In
     adventure mode, looking at a block, it would fire `LEFT_CLICK_BLOCK` instead.
   - Dropping the last item leaves the hand empty, and nothing opens.
   - This belongs with 7.5's safety-net events and audit F17's question of which events should open a
     menu. Nothing was changed. The client session can check it.
2. **Adventure mode doubles right-clicks on interactive blocks** (Q2 case 3). The guard makes this
   harmless. Left-click on a block also works in adventure mode, through the swing.
3. **Stage 7.5's entity listeners.** A right-click on an entity can reach `request` up to three
   times in one click (Q2 case 4). Each entity listener should cancel its own event and call
   `request`, as the interact listener does. Nothing more is needed for de-duplication.
4. **Screens invisible to the check** (§5 Q1): books, sign editors, dialogs, and the demo and win
   screens. A bound item's click while one of them is open will still open the menu. That is unlikely
   in practice, since the player cannot click the world while such a screen is up. Reported only.
5. **Packets split across ticks** are covered by the screen check rather than the guard (§5 Q2). No
   test covers that combination; it follows from the code.

## 8. Files changed

| File | Change |
|---|---|
| `src/.../listener/BoundItemOpener.java` | **new**: the opener, its guard, the screen check and the class header |
| `src/.../listener/PlayerInteractListener.java` | takes the opener instead of `SessionManager`; requests instead of opening; header |
| `src/.../MyMenu.java` | builds the opener (wiring only; the probe's wiring is gone) |
| `src/.../session/SessionManager.java` | `back()` no longer closes; javadoc of `back()` and `NO_HISTORY` |
| `src/.../action/ActionExecutor.java` | `BACK` via `back(sequence)`, `NO_HISTORY` → `close(sequence)`; header paragraph |
| `ARCHITECTURE.md` | §9 table row, rule 9 paragraph, bound-item paragraph |
| `DECISIONS.md` | #99, #100 appended |
| `CHANGELOG.md` | two `Fixed` lines in `[Unreleased]` |
| `NOTES.md` | overwritten |
| `STAGE7.45-REPORT.md` | **new**: this report |

The probe class `listener/Stage745Probe.java` was created and deleted. `run/plugins/MyMenu` is
restored identical. `CLAUDE.md` shows as modified in `git status`, but that change was already
present at the start of the session; I did not touch it. `STAGE7.45-PROMPT.md` is untracked in the
root (§6).
