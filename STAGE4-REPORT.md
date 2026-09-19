# Stage 4 report: rendering

Status: **done, not committed.** Stage 5 has not been started. This file is for your review and
is not meant to be committed. The lasting record is in DECISIONS #72–75 and in `NOTES.md`.

## What was built

| File | Role |
|---|---|
| `render/MenuHolder` | `InventoryHolder` carrying the menu **name**, the global revision and the `ViewMode`. It does not hold the `Menu`. Only the renderer creates one |
| `render/ViewMode` | `VIEW` or `EDIT` |
| `render/TokenReplacer` | The stage 9 seam: `Component replace(Component parsed)`. `NONE` is the identity |
| `render/ItemBuilder` | Template → `ItemStack` for both shapes. Text parsing (`&`, `&#hex`, `<!mm>`), the italic rule, glow, and reading opaque bytes back. Unreadable bytes raise a **checked** `UnreadableItemException` |
| `render/MenuRenderer` | `render(Menu, long revision, Player, ViewMode)` → fresh `Inventory`. Handles VIEW hiding and fallback, EDIT markers, barriers for unreadable items, and the rendered-icon PDC tag |
| `storage/ItemSerializer` | Now takes an `ItemBuilder`; `capture` checks its round trip with `ItemBuilder.build`. `deserialize` moved to `ItemBuilder` |
| `model/ItemTemplate` | Javadoc only |

`MyMenu` is unchanged. Nothing constructs a renderer until stage 5 opens menus.

## The substitution seam (parse now, replace later)

`ItemBuilder` parses admin text into a `Component` first, with tokens still literal. Only then
does it call `TokenReplacer.replace(component)`. The interface takes and returns a
`Component`, so an implementation has no string to re-parse, and the order is enforced by the
type. For items, titles, names and lore it runs after parsing. For opaque items it runs on the
deserialised name and lore components. The renderer takes a `Function<Player, TokenReplacer>`;
today that is `v -> NONE`, so `{PLAYER}` renders literally. Stage 9 supplies an implementation
built on `Component#replaceText`. The probe checked the shape: a replacer that inserts
`&c&l<red>Evil` for `{PLAYER}` produced that literal text, with no bold and no red anywhere in
the component tree.

## Your two questions

### 1. What does a slot show when its opaque blob fails to deserialise?

**A barrier named "Unreadable item", in both modes. Never an empty slot. The rest of the menu
renders normally.**

- **Why not empty:** an empty slot hides the fault, and in VIEW mode it looks like "nothing
  here" over a slot that still has actions in the model. **Why not a thrown error:** one bad
  slot would take the whole menu down, which is the 1.x failure again.
- **How the admin finds it:**
  1. A server-log warning naming the menu, slot, role (item or hidden fallback) and cause:
     `Menu 'probe' slot 5: the stored item could not be loaded and shows as a barrier. Its data
     is kept unchanged. Cause: java.util.zip.ZipException: Not in GZIP format`. It is logged
     once per stored item per renderer, not on every open; a *different* blob in the same slot
     that also fails is logged again.
  2. In EDIT mode the barrier's lore says `The stored item in slot 5 of 'probe' could not be
     loaded. Its data is kept unchanged. Cause: …`, with the cause cut to 60 characters.
  3. If a gated item's **fallback** is broken, EDIT mode still reports it in the permission
     marker ("Others see a barrier (hidden fallback unreadable)"), even though EDIT never
     displays fallbacks.
- **The data is never touched.** Storage keeps the bytes, so a blob that failed because a
  datapack was missing comes back intact once the datapack is loaded (#67). A stored item that
  deserialises to *empty* counts as unreadable too.
- **Left open (DECISIONS #74):** whether clicking the barrier runs the slot's actions is stage
  6's decision. As built, nothing prevents it. The stage 8 property editor must also refuse to
  edit metadata it cannot deserialise.

### 2. How EDIT mode marks permission-gated items, including when there is already lore

The marker is lines **appended below the item's existing lore, after one blank separator line**.
It is added to the rendered stack only; the model is never modified. The lines are
plugin-built components (explicitly non-italic). The permission node is inserted as a
**literal text node**, never parsed:

```
<existing lore line 1>
<existing lore line …>
                                     ← blank separator, only if lore existed
Only shown with permission:          (gold)
  mymenu.probe.secret                (yellow)
Others see the hidden fallback item. (gray; or "an empty slot.",
                                      or "a barrier (hidden fallback unreadable).")
