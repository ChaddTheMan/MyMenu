# Stage 7.25 report — code audit (read-only)

Audit of the stage-7 code (`ed6c40c`, documents corrected in `c2886e7`) against `SPEC.md` rev 2,
`ARCHITECTURE.md` rev 2, `DECISIONS.md`, `CLAUDE.md` and the supporting files. Nothing but this
file was created or changed.

## 1. Scope and method

**Read in full:** `CLAUDE.md`, `SPEC.md`, `ARCHITECTURE.md`, `DECISIONS.md` 58–93, `NOTES.md`,
`CHANGELOG.md`, `README.md`, `.gitignore`, `build.gradle.kts`, `settings.gradle.kts`,
`src/main/resources/config.yml`, and all 63 files under `src/main/java/`.
**Read in part:** `DECISIONS.md` 1–57 (headings, then in full: #6, #12–14, #17–33, #36–57, where
they bear on a check); the stage-report sections named in the brief and no others.
**Not read:** `legacy/`, `run/`, `build/`, `.gradle/`, `AUDIT.md`.

**Ran:**
- `javap -c -p org.bukkit.permissions.PermissibleBase` against
  `paper-api-26.2.build.124-stable.jar` from the Gradle user cache (no sources jar is cached).
- `./gradlew build -q`: exit 0. Quiet mode prints errors only, so any compiler warnings were not
  captured. Nothing tracked changed (`build/` is ignored).
- `git status`, `git log`, `git ls-files`, and greps.

**Could not check:**
- Whether Paper 26.3 has a stable build (C5). The brief allows no network use, so that premise is
  left unverified.
- The generated `paper-plugin.yml` itself, which lives under `build/`. The permission tree is taken
  from the `permissions {}` block in `build.gradle.kts`.
- Runtime behaviour. This audit is static; `runServer` was not run.
- One API statement is quoted from memory rather than checked: the Bukkit javadoc on
  `InventoryClickEvent` that lists `openInventory`/`closeInventory` as unsafe inside the handler
  (F2). No javadoc jar is cached.

**Conventions.** Code paths are relative to `src/main/java/me/chaddtheman/mymenu/`. Line numbers
are the file's own. "Stage report" means the `STAGEn-REPORT.md` sections named in the brief.

## 2. Findings

Ordered most severe first. No finding reached **High**: no path mutates the model while storage is
degraded, and no data-loss, duplication or injection path was found in the code as it stands.

### F1. Items handed out by `give` keep opening their menu after `unset`
- **Kind:** code differs from SPEC · **Severity:** Medium
- **Evidence:** SPEC.md:383–386: "A tagged item opens its menu only while that menu still has a
  bound item … So `unset` revokes every copy already dispensed by `give`." Code:
  `listener/PlayerInteractListener.java:116–119` returns the tagged menu whenever
  `registry.contains(tagged)` is true, without looking at `boundItem()`. Three texts state the
  contradicting behaviour: the class javadoc (:52–53, "that tag is the whole answer"), the `unset`
  reply (`command/UnsetCommand.java:45–46`, "Copies handed out with /mymenu give still open it while
  the menu exists"), and the `give` help description (`command/GiveCommand.java:41`, "a copy … that
  always opens it", shown by `/mymenu help`).
- **Documents / code:** SPEC says `unset` revokes dispensed copies. The code keeps them working, and
  tells the admin so.
- **Recorded?** NOTES.md:54–59 and the STAGE7 report addendum record it as a bug; no DECISIONS entry.
- **Suggested triage:** fix at 7.5. The brief's table assigns this rule to 7.5. Correct the three
  texts in the same change.

### F2. Commands in an action list run inside `InventoryClickEvent`
- **Kind:** hard-rule concern (rule 9) · **Severity:** Medium
- **Evidence:** CLAUDE.md:86: "Never open or close an inventory inside `InventoryClickEvent`." SPEC
  §9.3 (SPEC.md:569–570) defers only `MENU`, `BACK` and `CLOSE`. Code:
  `listener/InventoryClickListener.java:144` calls the dispatcher from the handler.
  `action/ActionExecutor.java:154` then runs the list in the same call, and :210–213 dispatch
  `PLAYER`, `CONSOLE` and `PLAYER_ELEVATED` commands synchronously. DECISIONS #89 makes MyMenu's own
  `/mymenu open` defer for this reason. Any other plugin's command that opens a GUI (`/warps`, `/ec`,
  a shop) opens it inside the click handler.
- **Documents / code:** the rule is absolute. The code keeps it for MyMenu's own inventory work but
  runs arbitrary commands, which may open inventories, in the forbidden context. The Bukkit javadoc
  for `InventoryClickEvent` warns against exactly this (quoted from memory; see §1).
- **Recorded?** Partly. #89 acknowledges that action lists run inside the click event, but only as a
  reason for `/mymenu open` to defer. Nothing records third-party commands.
- **Suggested triage:** fix at 7.5, or record an accepted risk in DECISIONS. The likely symptoms
  (desync, ghost items) have not been observed.

### F3. `CLOSE` closes whatever inventory the player has open, not the menu
- **Kind:** code differs from SPEC · **Severity:** Medium
- **Evidence:** SPEC.md:518: "`CLOSE` | — | Closes the menu". Code:
  `action/ActionExecutor.java:217` runs `nextTick(player, player::closeInventory)`, and `nextTick`
  (:256–262) checks only `isOnline()`. Compare `BACK`, which closes only when a view session is valid
  (`session/SessionManager.java:166–178`).
