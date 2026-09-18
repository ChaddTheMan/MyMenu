# CLAUDE.md — Project instructions for MyMenu

## What this project is

A ground-up rewrite of MyMenu, a Minecraft chest-GUI menu plugin originally released for
Bukkit 1.8.1 in January 2015 (version 1.0.4.3). The new version targets modern Paper and
carries **no backward compatibility** of any kind.

Read `SPEC.md` for required behaviour and `ARCHITECTURE.md` for structure before writing
code. `AUDIT.md` records the pre-implementation review that produced revision 2 of both.
Record non-obvious choices in `DECISIONS.md` as you go.

- **Author:** ChaddTheMan
- **License:** GPL-3.0 (every source file gets the standard GPLv3 header)
- **Repository:** https://github.com/ChaddTheMan/MyMenu
- **Root package:** `me.chaddtheman.mymenu`
- **Version:** 2.0.0 (semantic versioning)

## The legacy code

`legacy/` holds the decompiled 1.0.4.3 source. It is **read-only reference**, never
compiled, never copied. Consult it to answer "what did the original do here?" Do not
treat it as a template: it predates the flattening, uses removed APIs throughout, and
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

- **Check signatures against the javadocs for the target Paper version** before using any
  Paper-specific API, particularly Brigadier, the lifecycle event manager, `PluginLoader`,
  and item serialisation. Tutorials and my own memory are both unreliable here.
- If an API named in `ARCHITECTURE.md` does not exist as described, say so and propose an
  alternative rather than inventing a workaround.
- Never fabricate a method name to make something compile.

### Honesty

- If a decision in `SPEC.md` or `ARCHITECTURE.md` turns out to be wrong or impossible,
  say so directly. These documents are a plan, not scripture.
- Do not report something as working without having run it.
- Flag uncertainty explicitly rather than hedging vaguely.

## Hard rules

1. **No static mutable state.** No static registries, no static session fields. This
   caused the worst bug in the original.
2. **No blocking I/O on the main thread.** File and database access is async and returns
   a `CompletableFuture`. Hop back to the main thread before touching any Bukkit API.
   Exactly one exception exists: `onDisable` drains in-flight writes from storage's own
   executor with a bounded timeout. It never initiates a save. Do not add a second
   exception.
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
9. **Never open or close an inventory inside `InventoryClickEvent`.** Schedule it for the
   next tick.
10. **Never silently drop data.** A parse failure or a failed write puts storage into a
   degraded state. `MenuService` then refuses **mutations before they reach the model** —
   never at the storage boundary, where the model has already changed and the edit is lost
   on the next reload. Read paths keep working.
11. **Substitution happens after parsing.** Substituted values are never re-parsed as
   MiniMessage, and values entering command strings are sanitised. Placeholder output and
   nicknames are player-controlled; this is injection defence.
12. **No legacy support.** No 1.x commands, no 1.x data migration, no material alias
   table, no damage-value handling.

## Documentation during implementation

Four artefacts, four audiences. Keep them separate; a file that duplicates another is a
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

When an entry corrects an earlier one, add a dated correction to the original rather than
editing it silently — see entries 15, 18 and 25 for the pattern.

### `CHANGELOG.md` — what changed, for server owners

Created at stage 1, maintained as you go, bundled into the jar as a resource because
`/mymenu changelog` reads it. Keep-a-Changelog format with an `Unreleased` section at the
top. One line per user-visible change, in plain language, no internal terminology.

If a change cannot be described to a server owner in one sentence, that is a signal the
feature is confused, not a signal to write two sentences.

### Git commit messages — the routine record

Everything that does not clear the `DECISIONS.md` bar lives here. Describe the change and
its reason, not the files touched.

### `NOTES.md` — cross-session state

Short, **overwritten each session, never appended**. Exactly four things: current stage,
what is verified working, what is known broken, what needs the user's input. This is what
makes resuming after a few days cheap.

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

The test server's directory is `run/` and is git-ignored.

## Git

- Commit in small, self-contained units with a working build at each commit.
- Commit messages describe the change and its reason, not the files touched.
- Do not commit `run/`, `build/`, or `.gradle/`.
- **`gradle/wrapper/gradle-wrapper.jar` must be committed.** The repository's `.gitignore`
  came from GitHub's Java template, which ignores `*.jar`. Add an explicit negation for
  the wrapper or cloning the repo produces a broken build.

## Known placeholders

None. The bStats plugin ID is **34120** (registered). Note that the nine custom charts in
`SPEC.md` §14.2 must also be created on the bStats website with matching IDs, which only
the user can do; flag it when stage 10 is reached.

## Build order

Rough sequence. Each stage should compile and, where applicable, run before moving on.

1. Gradle build, `paper-plugin.yml`, plugin loader, empty plugin that enables cleanly
2. Model classes: `Menu`, `MenuItem`, `ItemTemplate`, enums
3. `ItemSerializer` and `YamlMenuStorage`, with the storage interface
4. `MenuHolder`, `MenuRenderer`, `ItemBuilder`
5. Sessions and the inventory listeners; menus open and display
6. `ActionParser` and `ActionExecutor`; menus become interactive
7. Brigadier command tree and subcommands
8. In-game editor: item-from-hand, property GUI, chat input
9. `TextService`, wildcards, PlaceholderAPI hook
10. `messages.yml`, bStats, update checker
11. `MySqlMenuStorage`

Do not start a stage before the previous one runs. The original project's failure mode
was scope outrunning working code, and this rewrite is already larger than the plugin it
replaces.

## Deferred features

Listed in `SPEC.md` §15. Do not implement them, do not add hooks "for later," and do not
design around them beyond what the architecture already provides. If something in the
list looks cheap while you are nearby, note it in `DECISIONS.md` and move on.
