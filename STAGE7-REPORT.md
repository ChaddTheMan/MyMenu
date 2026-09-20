# Stage 7 report — commands

## 1. What was built

`command/` (new, 18 files):

- **`CommandTree`** — the only file that mentions Brigadier. It builds the `/mymenu` tree
  (alias `mm`), gates each node with `requires`, resolves arguments into finished values, and
  supplies suggestions. Registered through `LifecycleEvents.COMMANDS`; it builds fresh nodes on
  every call and touches nothing else, so the datapack reload that re-fires the handler is
  harmless.
- **`CommandSpec`** — name, permissions, description, player-only, mutating. One record per
  subcommand, and the single source for permission gating, the degraded refusal and help.
- **Sixteen subcommand classes** — `help`, `list`, `info`, `open`, `edit`, `create`, `delete`,
  `set`, `unset`, `give`, `joinmenu`, `name`, `save`, `reload`, `changelog`, `update`. Each
  takes resolved parameters: `execute(CommandSender, Menu, MatchMode)`, never `String[]`.
  `create` takes a validated name, since its menu does not exist yet.
- **`Replies`** — the hard-coded English, ready to move to `messages.yml` at stage 10.

Elsewhere:

- `PluginConfig` reads `actions.maxTotalDelaySeconds`, `navigation.maxDepth`, `joinMenu` and
  `storage.type`, and gained `writeJoinMenu`, a line-level rewrite of the one key `joinmenu`
  sets. Comments and every other line survive it; it writes a temp file and moves it over.
- `MenuStorage` gained `retryUnwritten`, `discardUnwritten` and `UnwrittenChangesException`.
  `DebouncedMenuWriter` gained `flushAsync` (what `save` and reload use instead of the
  blocking `flush`) and `discardPending`.
- `MyMenu` wires it together and gained `applyConfig`, the one place the four cached settings
  are applied, used by enable and by reload alike.
- The bundled `config.yml` gained `joinMenu`, `navigation.maxDepth` and
  `actions.maxTotalDelaySeconds`.

Nothing in `model/`, `render/`, `action/`, `session/` or `listener/` changed.

## 2. What was read outside the list

The stage list named the documents and the files being changed. Writing a subcommand that calls
code without inventing method names meant reading, but not changing: `service/` (`MenuService`,
`MenuRegistry`, `MutationResult`, `MenuPersistence`), `session/` (`SessionManager`,
`EditSession`, `ViewSession`, `NavigationStack`), `action/ActionExecutor` and the public surface
of `ActionParser` and `Action`, `listener/PlayerInteractListener`, the models `Menu`,
`BoundItem`, `MatchMode`, `MenuType`, `ItemTemplate`, `MenuItem`, `ClickKey`, and
`storage/ItemSerializer` with `render/ItemBuilder`. I stopped and asked before reading them.

Paper signatures were checked with `javap` against `paper-api-26.2.build.125-stable`:
`Commands.literal/argument/register(node, description, aliases)`, `CommandSourceStack`
(`getSender`, `getExecutor`), `LifecycleEvents.COMMANDS` with
`ReloadableRegistrarEvent#registrar`, `ArgumentTypes.player()` and
`PlayerSelectorArgumentResolver#resolve`, `MessageComponentSerializer.message()`,
`ItemStack#editPersistentDataContainer`, `Player#give(Collection, boolean)`,
`Plugin#getPluginMeta`. Everything ARCHITECTURE §8 describes exists as described.

## 3. Verification

`./gradlew clean build` is clean. Four `runServer` runs, driven from the console; there is no
game client this session.

**Covered, and passing:**

