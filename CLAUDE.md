# CLAUDE.md — Project instructions for MyMenu

## What this project is

A ground-up rewrite of MyMenu, a Minecraft chest-GUI menu plugin originally released for
Bukkit 1.8.1 in January 2015 (version 1.0.4.3). The new version targets modern Paper and
carries **no backward compatibility** of any kind.

Read `SPEC.md` for required behaviour and `ARCHITECTURE.md` for structure before writing
code. Record non-obvious choices in `DECISIONS.md` as you go.

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
3. **The model is never the view.** A `Menu` is data. An `Inventory` is a rendering. Never
   store an `Inventory` on a model object, and never read menu state back out of an open
   inventory.
4. **Identify menus by `InventoryHolder`,** never by inventory title.
5. **Never op a player,** temporarily or otherwise. Elevation uses a
   `PermissionAttachment` removed in a `finally` block.
6. **Never write the live data file in place.** Temp file, rotate backup, atomic move.
7. **Listeners hold no per-event state in fields.** Locals and parameters only.
8. **Sessions are cleaned up unconditionally,** on close and on quit, including on
   exception paths.
9. **No legacy support.** No 1.x commands, no 1.x data migration, no material alias table,
   no damage-value handling.

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

- **bStats plugin ID.** Requires registering the plugin at https://bstats.org, which only
  the user can do. Leave a clearly marked constant and do not invent a number.

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

Listed in `SPEC.md` §14. Do not implement them, do not add hooks "for later," and do not
design around them beyond what the architecture already provides. If something in the
list looks cheap while you are nearby, note it in `DECISIONS.md` and move on.
