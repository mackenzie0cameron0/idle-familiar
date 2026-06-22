# Adding Animations to Idle Familiar

There are two ways to add or change the avatar's animations. **The drop-in folder
is the easy one — no rebuild, no code.**

---

## Option A — External drop-in folder (recommended, no rebuild)

The plugin loads sheets from a folder in your RuneLite home *in preference to*
the ones baked into the jar. Drop a PNG in, then either restart the plugin or tick
**"Reload animations"** in the plugin's Debug config section to pick it up live.

### Where

```
<your RuneLite home>/idle-familiar/avatar/
```

On Windows that is:

```
C:\Users\<you>\.runelite\idle-familiar\avatar\
```

The folder is **created automatically** the first time the plugin starts, so you
can just open it.

### What to drop in

A PNG named after the state you want to animate, with a `_loop` suffix:

```
walking_loop.png
fishing_loop.png
combat_loop.png      <- overrides the built-in combat animation
```

That's the whole step. If a file exists for a state, it wins; if not, the plugin
silently falls back to the bundled sheet, and if there's no bundled sheet either,
it falls back to the idle animation.

---

## Option B — Bundled assets (requires rebuilding the jar)

Put the same PNGs in:

```
src/main/resources/com/idlefamiliar/avatar/default/
```

then rebuild (`./gradlew shadowJar`). Use this only if you're shipping the
animations as part of the plugin itself.

---

## Sprite sheet format

- **One row, frames laid left → right.**
- **Each frame is square**, sized to the **height** of the image. The loader
  slices the sheet into `width ÷ height` frames automatically.
  - `64×64` PNG  → 1 frame (a static pose)
  - `256×64` PNG → 4 frames
  - `512×64` PNG → 8 frames
- Transparency (PNG alpha) is supported and recommended.
- Each frame shows for 300 ms; the last frame of a loop is held twice as long.
  (Global speed is adjustable in the plugin config.)

---

## State keys

Name the file `<key>_loop.png`.

| Key | Shown when… |
|---|---|
| `idle` | nothing happening / AFK threshold not yet reached |
| `active` | logged in and doing something unclassified |
| `walking` | moving 1 tile/tick |
| `running` | moving 2 tiles/tick |
| `teleporting` | a teleport animation just played |
| `grand_exchange` | a GE buy/sell offer just completed |
| `combat` | in combat |
| `skilling` | skilling, specific skill unknown |
| `inventory_full` | inventory just filled |
| `afk_warning` | idle past the AFK warning threshold |
| `low_health` | hitpoints at/under the low-HP threshold |
| `low_prayer` | prayer at/under the low-prayer threshold |
| `logged_out` | logged out / on the login screen |

### Per-skill skilling animations

While skilling, the avatar looks for a sheet named after the **skill**:

```
fishing_loop.png   mining_loop.png   woodcutting_loop.png
smithing_loop.png  cooking_loop.png  fletching_loop.png
herblore_loop.png  runecraft_loop.png  farming_loop.png
thieving_loop.png  …
```

Use the lower-cased skill name. If a skill has no sheet, it uses the generic
`skilling_loop.png`, then idle.

> Note: a few states ship **without** a bundled sheet — `grand_exchange`,
> `level_up`, and the generic `combat` / `skilling` fallbacks — so they fall back to
> `idle` until you add art for them. This is the intended behaviour, not a bug.

---

## Random variants (optional)

To make a state less repetitive, add numbered or rarity-tagged variants next to
the primary sheet. They're picked at random each time the state is (re)entered:

```
idle_loop.png            <- always present (the primary)
idle_loop_uncommon.png
idle_loop_rare.png
idle_loop_2.png … idle_loop_9.png
```

Relative odds live in `weights.json` in the same folder (any variant not listed
defaults to weight `1`):

```json
{ "idle_loop": 10, "idle_loop_rare": 1 }
```

A `weights.json` placed in the external drop-in folder overrides the bundled one.

---

## Quick checklist

```
[ ] PNG drawn: one row, square frames (frame size = image height)
[ ] Named <key>_loop.png  (e.g. walking_loop.png, fishing_loop.png)
[ ] Dropped in  <RuneLite home>/idle-familiar/avatar/
[ ] Plugin toggled off/on (or RuneLite restarted)
[ ] Avatar plays it — if it falls back to idle, re-check the file name
```
