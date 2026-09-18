# NOTES — session state (overwritten each session)

## Current stage
Stage 2 complete (not committed, awaiting review): model classes, `MenuRegistry`,
`MenuService`. Next: stage 3 (`ItemSerializer`, `YamlMenuStorage`, the storage interface).

## Verified working (2026-09-18, Paper 26.2-124, Temurin Java 25.0.4.1, Gradle 9.7.1)
- `./gradlew build` is clean with no warnings, stage 2 included.
- Checked in the 26.2 API jar with javap: `ClickType` constants (no `OTHER`), `Sound` is an
  interface, `Material` is still an enum with `isItem()`/`isAir()`, and
  `ItemStack#serializeAsBytes` / `ItemStack.deserializeBytes(byte[])` exist. JSpecify
  `@Nullable` is on the API compile classpath.
- Stage 1's server-start result still stands. Stage 2 adds nothing that loads at runtime:
  `MenuService` is not constructed in `MyMenu` yet, because no `MenuPersistence`
  implementation exists. `runServer` was **not** re-run.

## Known broken / open
- Nothing broken. Nothing in stage 2 has run, only compiled; there are no tests (SPEC §15.4).
- `MenuPersistence` and `ItemSerializer` are interfaces with no implementation (stage 3).
  `Action` is an empty interface (stage 6).
- Stage 3 must pick the YAML spelling for click sounds: `UI_BUTTON_CLICK` as in SPEC §5.3,
  or the key `minecraft:ui.button.click`. The model stores a key (DECISIONS #62).
- Stage 6 must map Bukkit's keyless clicks (`CONTROL_DROP`, `SWAP_OFFHAND`, the window-border
  clicks, `CREATIVE`, `UNKNOWN`) onto `ClickKey`.
- Registry loading (`replaceAll` for load and reload) is not written. It arrives with storage
  in stage 3.
- mysql-connector-j pulls in `protobuf-java` (~1.8 MB). Consider excluding it at stage 11.
- `InventoryCloseEvent.getReason()` / `Reason.OPEN_NEW` is unverified (needed at stage 5).
- Off-main-thread suggestions (stage 7) no longer matter for registry safety (DECISIONS #61),
  but still need checking for anything else a suggestion provider reads.

## Needs the user's input
- Review stage 2, especially DECISIONS #61 (immutable models, which diverges from
  ARCHITECTURE §3) and #63 (layout changes that would strand items are refused).
- Settled 2026-09-18: the user confirmed the project stays on Paper 26.2 stable (DECISIONS #58).