- **Documents / code:** `[PLAYER warps, CLOSE]` opens the other plugin's GUI synchronously (F2), and
  the `CLOSE` on the next tick closes that GUI. `[DELAY 100, CLOSE]` closes a chest, or the player's
  own inventory, if one was opened in the meantime.
- **Recorded?** No.
- **Suggested triage:** fix at 7.5. `action/` is a small, contained change.

### F4. The "reload running" gate exists only at the command edge
- **Kind:** risk (rule 10) · **Severity:** Medium
- **Evidence:** CLAUDE.md:88–91 says `MenuService` refuses mutations "before they reach the model".
  `service/MenuService.java:179–188` (`gate()`) checks only `loaded` and `isDegraded()`. The reload
  check lives only in `command/CommandTree.java:332–335` (`refuseWhileLocked`), which is `private`,
  throws Brigadier's `CommandSyntaxException`, and reads `ReloadCommand::isRunning`
  (`MyMenu.java:149`). DECISIONS #88: "Stage 8 must not bypass it: an editor click is a mutation, and
  it needs the same three checks."
- **Documents / code:** today every model mutation arrives through a mutating command (§4's rule-10
  trace), so the rule holds. The one implementation of the reload check cannot be called from outside
  `CommandTree`, and it speaks a Brigadier type. A future path that skips it would change menus that
  the in-flight load then replaces. That is silent loss of the edit.
- **Recorded?** #88.
- **Suggested triage:** owning later stage (8), which adds the first non-command mutation path.
  Planning decides where the check should live.

### F5. The editor's placement model cannot work under whole-view cancellation
- **Kind:** documents disagree · **Severity:** Low (documents only, but it blocks §11.2 as written)
- **Evidence:** SPEC.md:459–461 cancels every click and drag "for the whole view, top and bottom
  inventory alike, in both view and edit mode". SPEC.md:463–466 says edit mode then "reads what was
  on the cursor", and SPEC.md:656 says an item left on the cursor is returned when the editor closes.
  Code: `listener/InventoryClickListener.java:104–107` cancels bottom-half clicks before any mode
  check, and `listener/InventoryDragListener.java:34–35` cancels every drag. An admin in the edit
  view therefore can never put anything on the cursor. Related texts:
  - CHANGELOG.md:76–77 (user-visible via `/mymenu changelog 2.0.0`): "Menu items are placed by
    dragging a real item into the slot" (C1).
  - ARCHITECTURE.md:536 gives `InventoryCloseListener` the job "return cursor items".
  - `listener/InventoryCloseListener.java:34–35` says the cursor is not handled because it "can only
    hold what the player brought" (C4). That is true today only because nothing can be picked up.
  - DECISIONS #45's bullet on cursor return also depends on this.
- **Documents / code:** the documents require placement from the cursor. Their own cancellation rule,
  as built, keeps the cursor empty.
- **Recorded?** No. #80 records whole-view cancellation and its cost to the player, not its effect on
  the editor.
- **Suggested triage:** documents only. Settle this before stage 8, which owns §11.2.

### F6. CLAUDE.md rule 2 says `onDisable` "never initiates a save"; it does
- **Kind:** documents disagree · **Severity:** Low
- **Evidence:** CLAUDE.md:67–69: "`onDisable` drains in-flight writes … It never initiates a save."
  DECISIONS #49 (flush before draining) and ARCHITECTURE.md §7.4 ("It writes pending debounced work
  and drains the queue") say otherwise, and so does the code. `MyMenu.java:229–231` calls
  `writer.flush()`. That calls `submit()` (`storage/DebouncedMenuWriter.java:115–119`), which issues
  `saveAll` and `delete` for the pending batch. `storage/YamlMenuStorage.java:301–310` retries a
  failed write. DECISIONS #33, the origin of the wording, carries no dated correction pointing to #49.
- **Documents / code:** the rule's wording predates debouncing. The code follows #49.
- **Recorded?** #49 records the change; CLAUDE.md and #33 were not updated.
- **Suggested triage:** documents only.

### F7. Library download at plugin load is a second de-facto blocking-I/O exception
- **Kind:** documents disagree · **Severity:** Low
- **Evidence:** CLAUDE.md:65–69 permits "exactly one exception". `MyMenuLoader.java:61–68` hands a
  `MavenLibraryResolver` to Paper, which downloads HikariCP and the MySQL driver during plugin loading
  at server start. ARCHITECTURE.md:612–614 accepts this as a "Known cost".
- **Documents / code:** the rule allows one exception; the architecture accepts a second, network-bound
  one. This is uncertain: the thread Paper uses for loader resolution was not verified, but it runs
  before the tick loop.
