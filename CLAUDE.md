# CLAUDE.md — Project instructions for MyMenu

## What this project is

A ground-up rewrite of MyMenu, a Minecraft chest-GUI menu plugin originally released for
Bukkit 1.8.1 in January 2015 (version 1.0.4.3). The new version targets modern Paper and
carries **no backward compatibility** of any kind.

- **Author:** ChaddTheMan
- **License:** GPL-3.0 (every source file gets the standard GPLv3 header)
- **Repository:** https://github.com/ChaddTheMan/MyMenu
- **Root package:** `me.chaddtheman.mymenu`
- **Version:** 2.0.0 (semantic versioning)

## How work reaches you

The plugin is planned outside this repository, in planning conversations with the user. Each
session here is one **stage**, started from a **stage prompt** the user pastes in.

- **Your stage prompt defines your scope.** Implement only what it names. Its reading list is
  the whole reading list: read exactly the files and sections it names, and nothing else unless
  you need a class's API to call it.
- **`SPEC.md` describes the finished 2.0.0, not the current code.** Large parts of it are not
  built yet, and later revisions replace it. A difference between `SPEC.md` and the code is
  **not** a bug for you to fix unless your prompt names it. Never "fix" code to match a part of
  the spec your prompt did not name; report the difference instead.
- `SPEC.md` is exported from the planning document. **Do not edit it.** If it is wrong or
  impossible, say so in your report.
- Where your stage prompt and `SPEC.md` disagree, the prompt wins: it carries rulings made since
  the export. If the prompt or `SPEC.md` marks an item PROPOSED, OPEN or BACKLOG, do not build
  it. If your work depends on one, stop and ask.
- `ARCHITECTURE.md` describes structure. `DECISIONS.md` records why the code is shaped as it is.
  Read the parts your prompt names.
- **Do not read** `legacy/` (except as below), `run/` (beyond using it as the test server),
  `build/`, `AUDIT.md`, or earlier stage reports, unless your prompt names a section.

## The legacy code

`legacy/` holds the decompiled 1.0.4.3 source. It is **read-only reference**, never
compiled, never copied, and read only when a prompt asks "what did the original do here?" Do
not treat it as a template: it predates the flattening, uses removed APIs throughout, and
contains the bugs catalogued in `ARCHITECTURE.md` §12.

Bug-for-bug fidelity is explicitly **not** a goal.

## Working agreement

### Pace and explanation

Roughly 80% efficiency, 20% teaching.

- **Mechanical work** — boilerplate, getters, YAML plumbing, repetitive conversions —
  proceeds without commentary. Just do it.
- **Architectural work** — the model/view split, the storage abstraction, async
  boundaries, the Brigadier tree, session lifecycle, the action executor — gets a short
  explanation of the reasoning *before* the code. The user is learning Java and these are
  the parts worth learning from.
- Do not explain basic Java syntax unless asked.
- Comments explain **why**, not **what**. No comment restating the line below it.

### Verification, not assumption

The user's knowledge and mine both thin out around current Paper APIs.

- **Check signatures against the Paper API jar for the target version** (`javap` on the jar in
  the Gradle cache) before using any Paper-specific API, particularly Brigadier, the lifecycle
  event manager, `PluginLoader`, and item serialisation. Tutorials and my own memory are both
  unreliable here.
- If an API named in `ARCHITECTURE.md` or a prompt does not exist as described, say so and
  propose an alternative rather than inventing a workaround.
- Never fabricate a method name to make something compile.

### Honesty

- If a decision in `SPEC.md`, `ARCHITECTURE.md` or the prompt turns out to be wrong or
  impossible, say so directly. These documents are a plan, not scripture.
- Do not report something as working without having run it. Say what was observed and what was
  only inferred from the code.
- Flag uncertainty explicitly rather than hedging vaguely.

## Hard rules

1. **No static mutable state.** No static registries, no static session fields. This
   caused the worst bug in the original.
2. **No blocking I/O on the main thread while the server is running.** File and database
   access is async and returns a `CompletableFuture`. Hop back to the main thread before
   touching any Bukkit API. Two exceptions exist; do not add a third.
   (a) `onDisable` writes any pending debounced changes, then waits for storage's own executor
   with a bounded timeout. It starts no other work.
   (b) When the plugin loads, before the server ticks, Paper's library loader may download the
   MySQL libraries (HikariCP and the driver).
3. **The model is never the view.** A `Menu` is data. An `Inventory` is a rendering. Never
   store an `Inventory` on a model object, and never read menu state back out of an open
   inventory.
4. **Identify menus by `InventoryHolder`,** never by inventory title.
5. **Never op a player,** temporarily or otherwise. Elevation uses a
   `PermissionAttachment` removed in a `finally` block.
6. **Never write the live data file in place.** Copy the live file to `backups/`, write a
   temp file, then atomically move the temp over the live file — in that order. Rotating
   the live file out first leaves a window with no live file.
7. **Listeners hold no per-event state in fields.** Locals and parameters only.
8. **Session validity is derived, not tracked.** A view session counts as valid only if
   the player currently has a `MenuHolder` inventory open or is in a known transient
   state. Never let a map entry alone be authoritative — that is what locked players out
   of 1.x. Close handling branches on `InventoryCloseEvent.getReason()`; unconditional
   cleanup would destroy navigation stacks and chat prompts. Quit cleanup *is*
   unconditional, and no path may leave a session behind on an exception.
9. **Inside an event handler, do only what decides the event's outcome** (cancel or allow,
   and the checks that decide it) **and bookkeeping that depends on that moment** (session
   state on close, cleanup on quit). Opening or closing a screen, or running actions or
   commands, is scheduled for the next tick.
