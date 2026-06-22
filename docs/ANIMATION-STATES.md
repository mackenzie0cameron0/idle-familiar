# Idle Familiar — Animation States Reference (for Artists)

This is the authoritative list of every avatar **state**, what triggers it, and
exactly which PNG file you need to draw for it. It is generated from the plugin
source (`AvatarState`, `PlayerActivityService.resolveState()`, and
`AnimationController`), so the names below are the literal strings the loader
looks for on disk.

If you just want to start drawing: skip to the **Asset filename cheat-sheet** at
the bottom.

---

## 1. How a state becomes a sprite sheet

Every tick the plugin decides on one **state** (an `AvatarState`). It then maps
that state to an **asset key** — a short lowercase string like `low_health` or
`walking` — via `resolveAssetName()`. The loader turns that key into a filename
by trying, in order:

1. `<key>_loop.png`  ← preferred (this is what you almost always draw)
2. `<key>_start.png`
3. `<key>.png`

The first one that exists wins. **In practice: name your file `<key>_loop.png`.**

If none of those exist for the key, the avatar silently falls back to the
**idle** animation. That fallback is intentional — it is how a state with no art
yet behaves, not a bug.

Both this doc and the loader treat the **drop-in folder** (your `.runelite`
folder) and the **bundled folder** (baked into the jar) identically; a file in
the drop-in folder always wins over a bundled file of the same name. See
section 6 for the two locations.

---

## 2. Every state, in priority order

`resolveState()` checks states **top to bottom and returns the first match**, so
a state lower in this table only ever shows if every state above it is inactive.
That priority is the real order below. (`DEFAULT` and `CUSTOM_EVENT` are not part
of the resolve chain — see the notes under the table.)

