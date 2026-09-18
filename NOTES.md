# NOTES — session state (overwritten each session)

## Current stage
Stage 1 complete: Gradle build, `paper-plugin.yml`, loader, empty plugin. Next: stage 2
(model classes).

## Verified working (2026-09-18, Paper 26.2-124, Temurin Java 25.0.4.1, Gradle 9.7.1)
- `./gradlew build`: the jar contains `paper-plugin.yml`, `paper-libraries.json` and `CHANGELOG.md`.
- `./gradlew runServer`: MyMenu loads, enables and disables with no warnings of its own.
- The loader resolves HikariCP 7.1.0 and mysql-connector-j 26.7.0 through Paper's Central
  mirror (Google's storage mirror).
- The `/reload` behaviour is recorded in DECISIONS #59. Bukkit reload is not disabled by
  `COMMANDS` handlers.

## Known broken / open
- Nothing broken.
- mysql-connector-j pulls in `protobuf-java` (~1.8 MB, X DevAPI only). Consider
  excluding it at stage 11.
- `InventoryCloseEvent.getReason()` / `Reason.OPEN_NEW` is still unverified (needed at stage 5).
- Whether Paper computes suggestions off the main thread is still unverified (needed at stage 7).

## Needs the user's input
- Paper 26.3 is alpha-only today. Stage 1 targets 26.2 stable (DECISIONS #58). Say if you
  would rather track 26.3.