| Area | What was run |
|---|---|
| Help | `/mymenu`, `help admin` (every command plus its node), `help command open`, `help command nosuch`. Usage lines are generated from the built nodes: `open <menu> [<player>]`, `create <menu> [<rows> [<title>]]`, `reload [discard-unsaved]`. |
| Read-only | `list`, `info <menu>`, `info <unknown>`, `changelog`, `changelog 2.0.0`, `changelog 9.9.9`, `update`, `save`. |
| Console refusals | `create`, `edit`, `set`, `name` answer "Only a player can use this command."; `open <menu>` and `give <menu>` name the player form. No exceptions, no stack traces. |
| Bad arguments | `create Bad.Name`, `set welcome NONSENSE`, `open welcome nosuchplayer`, `joinmenu nosuchmenu`. |
| `joinmenu` | Sets and clears the key. The rest of `config.yml`, comments included, is byte-for-byte unchanged; `info` then reports "Join menu: yes". |
| Reload | Re-reads settings (debounce, backups, and both new keys), clamps `maxDepth` 99 to 32 with a warning, reports a changed `storage.type` instead of switching, and reloads menus. |
| `/minecraft:reload` | Fires the handler again; `list`, `help` and generated usage still work. |
| Delete | Backup written (`deleted-<menu>-<timestamp>.yml`), menu gone from `menus.yml` and the registry. Cascade over players not observable without a client. |
| Degraded at load | A bad material degrades storage: `list`, `info`, `changelog` work; `delete` and `joinmenu` are refused with the reason; `save` reports instead of writing; fixing the file and reloading clears it. |
| Degraded by a write failure | A non-empty directory at the temp path. Reload retries, fails, refuses, names the discard form. The discard form retries, discards, re-reads, and reports "Back to their last save: alpha". Removing the fault makes editing work again. |
| Tab completion (probe) | Subcommands, menu names with prefix filtering, match modes, `none`, `discard-unsaved`, `help command <name>`, and the `mm` alias. |
| Permission gating (probe) | The subcommand list a sender may use, as nodes are granted and revoked one at a time, including `open` appearing for `open.other` alone. |
| Stage 6 corrections (probe) | An empty action list plays no sound and starts no cooldown — the next click still runs, so nothing was put on cooldown. A trailing `DELAY` leaves the player not pending. `setMaxDepth` clamps and warns. |

The probe was a temporary class inside the plugin, run once from `onEnable`; 15 checks, 15
passed. It has been deleted.

**Not covered:**

- Anything a player sees. `open`, `edit`, `set`, `give`, `name`, the delete cascade's messages
  and inventory closing, and reload closing open menus are all inferred from the API and from
  the code paths, not observed. `create` is player-only, so **no menu was created through the
  command this session**; `create` was exercised only by its refusals.
- Suggestions as Paper actually delivers them to a client. A proxy `Player` cannot be turned
  into a Brigadier source — `PluginVanillaCommandWrapper` needs a real one — so the permission
  filtering above was checked against the predicate the tree uses, not through Brigadier's own
  filtering.
- MySQL, since it does not exist. `storage.type: MYSQL` was only exercised as a warning at
  enable and as reload's refusal to switch.

## 4. The one thing to verify rather than assume

**ARCHITECTURE §8 asks whether Paper computes suggestions off the main thread.** I could not
observe it: without a client nothing drives the real suggestion path, and the probe's own calls
run on whatever thread calls them. What the API does say is that Paper has
`com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent`, whose name and
`PlayerEvent` shape indicate the brigadier suggestion path for a real player is at least
sometimes off-thread. So the question stands open, and the code is written as if the answer
were yes.

Every suggestion provider reads only immutable data: `MenuRegistry#names()`, which returns an
unchanging snapshot published through a volatile field; constant lists (match modes,
subcommand names, `none`, `discard-unsaved`); and `hasPermission`, which Brigadier's own
`requires` predicates already call on that same thread. Nothing reads a session, an open
inventory, storage, or anything else confined to the main thread.

## 5. The two questions

### What does the discard form discard, and what does the admin see?

An admin edits three menus. Each mutation marks the menu dirty; the debounce timer fires once
and hands all three to storage as one batch. Storage puts them into its own image of the file,
flags the image as holding unwritten changes, and tries to write — and the write fails
permanently (say a stray directory where `menus.yml.tmp` goes). The live file is untouched and
still holds the last good save. Storage marks itself degraded, the failure is logged in full,
and online admins with `MyMenu.admin.reload` see "Saving menus failed, so menu editing is
disabled."

From here the admin can still use `list`, `info`, `open`, `help` and `changelog`; the registry
still shows the three edits. Any further mutation is refused with the reason. `/mymenu save`
reports the condition instead of writing.

