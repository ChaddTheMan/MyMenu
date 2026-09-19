# Stage 3 report — storage

Status: **done, not committed.** Stage 4 has not been started. This file is for your review and
is not meant to be committed. The lasting record is in DECISIONS #65–69 and in `NOTES.md`.

## What was built

| File | Role |
|---|---|
| `storage/MenuStorage` | Backend interface: `loadAll`, `saveAll`, `delete(Menu)`, `isDegraded`, `flush`, `close` |
| `storage/YamlMenuStorage` | Owns the single `MyMenu-Storage` thread. Holds the file image and does the write order |
| `storage/MenuYamlFormat` | YAML ↔ model, with the per-menu and per-slot catch boundaries |
| `storage/BackupWriter` | Save backups (rate-limited and pruned) and delete backups (never pruned) |
| `storage/DebouncedMenuWriter` | Implements `MenuPersistence`; batches changes and flushes on disable |
| `storage/ItemSerializer` | Bytes via `serializeAsBytes`/`deserializeBytes`, plus `capture()`, which picks readable or serialized |
| `storage/ActionCodec` | The seam for stage 6. `NONE` makes any stored action a slot error |
| `config/PluginConfig` + `config.yml` | Debounce and backup settings, read off the main thread |
| `MyMenu` | Builds storage, the writer and `MenuService`; loads asynchronously; flushes and closes on disable |

Stage 2 changes: `MenuPersistence.markDirty(String)` → `markDirty(Menu)`. `MenuService` has a
load gate (`NOT_LOADED`) and `replaceAll`. `MenuRegistry` has `replaceAll`.

## Your two questions

### 1. How does the writer decide an item needs the opaque form?

**It doesn't.** The writer writes whichever `ItemTemplate` variant it is given; the sealed type
*is* the decision. The decision is made once, when a real `ItemStack` becomes a template, in
`ItemSerializer.capture`. The exact check:

> Build the readable candidate from the stack: material, amount, `CUSTOM_NAME` and `LORE`
> converted to `&`-code strings with Adventure's legacy serializer (`&`, `&#rrggbb`), and
> `ENCHANTMENT_GLINT_OVERRIDE == true` as `glow`. Rebuild an `ItemStack` from that candidate.
> Keep the candidate **iff `rebuilt.equals(original)`**. Otherwise store the bytes.

`ItemStack#equals` compares type, amount and every data component. So anything the readable
form cannot hold makes the two unequal and sends the item to bytes: enchantments, head
textures, custom model data, a component that doesn't exist yet, a font, an explicit
`italic: false`, or a literal `&c` in a name. The check fails closed. A whitelist of "supported
components" would fail open and go stale every release.

Two extra rules:
- A name or lore line starting with `<!mm>` forces bytes, since it would otherwise be parsed as
  MiniMessage at render time.
- A glint override of `true` on an opaque item moves out of the bytes into `glow`, so that the
  editor's glow toggle still works on it.

**One dependency to flag:** if stage 9 renders readable names non-italic by default, `capture`
must apply the same rule. Otherwise every plugin-made item with `italic: false` is stored as bytes
when it didn't need to be. That costs readability, never data (DECISIONS #66).

### 2. What happens if a write fails partway, after the backup copy but before the atomic move?

- **The live file is intact.** It is never opened for writing, only replaced by an atomic move.
  So it still holds the last good save.
- **The backup is harmless.** It is a real copy of that unchanged live file, so it is merely
  redundant.
- **The temp file is deleted if possible.** A leftover one is truncated by the next write and
  never read.
- **Storage goes degraded.** `MenuService` refuses further edits, the cause is logged, and online
  holders of `MyMenu.admin.reload` get a notice.
- **The edits are not thrown away.** They stay in storage's in-memory image, flagged unwritten.
  `onDisable`'s flush retries the write once. If that also fails, the log says plainly that the
  changes are lost and `menus.yml` keeps its last good save.
- **Re-reading is blocked.** `loadAll()` refuses to run while the image is unwritten, because
  re-reading would silently discard those edits. Stage 7's reload must respect this.
- **A failed backup counts as a failed write,** and the live file is not replaced. A failed
  delete backup leaves the menu in the file.

(DECISIONS #68)

## Verification (all on Paper 26.2-124)

A temporary harness class drove the mutations, since there are no commands yet. **It has been
deleted.** The final jar contains no harness code, and the final `runServer` ran without it.

| Check | Result |
|---|---|
| `./gradlew build` | Clean, no warnings |
| Real `menus.yml` (SPEC §5.3 example minus actions) | `Loaded 3 menu(s)`; no writes |
| Console `stop` | `Disabling` → `Menu storage stopped` → JVM exits; no hang |
| 3 edits in one tick | One write, 2 s later |
| Edit then immediate `shutdown()` | Written by `onDisable` flush |
| Round trip | Serialized sword (Sharpness 3) survived disk → load → deserialise. Empty `LEFT: []` kept |
| `minIntervalSeconds: 300` | No second save backup within the interval |
| `keep: 3`, interval 0, six batches | Six writes, three backups kept. Delete backup and two admin files untouched |
| Delete | `deleted-hopperdemo-….yml` is pasteable YAML |
| **Malformed file** (bad material, `rows: 9`, slot 40 in 1 row, + 9 other faults) | 12 problems logged with menu and slot. The 4 surviving menus loaded exactly their good slots. Edit and create → `STORAGE_DEGRADED`. File byte-identical, no backup |
| Duplicate YAML key | Nothing loaded, degraded, file byte-identical, error names the line |
| First run, no files | Default `config.yml` written; `menus.yml` not created until needed |
| Write failure (non-empty dir at `menus.yml.tmp`) | Both attempts failed; live file byte-identical; later edits refused; clean stop |
| Write failure (empty dir) | Cleanup removed it; shutdown retry saved the edits |

**Bug found and fixed during verification:** SnakeYAML wrote `LEFT: &id001 []` /
`OTHER: *id001`, because `List.of()` is a shared singleton and SnakeYAML 2.2 has no option to
stop aliasing. The writer now emits fresh collections everywhere. Stage 6's `ActionCodec.write`
must also return a fresh map; this is noted in its Javadoc.

## Where I diverged from the documents

1. **The `MenuStorage` interface** (#65). There is no `save(Menu)`; `delete` takes a `Menu`; and
   YAML keeps its own image rather than reading the registry. The debounce timer is a fixed
   window from the first change, not reset by later ones, so constant editing still saves.
2. **Menus are built on the main thread** (#67). `Material.isItem()` is a registry lookup on
   26.2. Only file I/O and YAML parsing run on the storage thread.
3. **Serialized blobs are not validated at load** (#67). They are carried verbatim, so a blob
   that can't be deserialised is kept, not dropped. Stage 4 has to cope with it.
4. **Unknown keys degrade** (#67). The entry is kept, but saves would drop the key.
5. **Config is read off-thread,** not via `getConfig()` (#69). This avoids a second exception
   to hard rule 2. The default `config.yml` contains only keys that are read so far.
6. **Load gate** (#69). Menus now arrive after `Done`, so `MenuService` refuses edits with
   `NOT_LOADED` until then.

## Needs your decision

- **SPEC §5.1 is wrong:** it has two top-level `storage:` keys. I merged them in the real file
  but did not edit SPEC.
- **ARCHITECTURE §7's interface block is now out of date** (#65). I did not edit it, per "a plan,
  not scripture". Tell me if you want both documents updated to match.
- **Delete-backup failure behaviour** (#68): the menu stays in the file and comes back on the next
  load. The alternative is to delete it anyway without a backup. I chose restore-over-lose;
  confirm.