```

Actual output from the probe:

- with lore: `[Existing lore, , Only shown with permission:,   mymenu.probe.secret, Others see the hidden fallback item.]`
- without lore: `[Only shown with permission:,   mymenu.probe.secret, Others see an empty slot.]`

Appending keeps the admin's own lore where it would be in VIEW mode, so they still see what
players see, and the marker sits in a fixed, recognisable place. Prepending would push the real
lore down and make the item look different from its VIEW rendering. Edge case: an item already
at the 256-line lore limit loses its *last existing* lines from the editor view, not the
marker. Nothing is lost from the model.

Opaque items with lore are handled the same way; the marker is appended to the deserialised
lore. Glint is not used as the marker: glow is an item property the admin can set, so it would
be ambiguous.

## Italic, both halves (DECISIONS #70, #73)

Item names and lore get `decorationIfAbsent(ITALIC, FALSE)` at the root, so `&o` inside the text
still wins. Titles do not get it, because titles are not italic by default. `capture` rebuilds its
candidate through the **same** `ItemBuilder.build` the renderer uses, so the two cannot drift.

Runtime results:

| Captured item | Result |
|---|---|
| name + lore with explicit `italic: false` (typical plugin item) | **readable**; this went to bytes before stage 4 |
| name with italic unset (anvil rename) | **bytes**; this was readable before stage 4. See below |
| name with explicit `italic: true` | readable, `&oSlanted` |
| plain stone ×5, glint-only stick | readable (glint → `glow: true`) |
| rendered `&aHello {PLAYER}` + `&7…`/`&o…` lore, `&#ff8800` hex, `&aA &cB`, `&lBold`, lore-only | each round-trips back to the identical template |

**A behaviour change for you to accept or reject.** Anvil-renamed items now capture as bytes.
The readable form renders non-italic, and such a name is italic only by default, so the round
trip fails closed. I rejected normalising "unset" to `&o`, because the stored item would then
not be `isSimilar` to the original, which breaks `EXACT` bound-item matching. Details are in
#73.

## Other points that had to land

- **Holder:** name, revision and mode; `instanceof MenuHolder` identifies it. Hopper, dispenser
  and dropper use `createInventory(holder, InventoryType, Component)`. I checked with `javap -v`
  that none of the `createInventory` overloads used are deprecated on 26.2.
- **VIEW:** a player without the node sees the fallback, or an empty slot. Positions never shift
  (slots 9–17 stayed empty and slot 8 stayed at 8).
- **EDIT:** permissions are not applied. A player with no nodes saw the gated diamond, emerald
  and gold ingot.
- **Rendered-icon tag:** `mymenu:rendered_icon` (BOOLEAN), keyed from the plugin. It goes on
  every item placed, including fallbacks, barriers and marked items. The bound-item key does not
  exist yet (stage 5/6) and must be a different key.
- **Glow:** `ENCHANTMENT_GLINT_OVERRIDE = true` on both shapes, applied after building. A stored
  `false` override in bytes is left alone.
- **Purity:** after four renders, `registry.find("probe")` returned the *same instance* and the
  revision was unchanged. `menus.yml` was byte-identical after the run, with no backup and no
  write.
- **Adventure is 5.2.0 on 26.2**, not 4.x. Every Adventure method used here (`decorationIfAbsent`,
  `replaceText`, `MiniMessage.miniMessage()`, the legacy serializer builder) was checked with
  `javap` against the 5.2.0 jar.

## Verification

- `./gradlew clean build`: clean.
- `./gradlew runServer` with the real `run/plugins/MyMenu/menus.yml`. It holds `welcome`,
  `hopperdemo`, `shop`, and a new `probe` menu with a real serialized item (glow on), a garbage
  blob, a truncated real blob, a gated item with and without fallback, and a gated item whose
  fallback is corrupt. All four menus loaded, and there was no degraded warning. The `probe`
  menu is still in `run/` (git-ignored) for use at stage 5.
- **How:** there is no game client here, so a temporary `Stage4Probe`, now **deleted**, ran
  after load. It rendered with `java.lang.reflect.Proxy` players that answer only
  `hasPermission`/`getName`, and inspected the inventories. **45 PASS, 0 FAIL.** The full output
  is below.
- The final run had the probe removed and started and stopped cleanly.
- **Not verified:** how any of this *looks* in a real client, meaning the glint, italics and lore
  layout. They were checked as data components only. Stage 5 is the first point where a menu can
  be opened in game.