- **Recorded?** Only as a cost (ARCHITECTURE §11, #6), not as a rule-2 exception.
- **Suggested triage:** documents only. Either name it in rule 2 or scope rule 2 to runtime.

### F8. Reload clears the write-degraded state without proving a write works
- **Kind:** code differs from SPEC · **Severity:** Low
- **Evidence:** SPEC.md:742: reload "clears the state if the data now loads and writes". Code:
  `storage/YamlMenuStorage.java:186–188` sets `writeDegraded = false` on any clean load. A failed
  delete backup (:223–227) degrades without marking anything unwritten, so reload's retry
  (`command/ReloadCommand.java:164`) writes nothing, and the load then clears the flag. The discard
  form behaves the same way.
- **Documents / code:** SPEC asks for proof of a working write. The code accepts a clean read. The next
  edit re-degrades if the fault persists, and the edit is kept and retried, so no data is lost.
- **Recorded?** #68: "A clean load clears the write-degraded flag." SPEC was not updated.
- **Suggested triage:** documents only.

### F9. A refused shrink cannot name the offending slots
- **Kind:** unowned requirement · **Severity:** Low
- **Evidence:** SPEC.md:483–486: "the refusal names the offending slots". Code:
  `service/MenuService.java:172–173` returns `Refused(ITEMS_OUTSIDE_LAYOUT)`.
  `service/MutationResult.java:35` carries only a `Reason`. `command/Replies.java:61` says "That would
  leave items outside menu 'x'." `changeLayout` has no caller.
- **Documents / code:** the refusal exists without the slot list. `Menu.slotsOutside` could supply it,
  but the result type cannot carry it.
- **Recorded?** #63 records the refusal, not the naming.
- **Suggested triage:** owning later stage (8). The editor is the only future caller. See Q1.

### F10. `NOTES.md` is stale
- **Kind:** documents disagree · **Severity:** Low
- **Evidence:** NOTES.md:4–7 says stage 7 is "written, verified and reviewed, but not committed", but
  it is `ed6c40c`, and `c2886e7` followed it (C7). NOTES.md:71–72 still asks the user to commit
  stage 7. NOTES.md:43 says SPEC §13 "has no stage" (7.5 owns it now). NOTES.md:53–59 files the
  `unset` fix under "Stage 8" (7.5 owns it now).
- **Documents / code:** NOTES describes the state before the stage-7 commit. The repository is past it.
- **Recorded?** Known to be stale, per the brief.
- **Suggested triage:** documents only (the next building session overwrites it).

### F11. Two `CHANGELOG.md` lines overstate what exists
- **Kind:** documents disagree (user-visible) · **Severity:** Low
- **Evidence:** CHANGELOG.md:28: "Menus cannot be named `none`". SPEC.md:91–99 and #93 say only
  `/mymenu create` refuses it, and a hand-written `none` menu is valid. CHANGELOG.md:21–22: `joinmenu`
  "chooses the menu that opens on join", but nothing opens a menu on join yet (C11). Both lines are
  shown by `/mymenu changelog`. The drag line is F5.
- **Documents / code:** the changelog promises a blanket name ban and a working join menu. The code
  refuses the name only in `create` (`command/CommandTree.java:390–393`) and registers no join
  handler (`MyMenu.java:139–145`).
- **Recorded?** No.
- **Suggested triage:** documents only. The join line becomes true at 7.5.

### F12. Stale code comments and help text
- **Kind:** stale comment · **Severity:** Low
- **Evidence:**
  - `action/ActionExecutor.java:119` and `action/ActionParser.java:75`: `TODO(stage 7): config
    plumbing calls this once … is read`. Done at `MyMenu.java:195–196`.
  - `command/DeleteCommand.java:50–55` says SPEC §3.5 asks for pending `MENU` actions to be
    cancelled and that this "is not done here". SPEC.md:133–138 and #92's correction dropped the
    requirement.
  - `action/ActionParser.java:50`: "The same clamp covers shorthand input". `parseShorthand`
    (:157–170) has no `DELAY` form, so `parseList`'s clamp (:173–179) never has anything to clamp.
  - `command/CommandSpec.java:34` and `command/HelpCommand.java:70–72` (user-visible via
    `/mymenu help command <name>`) describe mutating commands as refused "while storage is degraded"
    (CommandSpec adds "or a reload is running"). `CommandTree.java:336–338` also refuses them before
    menus load.
- **Documents / code:** each comment describes an earlier plan or a narrower rule than the code
  now implements.
- **Recorded?** No.
- **Suggested triage:** documents only (comment and string edits).

### F13. SPEC §15.1 specifies behaviour under "Explicitly out of scope"
- **Kind:** documents disagree · **Severity:** Low
- **Evidence:** SPEC.md:858 opens "## 15. Explicitly out of scope for 2.0.0". SPEC.md:871–875 then
  specifies how a bound item opens its menu (C2), and SPEC.md:879–882 add the MySQL single-writer
  note and a permanent rejection under the same heading. The code implements §15.1
  (`listener/PlayerInteractListener.java:97–112`), and the brief's table assigns part of it to 7.5.
- **Documents / code:** the heading says the section is out of scope, but it is required behaviour,
  and the code builds it.
- **Recorded?** No.
- **Suggested triage:** documents only.

### F14. SPEC §13's "already has an active session" skip needs a stated moment
- **Kind:** documents disagree · **Severity:** Low
- **Evidence:** SPEC.md:767 and DECISIONS #55 skip "a player who already has an active session". The
  quit handler clears everything unconditionally (`listener/PlayerQuitListener.java:37–40` →
  `session/SessionManager.java:251–260`), so no session exists at `PlayerJoinEvent` (C3). SPEC.md:759
  opens the join menu 20 ticks later, and within those ticks a player can open a menu by bound item
  or command.
- **Documents / code:** the rule is meaningful only if it is evaluated when the 20-tick open fires.
  SPEC does not say when.
- **Recorded?** No.
- **Suggested triage:** owning later stage (7.5). Clarify the text when building it.

### F15. `config.yml` ships `configVersion`, which nothing reads
- **Kind:** code differs from SPEC · **Severity:** Low
- **Evidence:** `src/main/resources/config.yml:2` contains `configVersion: 1`. SPEC.md:272–274: "The
  bundled default file contains only the keys implemented so far … shipping unread keys implies
  settings that do nothing." `config/PluginConfig.java:106–115` reads no such key, and nothing else
  does (grep). SPEC.md:229 lists the key but never says what it does.
- **Documents / code:** SPEC forbids shipping unread keys. The bundled file ships one.
- **Recorded?** No. #69's list of bundled keys omits it.
- **Suggested triage:** documents only (say what it is for), or no action. See Q1.

### F16. Choices recorded only in stage reports; SPEC text not updated
- **Kind:** documents disagree · **Severity:** Low
- **Evidence:**
  - `set` strips `mymenu:bound_item` before capture (`command/SetCommand.java:56–59`). SPEC.md:105–107
    says "captured in full … all metadata". Recorded in STAGE7 §6.7 and the addendum.
  - `give` always hands out a stack of one (`command/GiveCommand.java:66–67`). SPEC §3.3 is silent.
    Recorded in STAGE7 §6.6.
  - A refused reload still applies `config.yml` (`command/ReloadCommand.java:149–152`, :207). SPEC §3.6
    is silent. Recorded in STAGE7 §6.8 and the addendum.
  - SPEC.md:64's command table has no row for `reload discard-unsaved`
    (`command/CommandTree.java:272–273`). Recorded in #71.
  - A player's `create` shows a hint rather than opening the editor (`command/CreateCommand.java:50–51`,
    compare SPEC.md:79–80). Recorded in the STAGE7 addendum.