10. **Never silently drop data.** A parse failure or a failed write puts storage into a
   degraded state. `MenuService` then refuses **mutations before they reach the model** —
   never at the storage boundary, where the model has already changed and the edit is lost
   on the next reload. The same gate also refuses while menus are still loading or a reload is
   running. Every path that changes a menu, commands now and the editor later, goes through that
   one gate; none checks for itself. Read paths keep working.
11. **Substitution happens after parsing.** Substituted values are never re-parsed as
   MiniMessage, and values entering command strings are sanitised. Placeholder output and
   nicknames are player-controlled; this is injection defence.
12. **No legacy support.** No 1.x commands, no 1.x data migration, no material alias
   table, no damage-value handling.

## Documentation during implementation

Five artefacts, five audiences. Keep them separate; a file that duplicates another is a
file nobody reads.

### `DECISIONS.md` — why the code is shaped this way

Audience: the user in six months, and a later session with no context.

Entries 1 to 57 are pre-implementation. Continue the numbering and the template. **The bar
is high:** an entry goes in only when

- the implementation diverges from `SPEC.md` or `ARCHITECTURE.md`, or
- something was decided that neither document covers, or
- a documented claim turned out to be wrong (version, API, behaviour).

Do **not** restate what the documents already say, and do not record routine
implementation choices. The test is whether someone reading the code later would ask "why
is it like that?" Expect roughly two to five entries per stage, not per session.

The file is **append-only**. When an entry corrects or supersedes an earlier one, add a dated
correction to the original rather than editing it silently — see entries 15, 18 and 25 for the
pattern.

### `CHANGELOG.md` — what changed, for server owners

Bundled into the jar as a resource because `/mymenu changelog` reads it. Keep-a-Changelog
format with an `Unreleased` section at the top. One line per user-visible change, in plain
language, no internal terminology.

If a change cannot be described to a server owner in one sentence, that is a signal the
feature is confused, not a signal to write two sentences.

### `STAGE<n>-REPORT.md` — what this stage did, for the review

Written at the end of every stage, in the repository root. What was built, what was verified
and how, what was only inferred, every question the prompt asked with its answer, and anything
that diverged from the prompt. **Anything long goes in a file, not the terminal**: the user's
terminal truncates.

### `NOTES.md` — cross-session state

Short, **overwritten each session, never appended**; resolved items are pruned. Exactly four
things: current stage, what is verified working, what is known broken, what needs the user's
input. This is what makes resuming after a few days cheap.

### `paused_session.md` — the mid-stage handoff

If a stage has to stop partway, write `paused_session.md` (git-ignored): where you are, what is
done, what is next. Read it when resuming; delete it when the stage finishes.

### Architectural explanations go in the code

The user set the teaching dial at roughly 80/20, with architectural reasoning explained
before it is written. Those explanations are lost when the session closes.

So: **when you explain something architectural, put the explanation in a comment block at
the head of the relevant class**, not only in chat. A paragraph atop `ActionExecutor` on
why execution cannot be a simple loop is worth more than the same paragraph in a transcript
nobody scrolls back through. This is the one place where more than a line of comment is
wanted; the "why, not what" rule still governs everything inside the class body.

## Build and test loop

```
./gradlew build        # compile and assemble the jar
./gradlew runServer    # start a Paper test server with the plugin installed
```

The `runServer` loop is the point of the whole setup. Build, run, read the startup log,
fix, repeat. Do not report a change as complete without at least confirming it compiles;
for anything touching runtime behaviour, confirm the server starts cleanly.

The test server's directory is `run/` and is git-ignored. It is your harness: probes may
overwrite and restore its files. No game client connects to it; what needs a real client is
listed in `NOTES.md` for the user's client session.

A behaviour that has no caller yet, or needs a player, can be checked with a **temporary probe
class** inside the plugin, run from `onEnable`. Delete it before the stage ends and say in the
report what it checked.

## Git

- **Never commit or push.** The user reviews every stage with `git status` and
  `git diff --stat`, and commits it.
- `run/`, `build/` and `.gradle/` are never tracked.
- **`gradle/wrapper/gradle-wrapper.jar` must stay tracked.** The repository's `.gitignore`
  came from GitHub's Java template, which ignores `*.jar`; the explicit negation for the
  wrapper must not be removed, or cloning the repo produces a broken build.

## Known placeholders

None. The bStats plugin ID is **34120** (registered). Note that the nine custom charts in
`SPEC.md` §14.2 must also be created on the bStats website with matching IDs, which only
the user can do; flag it when stage 10 is reached.

## Build order

Stages 1 to 7 are built and committed. The rest of the plan:

| Stage | What |
|---|---|
| 7.25 | Code audit (done: `STAGE7.25-REPORT.md`) |
| 7.4 | Fixes from the audit |
| 7.5 | Bound items and join behaviour |
| 7.75 | Settings schema and `/mymenu config` commands |
| 7.9 | Per-menu storage: one file per menu, drafts, typed backups |
| — | The user's first session with a real game client |
| 8 | The in-game editor: Phase A (API checks and design, then stop for approval), then 8a, 8b, 8c |
| 9 | `TextService`, wildcards, PlaceholderAPI |
| 10 | `messages.yml`, bStats, update checker |
| 11 | MySQL storage |

The plan can change between stages; your prompt is current, this table may not be. Do not
start a stage, or any part of a later one, that your prompt does not name. The original
project's failure mode was scope outrunning working code, and this rewrite is already larger
than the plugin it replaces. When the stage is done, stop: update `NOTES.md`, add
`DECISIONS.md` entries, write the report, and wait.

## Deferred features

Listed in `SPEC.md` §15. Do not implement them, do not add hooks "for later," and do not
design around them beyond what the architecture already provides. If something in the
list looks cheap while you are nearby, note it in your report and move on.
