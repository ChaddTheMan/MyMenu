# NOTES — session state (overwritten each session)

## Current stage
Stage 3 complete (not committed, awaiting review): `ItemSerializer`, `MenuStorage`,
`YamlMenuStorage`, `MenuYamlFormat`, `BackupWriter`, `DebouncedMenuWriter`, `ActionCodec` seam,
`PluginConfig` + default `config.yml`, and `MenuService` wired up in `MyMenu`. Next: stage 4
(`MenuHolder`, `MenuRenderer`, `ItemBuilder`), and only after the user signs off on stage 3.

## Verified working (2026-09-18, Paper 26.2-124, Java 25, Gradle 9.7.1)
- `./gradlew build` is clean with no warnings. `runServer` starts, loads `menus.yml` after `Done`,
  and stops cleanly from the console `stop`; the log shows "Menu storage stopped".
- Verified at runtime with a temporary harness, now deleted:
  - capture: readable vs serialized chosen as DECISIONS #66 says;
  - serialized items survive the trip to disk and back;
  - three same-tick edits produce one write;
  - an edit made just before shutdown is written by the flush in `onDisable`;
  - `minIntervalSeconds` and `keep` behave as configured, and delete backups and admin files in
    `backups/` are never pruned;
  - the delete backup can be pasted back into `menus.yml`;
  - a malformed file degrades without aborting the load (DECISIONS #67), refuses edits, and is
    left byte-identical;
  - a forced write failure (DECISIONS #68) leaves the live file intact.
- First run with no files writes the default `config.yml` and creates no `menus.yml` until
  something is saved.

## Known broken / open
- Nothing known broken. There are still no automated tests (SPEC §15.4).
- **Actions:** `ActionCodec.NONE` makes any stored action a load error, which degrades storage.
  Stage 6's `ActionParser` must implement `ActionCodec`. Its `write` must return a fresh map each
  call, or SnakeYAML emits `&id001` aliases (see `MenuYamlFormat.dump`).
- **Stage 4:** the renderer must handle an opaque blob that fails to deserialise. Blobs are not
  validated at load (DECISIONS #67).
- **Stage 7:** reload must flush first. It must abort if `loadAll()` fails with "changes that
  could not be written". It must also call `setBackupPolicy` / `setDebounce` with the re-read
  config. Storage's `flush()` blocks and is for `onDisable` only, so reload needs a
  non-blocking flush.
- **Stage 9:** `ItemSerializer`'s `&` conversion must match `TextService`'s, especially the
  italic default (DECISIONS #66).
- **Stage 10:** the write-failure notice to admins is a hard-coded string (TODO in
  `DebouncedMenuWriter`).
- **Stage 11:** MySQL always stores bytes, so it needs a readable-to-`ItemStack` conversion. The
  one in `ItemSerializer` is private and does no wildcard handling.
- Carried over: map Bukkit's keyless clicks onto `ClickKey` (stage 6). Verify
  `InventoryCloseEvent.getReason()` / `Reason.OPEN_NEW` (stage 5). Consider excluding
  `protobuf-java` from the MySQL driver (stage 11).

## Needs the user's input
- Review stage 3, especially DECISIONS #65 (the interface differs from ARCHITECTURE §7), #66 (the
  capture check) and #68 (write-failure semantics).
- ARCHITECTURE §7's interface block and SPEC §5.1's duplicate `storage:` key (DECISIONS #69) are
  now out of date or wrong. They were left unedited for the user to decide.
- Test data in `run/plugins/MyMenu/` is the SPEC §5.3 example with actions removed. It is
  git-ignored.