- **Documents / code:** each is an accepted choice that SPEC either contradicts in wording or leaves
  out.
- **Recorded?** Yes, as listed. Documents not updated.
- **Suggested triage:** documents only.

### F17. Behaviour in the code that no document describes
- **Kind:** undocumented behaviour · **Severity:** Low
- **Evidence:**
  - `backups.keep: 0` turns save backups off entirely (`storage/BackupWriter.java:61`, :99), and
    `config/PluginConfig.java:173` accepts 0. Neither SPEC §5.1 nor §11.6 says so. With 0, a write no
    longer copies the live file first; rule 6 (CLAUDE.md) assumes it always does.
  - A slot showing its hidden fallback (or empty) does nothing when clicked and plays no sound
    (`listener/InventoryClickListener.java:134–138`). SPEC §8.3 is silent.
  - A stale click on a deleted menu closes it (`session/SessionManager.java:190–193`), where SPEC.md:496
    says "re-renders".
  - A bound item opens its menu even when another plugin has already cancelled the interaction, for
    example in a protected region. Only an explicit `DENY` on item use stops it
    (`listener/PlayerInteractListener.java:63–68`, :97).
  - `give` to a full inventory drops the item at the player's feet
    (`command/GiveCommand.java:69`, `give(…, true)`).
- **Documents / code:** the documents say nothing, or (for §8.5) say something else. The code
  behaves as listed.
- **Recorded?** No, apart from the class javadocs.
- **Suggested triage:** documents only. The `keep: 0` case is worth a sentence in SPEC §5.1.

### F18. `ActionParser` has more entry points than recorded, and `read` skips the delay cap
- **Kind:** risk · **Severity:** Low
- **Evidence:** the public parsing methods are `read` (`action/ActionParser.java:90`), `readList`
  (:117), static `parseShorthand` (:157), `parseList` (:173) and `clampTotalDelay` (:196). Only
  `readList` and `parseList` apply the SPEC §9.2 cap. NOTES.md:67–68 tells stage 8 to use "`parseList`
  … or `ActionParser.read`". DECISIONS #85, which C23 cites, lists no entry points.
- **Documents / code:** an editor that builds a list one `read` at a time would store an uncapped
  total delay.
- **Recorded?** No.
- **Suggested triage:** owning later stage (8), plus a NOTES correction.