`/mymenu reload` re-reads `config.yml` and applies it, flushes the writer (nothing pending),
asks storage to write the unwritten image again, and that retry fails too, logging why. Storage
then refuses to load, because loading would discard those three edits. The admin sees:

> Menus were not reloaded. Changes that could not be saved are still held in memory, and
> reloading would throw them away. Saving them was just retried and failed again; the server
> log says why. Fix the cause and run /mymenu reload again, or run /mymenu reload
> discard-unsaved to drop those changes and load menus.yml as it is on disk.
>
> config.yml was re-read and applied.

`/mymenu reload discard-unsaved` does everything the ordinary form does — including the retry,
so anything that has become writable in the meantime is still written — and then, only if the
retry failed, tells storage to forget that its image differs from the file, drops the writer's
pending batch so the timer cannot bring those changes back, and loads `menus.yml` as it stands.

What is discarded is exactly the difference between what storage holds and what the file holds:
those three edits, and nothing else. Menus untouched since the last good save are unaffected;
`config.yml` is not involved; the delete backup of anything deleted in that window stays on
disk, so a deletion that was rolled back is still recoverable by hand.

Afterwards the admin sees:

> Reloaded config.yml and 12 menu(s).
> Unsaved changes were discarded. Back to their last save: kits, shop, warps.

Open menus were closed, edit sessions ended, and pending action sequences cancelled, as in any
reload. Storage is no longer degraded — the load succeeded and the image matches the file — so
editing works again. If the fault is still there, the next edit's write fails and the whole
cycle starts over, which is the honest outcome: the plugin cannot save to a disk that will not
take writes.

### What happens to an admin mid-prompt or with a menu open when `/bukkit:reload` builds a second instance?

The old instance's `onDisable` runs first. `SessionManager#closeAll` closes every open
MyMenu inventory, clears the view sessions, and cancels every pending chat prompt's timeout
task; the writer then flushes and storage's thread is stopped and waited for. So the admin's
menu closes before the new instance exists, and the chat prompt they were in the middle of
answering is gone — their next chat message is an ordinary chat message. Nothing tells them
why, beyond the menu shutting; that is worth a message at stage 8, when prompts carry what they
are for.

A pending action sequence is a scheduled Bukkit task, and Bukkit cancels a disabled plugin's
tasks, so a delayed `$give` three seconds after a click does not arrive. The executor's own
entry for that player dies with the instance.

The new instance then loads in a new classloader with empty registries and sessions, re-reads
both files, and registers its commands into the dispatcher Paper rebuilds. Because there is no
static state anywhere, the two instances share nothing; because `onDisable` stopped storage's
executor and waited for it, no thread from the old copy can still be writing `menus.yml` when
the new copy starts writing it (ARCHITECTURE §7.4, §8). The admin sees a closed menu and, in
the log, Paper's own warning about `/bukkit:reload`. Unsaved work is not lost: the flush writes
the pending batch before the executor stops, and retries a failed write once.

Two smaller edges. A reload chain of my own that is mid-flight when `onDisable` runs never
completes, because the main-thread executor drops tasks once the plugin is disabled — the new
instance loads from the file anyway, so nothing is lost beyond the reply the admin was waiting
for. And an admin whose `joinmenu` write is in flight may see the write land but not the reply.

## 6. Divergences and things for the user

1. **SPEC §3.5's "cancel pending `MENU` actions" is not implemented** (DECISIONS #92). The
   cascade needs a method in `action/`, which this stage was scoped out of. The smallest fix is
   to have `ActionExecutor` keep the remaining actions beside each pending task and add:
   ```java
   /** Cancels pending sequences whose remaining steps open {@code menuName}. */
   public void cancelTargeting(String menuName);
   ```
   `DeleteCommand.cascade` would call it. Until then a stale `MENU` action tells the player the
   menu does not exist.
