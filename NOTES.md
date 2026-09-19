# NOTES — session state (overwritten each session)

## Current stage
Stage 4 (`render/`: `MenuHolder`, `MenuRenderer`, `ItemBuilder`, `ViewMode`, `TokenReplacer`,
plus the `ItemSerializer.capture` change) is **written and verified but not committed**. It waits
for the user's review. Stage 5 has not started. Full report: `STAGE4-REPORT.md`.

## Verified working (2026-09-19, Paper 26.2-124, Java 25, Adventure 5.2.0)
- `./gradlew clean build` is clean. `runServer` starts and stops cleanly with a `menus.yml` of
  four menus (`run/` holds a `probe` menu with a real serialized item and two corrupt ones).
- A temporary probe (now deleted) passed 45 checks with proxy `Player`s, no game client:
  - holder fields;
  - VIEW hiding, fallback and no compaction;
  - EDIT markers, with and without existing lore;
  - glow on both shapes;
  - the italic rule, including `&o` still winning;
  - the rendered-icon key on every item;
  - model and revision unchanged by rendering;
  - hopper sizing;
  - a hostile token value staying literal text;
  - capture round trips;
  - MiniMessage;
  - corrupt blobs (garbage and truncated) as barriers with the rest of the menu intact;
  - `menus.yml` byte-identical afterwards.
- **Not verified by eye.** Nobody has seen a rendered menu in a real client yet. Glint, italics
  and lore layout were checked as data components, not visually. The first chance is stage 5,
  when menus can open.

## Known broken / open
- Nothing known broken. There are still no automated tests (SPEC §15.4).
- **Stage 5:** `MenuRenderer` is not wired into `MyMenu` yet; construct one renderer per plugin
  instance (its log-once set is per instance). Read the menu and its revision in the same tick.
  Verify `InventoryCloseEvent.getReason()` / `Reason.OPEN_NEW`.
- **Stage 6:** decide whether clicking an unreadable-item barrier runs the slot's actions
  (DECISIONS #74). Clicking a hidden slot must also be refused: the click handler must re-check
  the view permission rather than trust that an empty slot has no model item. Map Bukkit's keyless
  clicks onto `ClickKey`. `ActionCodec.NONE` makes any stored action a load error; `ActionParser`
  must implement `ActionCodec`, with `write` returning a fresh map each call.
- **Stage 7, reload:** flush first; refuse on unwritten changes; re-apply
  `setBackupPolicy`/`setDebounce`; needs a non-blocking flush; add the discard form (#71): an
  explicit discard on `MenuStorage` that also cancels `DebouncedMenuWriter`'s pending batch.
- **Stage 8:** the property editor must refuse to edit an opaque item that does not deserialise
  (#74).
- **Stage 9:** implement `TokenReplacer` per viewer with `Component#replaceText` (#72, #75);
  replace `viewer -> TokenReplacer.NONE`. Command values take a separate string path with
  sanitisation. `ItemBuilder` already owns the italic rule (#70), so `TextService` must reuse it
  rather than redefine it.
- **Stage 10:** hard-coded English in `MenuRenderer` (marker, barrier) and in
  `DebouncedMenuWriter` (write-failure notice) moves to `messages.yml`.
- **Stage 11:** readable → `ItemStack` for MySQL is now `ItemBuilder.build(descriptive,
  TokenReplacer.NONE)`. Consider excluding `protobuf-java` from the MySQL driver.

## Needs the user's input
- Review and commit stage 4.
- ARCHITECTURE §4 still says `render(Menu, Player)`, and §7.5 still implies `ItemSerializer`
  deserialises. The code differs, as DECISIONS #73 and #75 record. The documents were not edited.
- Behaviour change to accept or reject (DECISIONS #73): anvil-renamed items now capture as
  bytes rather than readable.