| # | State | When it triggers (plain English) | Asset key → file to create | Bundled art today? |
|---|-------|----------------------------------|----------------------------|--------------------|
| 1 | `LOGGED_OUT` | Player is logged out / sitting on the login screen. | `logged_out` → `logged_out_loop.png` | Yes |
| 2 | `LOW_HEALTH` | Current hitpoints at or below the **Low HP threshold** (config `lowHpThreshold`, default **20**). Gated by the "Low HP warning" toggle. | `low_health` → `low_health_loop.png` | Yes |
| 3 | `LOW_PRAYER` | Current prayer at or below the **Low prayer threshold** (config `lowPrayerThreshold`, default **10**). Gated by the "Low prayer warning" toggle. | `low_prayer` → `low_prayer_loop.png` | Yes |
| 4 | `AFK_WARNING` | Player has been idle past the **AFK warning threshold** (config `afkWarningThresholdTicks`, default **60** ticks ≈ 36 s). | `afk_warning` → `afk_warning_loop.png` | Yes |
| 5 | `BANKING` | The bank interface is open (you're at a banker NPC / bank booth). Sits **above combat** so opening a bank is never misread as combat. | `banking` → `banking_loop.png` | Yes (`banking.png` present) |
| 6 | `COMBAT` | **Real combat only:** a hitsplat lands on you, or you deal one to the actor you're interacting with. (No longer triggered by merely having an interaction target — that was the old false-positive.) Gated by "React to combat". | `combat` → `combat_loop.png` | Yes |
| 7 | `SKILLING` | Player is training a skill. Detection is per-tick; the **Skilling linger** (config `skillingLingerTicks`, default **2** ticks) only bridges sub-second gaps in click-per-item skills. Gated by "React to skilling". Picks a **per-skill** sheet — see section 3. | `skilling` → `skilling_loop.png` (generic fallback) | Yes (generic only) |
| 8 | `TELEPORTING` | A recognised teleport animation just played. A committed, momentary action — shown above standing conditions, and plays its **full cycle once** (one-shot, see section 4b). | `teleporting` → `teleporting_loop.png` | **No — falls back to idle** |
| 9 | `INVENTORY_FULL` | Inventory just became full. Gated by the "Inventory full warning" toggle. | `inventory_full` → `inventory_full_loop.png` | Yes |
| 10 | `GRAND_EXCHANGE` | A GE buy/sell offer just completed. Sits above movement so the event still surfaces while walking; plays its **full cycle once** (one-shot). | `grand_exchange` → `grand_exchange_loop.png` | **No — falls back to idle** |
| 11 | `WALKING` | Moving ~1 tile/tick. | `walking` → `walking_loop.png` | **No — falls back to idle** |
| 12 | `RUNNING` | Moving ~2 tiles/tick (run enabled). | `running` → `running_loop.png` | **No — falls back to idle** |
| 13 | `PLAYER_IDLE` | Logged in but idle past the **Idle threshold** (config `idleThresholdTicks`, default **15** ticks ≈ 9 s), and not yet at the AFK threshold. | `idle` → `idle_loop.png` | Yes |
| 14 | `PLAYER_ACTIVE` | The catch-all "logged in and doing something unclassified" state — the bottom of the chain when nothing more specific matches. | `active` → `active_loop.png` | Yes |

### Notes on the non-chain states

- **`DEFAULT`** — never returned by `resolveState()`. It is the controller's
  internal fallback bucket and is mapped to the `idle` key. Drawing `idle_loop.png`
  covers it; you do not draw a separate `default` file.
- **`CUSTOM_EVENT`** — also not in the resolve chain (it is set by other code
  paths, e.g. scripted events). It maps to the **`active`** key, so it reuses
  `active_loop.png`. No separate file needed.

### States currently with NO bundled art (fall back to idle)

These four states have **no sheet shipped in the plugin**, so until art is added
they render the idle animation. Drawing these is the highest-value work for an
artist:

- `grand_exchange` → `grand_exchange_loop.png`
- `teleporting` → `teleporting_loop.png`
- `walking` → `walking_loop.png`
- `running` → `running_loop.png`

### BANKING is now a live state

`BANKING` is wired into `resolveState()` (above `COMBAT`). It triggers whenever the
bank interface is open and clears when you close it. Draw:

- `banking` → **`banking_loop.png`**

(The existing `banking.png` already satisfies the key, but a dedicated
`banking_loop.png` is the proper multi-frame sheet to draw.)

---

## 3. Per-skill SKILLING sub-keys

When the state is `SKILLING` and the plugin knows **which** skill is being
trained, it builds the key from the skill name: it lowercases the skill label and
replaces spaces with underscores, then appends `_loop`. So while training Fishing
it looks for `fishing_loop.png`.

The skill sheets the plugin will look for:

| Skill | File to create |
|-------|----------------|
| Fishing | `fishing_loop.png` |
| Mining | `mining_loop.png` |
| Woodcutting | `woodcutting_loop.png` |
| Smithing | `smithing_loop.png` |
| Cooking | `cooking_loop.png` |
| Fletching | `fletching_loop.png` |
| Herblore | `herblore_loop.png` |
| Runecraft | `runecraft_loop.png` |
| Farming | `farming_loop.png` |
| Thieving | `thieving_loop.png` |

(Any other gathering/artisan skill follows the same rule — lowercase name +
`_loop.png`.)

**Fallback chain for skilling**, in order:

1. `<skill>_loop.png` (e.g. `mining_loop.png`)
2. `skilling_<skill>.png` (e.g. `skilling_mining.png`) — secondary lookup
3. `skilling_loop.png` — the **generic** skilling sheet, used when the specific
   skill has no art or the skill is unknown
4. `idle_loop.png` — last resort if even the generic is missing

> **Combat-style skills are XP, not animation.** Attack, Strength, Defence,
> Hitpoints, Ranged, and Magic surface through the **COMBAT** state (driven by
> XP / interaction target), not through a per-skill skilling sheet. You do **not**
> draw `ranged_loop.png` or `magic_loop.png` — those play `combat_loop.png`.

---

## 4. Naming convention for random variants

To stop a state looking repetitive you can ship several sheets for it; the plugin
picks one at random on entry and **re-rolls each time the loop finishes**, with
odds controlled by `weights.json`.

**The critical rule: variants are named after the RESOLVED PRIMARY base, not the
bare key.** Because the primary file is `<key>_loop.png`, the resolved base is
`<key>_loop`, and every variant suffix attaches to *that*:

```
idle_loop.png            <- the primary (always present)
idle_loop_2.png          <- numbered variants 2 through 9
idle_loop_3.png
...
idle_loop_9.png
idle_loop_uncommon.png   <- rarity-tagged variants
idle_loop_rare.png
```

So the valid variant names for a key are:

- `<key>_loop_2.png` … `<key>_loop_9.png`
- `<key>_loop_uncommon.png`
- `<key>_loop_rare.png`

> **Common mistake:** the variant is `idle_loop_rare.png`, **not** `idle_rare.png`.
> A file named `idle_rare.png` (without `_loop`) is **not** discovered as a
> variant and will be ignored. The `_loop` segment must be in the name.

(If a key's primary happens to resolve to the bare `<key>.png` instead of
`<key>_loop.png`, then the base is `<key>` and variants would be `<key>_2.png`,
`<key>_uncommon.png`, etc. — but since you should always draw `_loop` files, treat
`<key>_loop_*` as the rule.)

## 4b. One-shot ("finish the full cycle") animations

Two states are **momentary events** rather than ongoing conditions: `TELEPORTING`
and `GRAND_EXCHANGE`. With the **"Finish one-shot animations"** config option on
(default), entering one of these latches its animation so it **plays its entire
cycle exactly once**, even if the underlying state ends a tick later — so a teleport
flourish or a GE "ka-ching" never gets cut off mid-pose. After the single cycle it
hands back to whatever state is current.

What this means for you as an artist: draw `teleporting_loop.png` and
`grand_exchange_loop.png` as a **complete one-shot gesture** (wind-up → action →
settle) that reads well played once. The last frame is the resting pose. Variants
work here too (the chosen variant plays through fully).

---

### weights.json

Relative odds live in `weights.json` in the **same folder** as the sheets. Any
variant not listed defaults to weight `1` (equal chance). Higher number = more
frequent.

```json
{ "idle_loop": 10, "idle_loop_uncommon": 3, "idle_loop_rare": 1 }
```

That example makes the plain idle sheet ~71% likely, the uncommon ~21%, and the
rare ~7%. A `weights.json` in the drop-in folder overrides the bundled one.

---

## 5. Sprite-sheet format

- **One horizontal row**, frames laid out left → right.
- **Each frame is a square**, its side equal to the **image height**. The loader
  slices the sheet into `width ÷ height` frames automatically, so the **frame
  count is `width ÷ height`**.
  - `64 × 64` PNG  → 1 frame (a static pose)
  - `256 × 64` PNG → 4 frames
  - `512 × 64` PNG → 8 frames
- **PNG alpha (transparency) is supported and recommended** — draw on a
  transparent background.
- **Timing: 300 ms per frame**, and the **last frame of a loop is held twice as
  long** (≈600 ms) so the loop has a small breath before repeating. (Players can
  scale global speed in config, but draw to the 300 ms cadence.)
- The plugin's reference frame box is `64 × 64`. Larger square frames work — they
  are scaled to fit — but keeping frames a consistent height across your sheets
  keeps the avatar visually stable.

---

## 6. Where the files go — two locations

| | Bundled (in the jar) | Drop-in (runtime) |
|---|---|---|
| **Path** | `src\main\resources\com\idlefamiliar\avatar\default\` | `C:\Users\Mac\.runelite\idle-familiar\avatar\` |
| **Effect** | Baked into the plugin build; ships to all users. | Loaded at runtime on the local machine only. |
| **Needs a rebuild?** | **Yes** — `./gradlew shadowJar`. | **No.** |
| **Use when** | Shipping art as part of the plugin. | Iterating, testing, personal swaps. A drop-in file **overrides** the bundled file of the same name. |

The drop-in folder is **created automatically** the first time the plugin starts.
After dropping files in, toggle the plugin off/on (or use "Reload animations")
to pick them up — no restart of the whole client required.

---

## Asset filename cheat-sheet (everything an artist can produce)

**State sheets (one per state key):**

```
idle_loop.png            (also covers DEFAULT and PLAYER_IDLE)
active_loop.png          (also covers CUSTOM_EVENT / PLAYER_ACTIVE)
walking_loop.png         (no bundled art yet)
running_loop.png         (no bundled art yet)
teleporting_loop.png     (no bundled art yet)
grand_exchange_loop.png  (no bundled art yet)
combat_loop.png
inventory_full_loop.png
afk_warning_loop.png
low_health_loop.png
low_prayer_loop.png
logged_out_loop.png
skilling_loop.png        (generic skilling fallback)
banking_loop.png         (live state — bank interface open)
```

**Per-skill skilling sheets:**

```
fishing_loop.png    mining_loop.png      woodcutting_loop.png
smithing_loop.png   cooking_loop.png     fletching_loop.png
herblore_loop.png   runecraft_loop.png   farming_loop.png
thieving_loop.png
```

**Optional random variants** (for any key above — replace `idle` with the key):

```
idle_loop_2.png … idle_loop_9.png
idle_loop_uncommon.png
idle_loop_rare.png
weights.json   (relative odds; unlisted variants default to weight 1)
```