2. **`/mymenu update` answers that it cannot check yet** (DECISIONS #90). I recommended leaving
   it out until stage 10 and then registered it anyway, because your list of what must land
   named it and asked for it to be driven from the console. Say the word and it goes.
3. **`PluginConfig` reads two keys beyond the two the stage named**, `joinMenu` and
   `storage.type` (DECISIONS #91), and the default `config.yml` gained three keys. Nothing opens
   a menu on join yet, so `joinMenu` is a setting that can be set but not yet felt.
4. **`DebouncedMenuWriter` gained more than the discard form needed.** `flushAsync` exists
   because `flush` blocks the main thread and rule 2 allows that only in `onDisable`; `save` and
   reload both need a non-blocking version. `MenuStorage.retryUnwritten` is likewise new
   (DECISIONS #87).
5. **A menu named `none` cannot be made the join menu**, because `none` is how the command
   clears the setting. SPEC §3.2 allows the name. Nothing else treats it specially.
6. **`give` hands out one item, whatever the stack size was when it was bound**, and the tag it
   carries opens the menu even after `unset`, because `PlayerInteractListener` treats the tag as
   the whole answer. `unset` says so in its reply. If SPEC §7 means a dispensed copy to stop
   working when the binding goes, the listener needs to check the binding too — which is a
   change in `listener/`.
7. **`set` strips the dispensed-item tag before capturing.** Binding a copy that came from
   `give` would otherwise store the tag inside the prototype and force the item into the encoded
   form.
8. **Reload applies `config.yml` even when the menu reload is refused**, and says so. The
   settings have to be applied before the load, because the delay cap is enforced while parsing.
9. **`create` is player-only**, per SPEC §3.1, so a server owner cannot make a menu from the
   console. That is what SPEC says; it is also why this session could not create one.

## 7. Stage 8 hand-off, in one paragraph

The editor is a mutation path that does not go through the command tree, so it must repeat the
three checks in `CommandTree#refuseWhileLocked` — degraded, not loaded, reload running — or a
click will change a menu the reload is about to replace (DECISIONS #88). `EditCommand` already
opens the `EDIT` view through `SessionManager#open`, so stage 8 starts from an open edit
inventory and an `EditSession` whose only content is its prompt. Prompt text and timeouts belong
next to `EditSession.PendingPrompt`; the delete cascade already clears prompts and messages the
admin, and reload ends them, so both paths only need the prompt's purpose added. Action input
goes through `ActionParser.parseList`, never anywhere else (#85), and the property editor must
refuse an opaque item that will not deserialise (#74). `DECISIONS.md` continues from #93.

## Addendum (2026-09-19, after review)

Sections 1 to 6 record the stage as it was built. The review changed five things; the body above
is left as written.

- **`none` is refused as a new menu name.** `CommandTree` refuses it when validating a new name
  and says why, which answers item 5 of section 6. The reservation is deliberately command-layer
  only: it belongs to the `joinmenu` argument, where `none` is a sentinel, not to menus, so
  `Menu` still accepts the name and a hand-written menu called `none` works normally
  (DECISIONS #93).
- **`create` is no longer player-only** (SPEC §3). The console can create menus; the menu then
  records no author, and the "open it for editing" hint is only shown to a player, who can act
  on it. The spec, the generated help and the `help command create` page follow automatically.
  Section 3's note that no menu was created by command no longer holds: one was, from the
  console, and deleted again.
- **The pending-`MENU` cancellation in section 6 item 1 is dropped, not deferred.** SPEC §3.5 no
  longer asks for it and `cancelTargeting` is not to be written; the reasoning is in the
  correction to DECISIONS #92.
- **The suggestion-threading question in section 4 is closed.** ARCHITECTURE §8 now records
  `AsyncPlayerSendSuggestionsEvent` as sufficient evidence, together with the fact that every
  provider reads only immutable data.
- Accepted as built, with no change: `/mymenu update` stays registered (#90); `PluginConfig`
  reading `joinMenu` and `storage.type` (#91); `flushAsync` and `retryUnwritten`, which are rule
  2 compliance rather than scope creep; reload applying `config.yml` even when the menu reload is
  refused; and `set` stripping the dispensed-item tag before capturing.

One new ruling, recorded in `NOTES.md` for stage 8: a tagged item must open its menu only while
that menu still has a bound item. Section 6 item 6 asked the question; the answer is that
`give`'s copies outliving `unset` is a bug, and the fix belongs in `listener/`.
