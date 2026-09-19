# NOTES — session state (overwritten each session)

## Current stage
Stage 3 is committed (`7fea4e2`). Stage 4 (`MenuHolder`, `MenuRenderer`, `ItemBuilder`, and the
`ItemSerializer.capture` change) has **not started**. It waits for the user's go-ahead.
SPEC.md and ARCHITECTURE.md were revised after stage 3 and were re-read in full on 2026-09-18.

## Verified working (2026-09-18, Paper 26.2-124, Java 25, Gradle 9.7.1)
- Everything listed for stage 3 in commit `7fea4e2` and DECISIONS #66–#68: capture chooses the
  form, serialized items round-trip, debouncing coalesces, the shutdown flush writes, the backup
  policy holds, a malformed file degrades without being touched, and a failed write leaves the
  live file intact.
- No code has changed since that commit.

## Known broken / open
- Nothing known broken. There are still no automated tests (SPEC §15.4).
- **Stage 4, italic (DECISIONS #70, SPEC §10.2).** No longer an open stage 9 question. Both
  halves land in stage 4:
  - the render half: readable text renders with italic explicitly disabled unless the text
    sets it;
  - the capture half: `ItemSerializer.capture` builds its readable candidate the same way.
  If only the render half lands, items carrying `italic: false` keep going to bytes and nothing
  visibly breaks. Stage 9's `TextService` inherits this rule; it must not redefine it.
- **Stage 4, blobs.** The renderer must handle an opaque blob that fails to deserialise. Blobs
  are not validated at load (DECISIONS #67).
- **Stage 6, actions.** `ActionCodec.NONE` makes any stored action a load error, which degrades
  storage. `ActionParser` must implement `ActionCodec`. Its `write` must return a fresh map each
  call, or SnakeYAML emits `&id001` aliases (see `MenuYamlFormat.dump`).
- **Stage 7, reload.**
  - Reload must flush first.
  - It must refuse if `loadAll()` fails with "changes that could not be written".
  - It must also call `setBackupPolicy` / `setDebounce` with the re-read config.
  - Storage's `flush()` blocks and is for `onDisable` only, so reload needs a non-blocking
    flush.
  - **The discard form (DECISIONS #71, SPEC §12):** `/mymenu reload` needs an explicit form that
    drops unwritten changes and re-reads from disk, and the ordinary refusal must name it.
    `YamlMenuStorage` has no way to do that yet. Today `loadAll()` refuses while the image is
    flagged unwritten, and nothing clears the flag except a successful write. Stage 7 must add
    an explicit discard to `MenuStorage`, for example `discardUnwritten()` or a
    `loadAll(boolean discard)`. It must also cancel `DebouncedMenuWriter`'s pending batch, or
    the discarded changes come back on the next timer.
- **Stage 9:** `TextService` must follow #70's italic rule, which is already decided (see
  above).
- **Stage 10:** the write-failure notice to admins is a hard-coded string (TODO in
  `DebouncedMenuWriter`).
- **Stage 11:** MySQL always stores bytes, so it needs a readable-to-`ItemStack` conversion. The
  one in `ItemSerializer` is private and does no wildcard handling. Stage 4's `ItemBuilder` may
  make this easier.
- Carried over: map Bukkit's keyless clicks onto `ClickKey` (stage 6; ARCHITECTURE §3 now gives
  the mapping). Verify `InventoryCloseEvent.getReason()` / `Reason.OPEN_NEW` (stage 5). Consider
  excluding `protobuf-java` from the MySQL driver (stage 11).

## Needs the user's input
- A go-ahead for stage 4.
- Three document inconsistencies found in the re-read, all left unedited:
  1. ARCHITECTURE §7 has a stray code fence at line 334, after the "Since models are
     immutable…" paragraph. It opens a code block that swallows the `YamlMenuStorage` and
     `MySqlMenuStorage` paragraphs when the file is rendered.
  2. ARCHITECTURE §8's "Resolved at stage 2" says "immutable models plus a **concurrent map**".
     §3.1 and DECISIONS #61 say a copy-on-write snapshot behind a `volatile` field, and §3.1
     explicitly argues against `ConcurrentHashMap`.
  3. The substitution order conflicts with itself. SPEC §10 and ARCHITECTURE §4 step 3 / §10
     say "wildcards → PlaceholderAPI → colour translation". SPEC §10.1 and hard rule 11 say
     substitution happens *after* parsing. Taken literally, the first order parses substituted
     values as `&` codes. Hard rule 11 should win: parse the admin text first, then insert
     substituted values as plain-text data. This is stage 9's problem, but it shapes the text
     seam stage 4 leaves (see the confirmation message).