### F19. `DECISIONS.md` entries 65, 69 and 74 are no longer accurate
- **Kind:** DECISIONS entry stale · **Severity:** Low
- **Evidence:**
  - #65 (DECISIONS.md:675–676) lists six `MenuStorage` methods. `storage/MenuStorage.java:47–85` has
    eight (`retryUnwritten`, `discardUnwritten`, added by #87 and #71). #65 has no correction.
  - #69 (DECISIONS.md:776–787) says SPEC §5.1 has two `storage:` keys and that the default file
    holds three keys. SPEC.md:235–246 now has one block, and `config.yml` holds seven keys.
  - #74 (DECISIONS.md:865–866): "Open for stage 6: whether clicking a barrier runs the slot's
    actions". SPEC.md:475–476 settles it (it does), and the code agrees
    (`listener/InventoryClickListener.java:129–144`). There is no closing correction.
- **Documents / code:** the entries describe an earlier state. The code matches the later documents.
- **Recorded?** Only by the later entries and SPEC text cited.
- **Suggested triage:** documents only (dated corrections).

### F20. Older DECISIONS entries superseded without a dated correction
- **Kind:** DECISIONS entry stale · **Severity:** Low
- **Evidence:** CLAUDE.md requires a dated correction on the original. None of these has one:
  - #32 (DECISIONS.md:297–302), superseded by #47: the refusal moved out of storage.
  - #33 (:304–310), superseded by #49: see F6.
  - #37 (:338–343), superseded by #46 and #61: the revision is not on the menu.
  - #52 (:467–471), superseded by #92's correction: pending `MENU` actions are no longer cancelled.
  - #53 (:473–476), superseded by #61 and #65: "Models are main-thread-only" and `save(Menu)` are gone.
  - #66's cost paragraph (:710–716), reversed in part by #73.
- **Documents / code:** in each case the code follows the later entry (for example,
  `MenuService.gate()` holds the refusal and `YamlMenuStorage` has no `save(Menu)`), so only the
  older text misleads.
- **Recorded?** Yes, in the superseding entries; the originals were not annotated.
- **Suggested triage:** documents only.

### F21. ARCHITECTURE §1, §2 and §7 lag the code
- **Kind:** code differs from ARCHITECTURE · **Severity:** Low
- **Evidence:**
  - ARCHITECTURE.md:33 draws `Map<ClickType, List<Action>>`. `model/MenuItem.java:41` uses `ClickKey`
    (#62), and ARCHITECTURE §3 was already corrected.
  - The §2 package list (ARCHITECTURE.md:53–65) omits `BoundItem`, `ClickKey`, `VersionedMenu`,
    `MenuPersistence`, `MutationResult`, `ActionTextResolver`, `CommandSanitiser`,
    `DebouncedMenuWriter`, `MenuYamlFormat`, `ActionCodec`, `CommandSpec` and `Replies`.
  - The §7 interface block (ARCHITECTURE.md:349–358) lacks `retryUnwritten` and `discardUnwritten`.
  - The §9 cursor row is F5.
- **Documents / code:** ARCHITECTURE shows the pre-stage-3 shapes. The code has moved on as the
  entries record.
- **Recorded?** #62, #65, #71, #87. ARCHITECTURE was not updated.
- **Suggested triage:** documents only.

### F22. `runServer` accepts the Minecraft EULA on the user's behalf
- **Kind:** undocumented behaviour · **Severity:** Low
- **Evidence:** `build.gradle.kts:50`: `jvmArgs("-Dcom.mojang.eula.agree=true")` (C6). No document
  mentions it. Accepting the EULA is the server owner's act; the flag performs it silently on every
  test-server start.
- **Documents / code:** no document says it. The build does it.
- **Recorded?** No.
- **Suggested triage:** documents only (record it), or no action. The user's call.

## 3. Coverage table

| SPEC § | Status | Findings / note |
|---|---|---|
| 1 Purpose | Not applicable | |
| 2 Platform target | Built as written | Paper 26.2, Java 25, `paper-plugin.yml`, Brigadier via `LifecycleEvents.COMMANDS` |
| 3 Commands (table) | Built with recorded divergence | All 18 rows present with the listed nodes and player-only flags. The discard form is missing from the table (F16) |
| 3.1 Argument detail | Built with recorded divergence | `create` gives a hint rather than opening the editor (F16) |
| 3.2 Menu names | Built as written | #93 |
| 3.3 `set`, `unset`, `give` | Built with recorded divergence | Tag stripping and stack of one (F16). `unset`'s reply contradicts §7 (F1) |
| 3.4 `joinmenu` | Built as written | Only runtime writer of `config.yml` |
| 3.5 `delete` | Built as written | Backup, cascade and `joinMenu` warning present. Stale javadoc (F12) |
| 3.6 `save` and `reload` | Built with recorded divergence | Config applied on a refused reload (F16). `messages.yml` belongs to stage 10 |
| 3.7 `changelog` and `update` | Built with recorded divergence | `update` is the #90 stub (due now, correct as a stub) |
| 4 Permissions | Built as written | Tree in `build.gradle.kts` matches. See Q2 |
| 5 Files | Built as written | `messages.yml` belongs to stage 10 |
| 5.1 `config.yml` | Built with unrecorded divergence | `configVersion` is shipped unread (F15). `keep: 0` is undocumented (F17). Unread keys belong to later stages |
| 5.2 `messages.yml` | Later stage (10) | No code contradicts it; hard-coded strings carry TODOs |
| 5.3 `menus.yml` | Built as written | |
| 6 Item representation | Built as written | Editing belongs to 8, MySQL to 11 |
| 7 Bound items and match modes | Partly built | Tag first, the modes and name-order ties are built. The unset rule is contradicted by code (F1, 7.5). The load-time log belongs to 7.5 |
| 8 Menu behaviour | Not applicable | Heading only |
| 8.1 Types and sizes | Built as written | |
| 8.2 Rendering | Built as written | |
| 8.3 Per-item properties | Built as written | Inert fallback clicks undocumented (F17). Bare sound names are the editor's (8) |
| 8.4 Clicks | Built as written | Edit interpretation belongs to 8, and as written it cannot be built (F5) |
| 8.4.0 Items that cannot be loaded | Built as written | |
| 8.4.1 Shrinking a menu | Partly built | Refusal exists; naming the slots does not (F9) |
| 8.5 Stale views | Built with unrecorded divergence | A deleted menu closes rather than re-renders (F17) |
| 9 Action types | Built as written | Commands run inside the click event (F2). `CLOSE` closes any inventory (F3) |
| 9.1 Elevation | Built as written | |
| 9.2 Delays and sequencing | Built as written | #82, #84, #86 |
| 9.3 Navigation | Built with unrecorded divergence | `CLOSE` scope (F3). The rest as written, including the clamp to 1–32 (#83) |
| 10 Text substitution | Later stage (9) | Due-now seam `render/TokenReplacer` is built as written |
| 10.1 Injection safety | Built as written | Command sanitisation is due now: `action/CommandSanitiser`, applied unconditionally at `ActionExecutor.java:223–225` |
| 10.2 Colour | Built as written | Italic rule #70, #73 |
| 11 Editing | Later stage (8) | |
| 11.1 Edit-mode rendering | Built as written | Due now. Clicks are a stub (C16) |
| 11.2 Placing items | Later stage (8) | Cannot be built as written (F5) |
| 11.3 Property editor | Later stage (8) | `giveItemOnJoin` setter lives here (C12) |
| 11.4 Text input | Later stage (8) | `EditSession.PendingPrompt` scaffold holds only a timeout (C17). `editor.chatInputTimeoutSeconds` is unread (C13) |
| 11.5 Visibility of edits | Built as written | Built at stages 3–5 |
| 11.6 Saving | Built as written | Built at stage 3. MySQL part belongs to 11. `keep: 0` (F17) |
| 12 Degraded state | Built with recorded divergence | Clears on a clean load without a write (F8, #68). Reload gate (F4). Rule-10 trace in §4 |
| 13 Join behaviour | Later stage (7.5) | No code contradicts it. `joinMenu` is read and settable (#91). The session-skip rule needs a moment (F14) |
| 14 Metrics | Later stage (10) | No code or scaffolding. Shadow correctly not yet applied (#60) |
| 14.1 Privacy rule | Later stage (10) | |
| 14.2 Charts | Later stage (10) | The nine website charts are the user's task at stage 10 (CLAUDE.md) |
| 14.3 Website registration | Later stage (10) | |
| 14.4 Opt-out | Later stage (10) | No MyMenu switch exists, as required |
| 14A Update checking | Later stage (10) | `update` stub audited under §3.7 |
| 15 Out of scope (items 1–10) | Not applicable | Nothing in code implements any of them |
| 15.1 Bound item opening | Built as written | Main hand, left or right click, event cancelled. Misfiled (F13). "Every normal use" belongs to 7.5 |
| §15 trailing paragraphs (MySQL single-writer; scripting rejected) | Not applicable / later stage (11) | Misfiled (F13) |

## 4. Hard rules table

| Rule | Verdict | Evidence |
|---|---|---|
| 1 No static mutable state | Holds | No non-final static field in `src/main/java`. Static finals are immutable (`List.of`, `Set.of`, `Pattern`, records). `SessionManager.openHolder` is a stateless static method |
| 2 No blocking I/O on the main thread | Holds (documents: F6, F7) | Config via `supplyAsync` (`MyMenu.java:170`, `ReloadCommand.java:137`). Menus on `MyMenu-Storage` (`YamlMenuStorage.java:126`). `joinmenu` via `thenRunAsync` (`MyMenu.java:203`). Changelog via `supplyAsync` (`ChangelogCommand.java:62`). Only `onDisable` blocks, bounded 10 s + 10 s (`YamlMenuStorage.java:97`, :316, :331) |
| 3 The model is never the view | Holds | `MenuHolder` keeps name, revision and mode only (`render/MenuHolder.java:54–57`). Clicks resolve from the registry (`InventoryClickListener.java:118–130`) |
| 4 Identify menus by `InventoryHolder` | Holds | Every listener tests `getHolder() instanceof MenuHolder` (Click :104, Drag :34, Close :47, `SessionManager.java:104`) |
| 5 Never op a player | Holds | `ActionExecutor.java:227–241`: attachment added, command dispatched, `attachment.remove()` in `finally`. No `setOp` anywhere |
| 6 Never write the live file in place | Holds (F17) | `YamlMenuStorage.java:259–266`: backup, then temp file with `force`, then `ATOMIC_MOVE`. `keep: 0` skips the backup copy (F17) |
| 7 Listeners hold no per-event state | Holds | Listener fields are final dependencies. `PlayerInteractListener.reportedUnreadable` is a log-once cache, and `ActionExecutor.pending` is per-player state owned by design (#84) |
| 8 Session validity is derived | Holds | `SessionManager.view`/`edit` validate against the open holder (:109–135). `closed` branches on `OPEN_NEW` (:228–248). `endAll` uses `finally` (:251–260). Sessions are created after a successful open (:205–209, :215–219) |
| 9 No inventory open/close in `InventoryClickEvent` | Concern (F2; see F3) | MyMenu's own opens and closes are deferred (`ActionExecutor.java:215–217`, `InventoryClickListener.java:147–153`, #89). Commands in action lists run synchronously in the handler |
| 10 Refuse mutations before the model | Holds today; concern (F4) | See the trace below |
| 11 Substitution after parsing | Holds | `TokenReplacer` takes and returns `Component` (`render/TokenReplacer.java:47–52`). `ActionTextResolver.message` returns `Component`, and commands always go through `CommandSanitiser` (`ActionExecutor.java:223–225`) |
| 12 No legacy support | Holds | `Material.matchMaterial` with `isLegacy()` rejected (`MenuYamlFormat.java:295–297`). No alias table, no damage values, no 1.x commands or migration |

**Rule 10 trace.** Every call site of a `MenuService` mutator (grep):

| Path | Degraded | Not loaded | Reload running | Before the model? |
|---|---|---|---|---|
| `create` → `MenuService.create` (`CreateCommand.java:47`) | Edge + service | Edge + service | Edge only | Yes: `gate()` runs before `Menu.create` (`MenuService.java:110–117`) |
| `delete` → `MenuService.delete` (`DeleteCommand.java:76`) | Edge + service | Edge + service | Edge only | Yes (:121–125) |
| `set` → `MenuService.update` (`SetCommand.java:61`) | Edge + service | Edge + service | Edge only | Yes. `capture` runs first (:60) but changes nothing |
| `unset` → `MenuService.update` (`UnsetCommand.java:43`) | Edge + service | Edge + service | Edge only | Yes |
| `joinmenu` → `config.yml` | Edge | Edge | Edge | Not a model change. Writes the file only when accepted |
| `edit` → opens `EDIT` view | Edge | Edge | Edge | Not a model change. Editor clicks are a stub |
| `MenuService.changeLayout` | Service | Service | None | No caller |
| `replaceAll` (`MyMenu.java:179`, `ReloadCommand.java:186`) | n/a | n/a | n/a | This is the load itself |

"Edge" is `CommandTree.run` → `refuseWhileLocked` (`CommandTree.java:319–321`, :332–342), applied
because each spec has `mutating = true`. "Service" is `MenuService.gate()`
(`MenuService.java:179–188`). No path reaches the model while degraded or before loading. The
reload condition is enforced at the edge alone (F4).

## 5. `DECISIONS.md` 58–93 table

| # | Status | Note |
|---|---|---|
| 58 | Still accurate | As of its date. C5's premise (26.3 stable) could not be verified |
| 59 | Still accurate | Registration is idempotent and `onDisable` leaves nothing running; runtime claims not re-run |
| 60 | Still accurate | |
| 61 | Still accurate | |
| 62 | Still accurate | |
| 63 | Still accurate | Slot naming is a SPEC gap (F9) |
| 64 | Still accurate | With its correction |
| 65 | Stale (F19) | Interface now has eight methods |
| 66 | Still accurate | Cost paragraph superseded by #73 without correction (F20) |
| 67 | Still accurate | The "until stage 6" note is historical |
| 68 | Still accurate | Diverges from SPEC §12.5 (F8) |
| 69 | Stale (F19) | |
| 70 | Still accurate | |
| 71 | Still accurate | |
| 72 | Still accurate | |
| 73 | Still accurate | |
| 74 | Stale (F19) | Stage-6 question settled |
| 75 | Still accurate | With its correction |
| 76 | Still accurate | With its correction (`model/VersionedMenu`) |
| 77 | Still accurate | |
| 78 | Still accurate | |
| 79 | Still accurate | |
| 80 | Still accurate | Consequence for the editor unrecorded (F5) |
| 81 | Still accurate | |
| 82 | Still accurate | |
| 83 | Still accurate | With its correction. Stale TODO in code (F12) |
| 84 | Still accurate | |
| 85 | Still accurate | |
| 86 | Still accurate | With its correction |
| 87 | Still accurate | |
| 88 | Still accurate | Risk noted in F4 |
| 89 | Still accurate | |
| 90 | Still accurate | |
| 91 | Still accurate | |
| 92 | Still accurate | With its correction. Stale javadoc in code (F12) |
| 93 | Still accurate | CHANGELOG overstates it (F11) |

## 6. Claims table

Checked after the six-direction pass.

| Claim | Verdict | Evidence |
|---|---|---|
| C1 | Confirmed | CHANGELOG.md:76–77. `InventoryDragListener.java:34–35` cancels every drag while a menu is open → F5 |
| C2 | Confirmed | SPEC.md:858, :871–875 → F13 |
| C3 | Confirmed, with a qualification | True at `PlayerJoinEvent` (`PlayerQuitListener.java:37–40` → `SessionManager.java:251–260`). Not necessarily true 20 ticks later, when SPEC.md:759 opens the menu → F14 |
| C4 | Confirmed | `InventoryCloseListener.java:34–35`. Nothing can place an item on a cursor: `InventoryClickListener.java:104–107`, `InventoryDragListener.java:34–35` → F5 |
| C5 | Confirmed (text); premise unverified | `build.gradle.kts:14–15` ("alpha-only as of 2026-09-18"). Whether 26.3 is now stable was not checkable offline. If it is, #58 says to move: two values at `build.gradle.kts:16–17` |
| C6 | Confirmed | `build.gradle.kts:50` → F22 |
| C7 | Confirmed | NOTES.md:4–7 against `git log` (`ed6c40c`, then `c2886e7`) → F10 |
| C8 | Confirmed | `Menu.java:72–80` has no permission component. Only `MenuItem.viewPermission` (`MenuItem.java:42`) exists. This is by design: SPEC.md:202–203 |
| C9 | Confirmed | No such logging exists. `PluginConfig.java:181–191` checks only the name's form. Stage 7.5 owns it |
| C10 | Confirmed | `PlayerInteractListener.java:116–119`, `UnsetCommand.java:45–46` → F1 |
| C11 | Confirmed | `MyMenu.java:139–145` registers `ActionExecutor` plus five listeners. There is no `PlayerJoinListener` |
| C12 | Confirmed | `Menu.withGiveItemOnJoin` (`Menu.java:161`) has no caller. Readers are `MenuYamlFormat.java:203`, :351 and `InfoCommand.java:52`. The setter is §11.3's (stage 8) while 7.5 consumes the flag (see Q1) |
| C13 | Confirmed | `PluginConfig.java:56–57`. No code mentions `chatInputTimeoutSeconds`. Stage 8's key |
| C14 | Confirmed | `MenuHolder.java:52` (`public final class`), :59 (package-private constructor). Sole construction at `MenuRenderer.java:130` |
| C15 | Confirmed | `InventoryClickListener.java:104–107`, `InventoryDragListener.java:34–35`, `InventoryCloseListener.java:47–50`, `SessionManager.java:103–107` |
| C16 | Confirmed | `InventoryClickListener.java:111–113` returns before the `EDIT` stub at :114–117 |
| C17 | Confirmed | `SessionManager.java:127–131`; `EditSession.java:43–52` |
| C18 | Confirmed | `CommandTree.java:332` (`private void refuseWhileLocked() throws CommandSyntaxException`); `Replies.java:29` (`final class Replies`, package-private) |
| C19 | Confirmed | `MenuService.java:156–158`, :172–173 |
| C20 | Confirmed | `MenuItem.java:40–45`, `Menu.java:72–80`, `ItemTemplate.java:53`, :61–62, :106. No finding: SPEC §6 puts `glow` inside `item:`, which is what §8.3's row describes. Note that a hidden fallback carries its own glow |
| C21 | Confirmed, with a naming correction | `ItemSerializer.java:85–87`. The checked `UnreadableItemException` is declared by `ItemBuilder.build(ItemTemplate, TokenReplacer)` (`ItemBuilder.java:113`). The opaque path is the private `buildOpaque` (:133). No public `build(Opaque)` exists, and `build(Descriptive, …)` (:121) throws nothing checked |
| C22 | Confirmed | `SetCommand.java:51`, :58–59; `NameCommand.java:43` |
| C23 | Refuted (incomplete) | `readList` (`ActionParser.java:117`) and `clampTotalDelay` (:196) are public entry points too. `read` applies no cap. #85 names no entry points; NOTES.md:67 is the source → F18 |
| C24 | Confirmed | `ClickKey.java:28–37`; fallback in `InventoryClickListener.java:160–166`; an empty list does nothing (`ActionExecutor.java:133–135`). The fallback rule is SPEC §8.4 and #24/#45; #62 is the enum itself |

## 7. Answers to the two questions

### Q1. Unowned requirements

SPEC rev 2 requirements that are neither built nor owned by a later stage in the brief's table:

1. **§8.4.1 "the refusal names the offending slots"** (F9). The refusal is built but cannot carry
   slots. Nearest stage: **8**. `changeLayout` has no caller until the editor's menu-level settings
   (§11.3) change type or rows.
2. **§5.1 `configVersion`** (F15). SPEC lists the key and gives it no behaviour, and no stage reads
   it. Nearest stage: **10**, the next stage to change config shape (`messages.yml`, update-check
   keys). Alternatively, remove it from SPEC.

Not unowned, but likely to trip the stage plan:

- **§13's fourth bullet** (update notification 100 ticks after join) is in 7.5's "§13 in full" and
  in stage 10's §14A step 5. It cannot be built at 7.5, because the checker is stage 10. Suggest
  assigning it to **10**.
- **`giveItemOnJoin` has no in-game setter until stage 8** (§11.3; C12), but 7.5 makes the flag act.
  Between the two stages it can be set only by hand-editing `menus.yml`, against SPEC §1's "never
  required".

### Q2. Do Bukkit permission children resolve recursively?

**Yes, to any depth.** From `javap -c -p org.bukkit.permissions.PermissibleBase` on
`paper-api-26.2.build.124-stable.jar`:

```
private void calculateChildPermissions(Map<String,Boolean>, boolean, PermissionAttachment);
   57: invokeinterface PluginManager.getPermission(String)          // look up the node
   77: iload_2
   78: ixor                                                          // value XOR invert
  113: invokeinterface Map.put                                       // record the node itself
  140: ifnull 163                                                    // registered? then:
  146: invokevirtual Permission.getChildren()
  149: iload 8 / ifne 158 / iconst_1 / goto 159 / 158: iconst_0      // invert = !value
  160: invokevirtual calculateChildPermissions(Map, boolean, PermissionAttachment)   // recursion
```

`recalculatePermissions()` feeds it from two places. For each default permission the sender
qualifies for, it records the node as true and then recurses into its children (offsets 16, 81–134:
`getDefaultPermissions(isOp)` … `calculateChildPermissions(perm.getChildren(), false, null)`). It
does the same for each attachment (169–176). `hasPermission(String)` then reads the flattened map,
and falls back to the node's own default only when the node is absent (that method's offsets 22–89).

**What that reaches**, with the tree in `build.gradle.kts:72–117`. The generated file under `build/`
was not read, and every declared child is `true`.

- **`MyMenu.*` through operator status** (`default: OP`): an op receives every registered node whose
  default is `OP` or `TRUE` directly, and child expansion from `MyMenu.*` reaches the same set. That
  is 22 nodes:
  - `MyMenu.*`, `MyMenu.admin.*`
  - `MyMenu.help.admin`, `MyMenu.help`, `MyMenu.help.player`
  - `MyMenu.admin.reload`, `.save`, `.update`, `.joinmenu`
  - `MyMenu.admin.menu.*` and its ten children `MyMenu.admin.menu.{list, open, open.other, edit,
    create, delete, set, unset, give, info}`
  - `MyMenu.admin.item.*`, `MyMenu.admin.item.name`

  It does **not** reach `MyMenu.bypass.cooldown` (`default: FALSE`, outside the tree). A non-op with
  `MyMenu.*` granted directly reaches the same 22 through recursion alone.
- **`MyMenu.admin.*` granted directly:** 21 nodes, which is all of the above except `MyMenu.*`
  itself. This **includes `MyMenu.admin.menu.list`** (two levels: `admin.*` → `admin.menu.*` →
  `admin.menu.list`) and `MyMenu.help.player` (three levels). It does not reach
  `MyMenu.bypass.cooldown`.

The retired claim, that "children are one level" so `MyMenu.admin.*` does not grant
`MyMenu.admin.menu.list`, is **false for Bukkit's own resolution**. No repository document or code
comment repeats it (grep across every document read and all of `src/`; `AUDIT.md` was excluded
unread, per the brief). Permission plugins such as LuckPerms can resolve differently, and that was
not tested here.

## 8. Counts

**By severity:** High 0 · Medium 4 (F1–F4) · Low 18 (F5–F22) · total 22.

**By suggested triage:**

| Triage | Count | Findings |
|---|---|---|
| Fix at 7.5 | 3 | F1, F2, F3 |
| Owning later stage | 4 | F4 (8), F9 (8), F14 (7.5), F18 (8) |
| Documents only | 15 | F5, F6, F7, F8, F10, F11, F12, F13, F15, F16, F17, F19, F20, F21, F22 |
| No action | 0 | (F15 and F22 offer it as an alternative) |