### Probe output

```
PASS holder is MenuHolder
PASS holder name/revision/mode
PASS size 18
PASS s0 rendered
PASS s3 VIEW no perm -> fallback
PASS s4 VIEW no perm, no fallback -> empty
PASS s5 garbage blob -> barrier
PASS s6 truncated blob -> barrier
PASS s7 VIEW corrupt fallback -> barrier
PASS s8 renders after corrupt slots
PASS s9..17 empty, no compaction
s5 VIEW barrier lore: [This item could not be loaded.]
PASS s3 VIEW with perm -> icon
PASS s4 VIEW with perm -> icon
PASS s7 VIEW with perm -> icon
PASS edit holder mode
PASS s3 EDIT no perm -> icon
PASS s4 EDIT no perm -> icon
s3 EDIT lore: [Existing lore, , Only shown with permission:,   mymenu.probe.secret, Others see the hidden fallback item.]
s4 EDIT lore: [Only shown with permission:,   mymenu.probe.secret, Others see an empty slot.]
s7 EDIT lore: [Only shown with permission:,   mymenu.probe.secret, Others see a barrier (hidden fallback unreadable).]
s5 EDIT lore: [The stored item in slot 5 of 'probe', could not be loaded. Its data is kept unchanged., Cause: java.util.zip.ZipException: Not in GZIP format]
PASS s3 EDIT keeps existing lore, then blank, then marker
PASS s4 EDIT marker without separator
PASS s7 EDIT marker mentions unreadable fallback
PASS s0 EDIT has no marker
PASS s1 descriptive glow
PASS s2 opaque glow
PASS s2 opaque is the stored paper
PASS s0 no glow
PASS s0 name italic FALSE
PASS s0 lore line 1 italic FALSE
PASS s0 lore '&o' line still italic
PASS s0 token literal
PASS key is mymenu:rendered_icon
PASS every rendered item carries the icon key (incl. barriers, fallbacks)
PASS model unchanged after renders
PASS revision unchanged
PASS hopper type/size
PASS welcome s12 fallback for no-perm
PASS hostile value literal
PASS hostile value adds no bold/red
PASS capture italic:false name+lore -> readable
capture anvil-style (italic unset) -> Opaque
capture explicit italic:true -> Descriptive[material=DIAMOND, displayName=&oSlanted, amount=1, lore=[], glow=false]
PASS capture plain stone -> readable
PASS capture glint-only -> readable glow
PASS round trip &aHello {PLAYER} [&7Plain line, &oItalic line]
PASS round trip &#ff8800Hex name []
PASS round trip &aA &cB [&aone &btwo]
PASS round trip &lBold []
PASS round trip null [no name]
PASS MiniMessage name parsed, non-italic
FAILURES: 0
```

### Warnings as logged

The second group comes from the probe's separate hostile-replacer renderer, because the log-once
set is per renderer instance.

```
[MyMenu] Menu 'probe' slot 5: the stored item could not be loaded and shows as a barrier. Its data is kept unchanged. Cause: java.util.zip.ZipException: Not in GZIP format
[MyMenu] Menu 'probe' slot 6: the stored item could not be loaded and shows as a barrier. Its data is kept unchanged. Cause: Loading NBT data
[MyMenu] Menu 'probe' slot 7: the stored hidden fallback could not be loaded and shows as a barrier. Its data is kept unchanged. Cause: java.util.zip.ZipException: Not in GZIP format
[MyMenu] Menu 'probe' slot 5: the stored item could not be loaded and shows as a barrier. Its data is kept unchanged. Cause: java.util.zip.ZipException: Not in GZIP format
[MyMenu] Menu 'probe' slot 6: the stored item could not be loaded and shows as a barrier. Its data is kept unchanged. Cause: Loading NBT data
[MyMenu] Menu 'probe' slot 7: the stored hidden fallback could not be loaded and shows as a barrier. Its data is kept unchanged. Cause: java.util.zip.ZipException: Not in GZIP format
```

## Documents not edited

- ARCHITECTURE §4 still shows `render(Menu, Player)`. The real signature is `render(Menu, long
  revision, Player, ViewMode)` (#75).
- ARCHITECTURE §7.5 and §2 imply `ItemSerializer` does both directions. Deserialisation now
  lives in `ItemBuilder` (#73).
