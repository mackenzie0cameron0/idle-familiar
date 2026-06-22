# Avatar Animation Specification - Template for AI Generation

**Purpose:** This document tells an AI animation generator everything it needs to
produce a complete, unique avatar for the Idle Familiar plugin. The avatar is a
small animated character that reacts to what a player is doing in Old School
RuneScape. Follow the technical format exactly; the creative direction (species,
style, personality) is yours - the action descriptions below describe *what
happens*, not *who* the character is, so they work for any avatar (an animal, a
robot, a wizard, a blob, etc.).

---

## 1. How the avatar system works

The plugin picks one animation at a time based on the player's current state and
plays it. Each animation is a single PNG **sprite sheet**. There are two playback
modes:

- **Loop** - the animation repeats continuously while the state lasts (idle,
  walking, skilling, combat, etc.). The sheet must loop **seamlessly**: the last
  frame has to flow back into the first with no visible pop.
- **One-shot** - a brief, momentary event that plays its full cycle **exactly
  once** and then hands back to whatever the character was doing
  (teleporting, a completed Grand Exchange sale, a level-up, crossing an agility
  obstacle). Design these with a clear **beginning and end**, not a seamless loop.

If a specific sheet is missing, the plugin falls back gracefully:

- A missing per-skill sheet falls back to the generic `skilling` sheet, then to
  `idle`.
- A missing combat-style sheet falls back to the generic `combat` sheet.
- Most other missing states fall back to `idle`.

So the only truly required animation is **idle**. A strong minimum set is: idle,
active, walking, running, combat, skilling, logged_out, and the four reaction
states. Per-skill and combat-style sheets are enhancements that make the avatar
feel richer.

---

## 2. Technical format (follow exactly)

**Sheet layout**

- One PNG per animation, a **single horizontal row** of frames, left to right.
- Every frame is **square**, and the frame size equals the image **height**. The
  loader slices the sheet into `width / height` frames, so the image **width must
  be an exact whole multiple of its height**. Example: a 256px-tall sheet with 6
  frames is `1536 x 256`.
- **Transparent background** (PNG alpha) is required - the avatar floats on the
  game overlay and on the desktop with no backdrop. Never bake in a solid
  background.

**Resolution**

- Recommended frame size: **256 x 256 px** per frame. Any square size works, but
  use **one resolution across every sheet** of a given avatar.
- The plugin normalizes display to **64 px tall** at 100% scale, and the desktop
  widget can scale up to ~400%. So: design a **bold, readable silhouette** that
  reads at ~64 px, but deliver at 256 px so it stays crisp when enlarged.

**Frame count and timing**

- Each frame is shown for ~**300 ms**; the **last frame of every sheet is held
  twice as long** (~600 ms) as a natural "settle." Make the last frame a calm
  resting/pause pose.
- Suggested counts: **4-8 frames** for most actions; **8-12** for idle and other
  high-traffic states; **6-10** for one-shots so the event reads clearly. A
  6-frame loop runs ~2.1 s. (Players can globally speed playback up or down.)

**Consistency across the whole avatar**

- Same character, palette, line weight, and rendering style on every sheet.
- **Consistent size and anchor:** the character occupies a consistent footprint
  and sits on a consistent baseline (e.g. feet/base at the same height) in every
  frame of every sheet, so it never jumps or resizes when the state changes.
- **Consistent facing** (e.g. facing the viewer, or a steady 3/4 view) so
  switches between animations read naturally.

---

## 3. Naming convention

Name each primary sheet `<key>_loop.png`. Place files in the drop-in folder
`<RuneLite home>/idle-familiar/avatar/` (created on first run), or bundle them in
`src/main/resources/com/idlefamiliar/avatar/default/`.

| Slot | Filename |
|---|---|
| State | `<state>_loop.png` (e.g. `idle_loop.png`) |
| Per-skill | `<skill>_loop.png` (lower-case, e.g. `fishing_loop.png`) |
| Combat style | `combat_melee_loop.png`, `combat_ranged_loop.png`, `combat_magic_loop.png` |

Use lower-case, words joined by underscores. The exact keys are listed in the
catalog in section 5.

---

## 4. Random variants (optional, recommended for idle)

Any state can have multiple animations that the plugin randomly chooses between,
re-rolled every loop, so a state does not look repetitive. Add variants beside the
primary sheet:

```
idle_loop.png            (the primary - always present)
idle_loop_uncommon.png
idle_loop_rare.png
idle_loop_2.png ... idle_loop_9.png   (up to eight numbered extras)
```

That is up to 11 variations of a single state. Relative odds live in a
`weights.json` file in the same folder; any variant not listed defaults to weight
1, and weights are relative integers (not percentages):

```json
{ "idle_loop": 70, "idle_loop_uncommon": 25, "idle_loop_rare": 5 }
```

Idle is seen far more than anything else, so it benefits most from variants
(including occasional rare "fidget" animations). Generate the primary first;
variants are a bonus pass.

---

## 5. Animation catalog

Each entry gives the **filename**, **playback mode**, a **frame suggestion**, and
a detailed, character-agnostic description of the action. Keep the avatar centered
and consistently sized throughout.

### Core / resting (loops)

- **`idle_loop.png`** - loop, 8-12 frames. The avatar at rest, doing nothing in
  particular - its calm default and by far the most-seen animation. Low-energy
  ambient motion: slow breathing, a gentle sway, an occasional blink or small
  twitch. Reads as "waiting patiently, content." Prime candidate for variants.
- **`active_loop.png`** - loop, 4-6 frames. Logged in and doing something
  unclassified. Alert and engaged but not performing a specific task: glancing
  around, shifting weight, an attentive ready posture. A notch more energetic than
  idle.
- **`logged_out_loop.png`** - loop, 2-6 frames. The player is not in the game /
  on the login screen, so the avatar is dormant: asleep, curled up, or
  powered-down, with minimal motion (the slow rise-and-fall of a sleeping figure,
  a drifting "Zzz", a dimmed glow). Clearly "offline / off."

### Locomotion (loops)

- **`walking_loop.png`** - loop, 4-8 frames. Travelling at a steady, relaxed pace.
  A complete walk cycle performed in place that reads as continuous forward
  movement. Calm and unhurried.
- **`running_loop.png`** - loop, 4-8 frames. Travelling quickly. A run cycle -
  faster, leaning into the motion, limbs pumping, a sense of exertion. Clearly
  more energetic than walking.

### Reactions / warnings (loops)

- **`inventory_full_loop.png`** - loop, 4-6 frames. Reacting to a full inventory.
  The avatar is overloaded - arms full, an overflowing bag, items threatening to
  spill, a strained or exasperated "I can't carry any more" beat. Mildly comedic.
- **`low_health_loop.png`** - loop, 4-6 frames. Hurt and in danger (low
  hitpoints). Weakened and unsteady: clutching a wound, wobbling, a low stance, a
  pained look, perhaps a pulsing red warning tint. Communicates urgency at a
  glance.
- **`low_prayer_loop.png`** - loop, 4-6 frames. Spiritual / magical energy running
  out (low prayer). A surrounding aura or glow flickers and dims, the avatar
  glancing worriedly at the fading light. A "running on empty" feeling, calmer
  than low health.
- **`afk_warning_loop.png`** - loop, 4-8 frames. The player has been inactive too
  long. Bored and impatient while still present: tapping a foot, checking the
  time, a big yawn, nodding off, or glancing around restlessly. Light comedic
  boredom. (Distinct from logged_out: here the character is awake but waiting.)

### Momentary events (one-shots)

- **`teleporting_loop.png`** - one-shot, 6-8 frames. A teleport. The avatar
  dematerializes: a swirl of magic, a rising shimmer of sparks, the figure
  dissolving or beaming away, optionally reforming at the end. Begin normal, peak
  the effect in the middle, end gone or just-returned. Plays once.
- **`grand_exchange_loop.png`** - one-shot, 4-6 frames. A market transaction just
  completed. A short, upbeat "cha-ching" celebration: coins raining or tossed, a
  pleased money gesture, a satisfied nod with a coin flip. Brief and happy.
- **`level_up_loop.png`** - one-shot, 6-8 frames. A skill level just increased.
  The biggest, most triumphant flourish: arms thrown up, a burst of light /
  fireworks / stars, a cheer or fist-pump, a proud pose. Celebratory and clearly
  a "win."
- **`death_loop.png`** - shown for ~5 seconds, 4-8 frames. The character was
  defeated (hitpoints hit 0). Play a collapse that settles into a defeated /
  knocked-out pose that still reads fine while it repeats for a few seconds:
  falling down then lying dazed with stars circling, fainting, or a small spirit
  drifting up. Dramatic but can be playful.

### Combat (loops)

- **`combat_loop.png`** - loop, 4-8 frames. Generic fighting, used when the combat
  style is unknown or the style sheets below are not provided. An aggressive
  stance with a repeating attack motion (a strike / swing cycle).
- **`combat_melee_loop.png`** - loop, 4-8 frames. Close-range fighting: swinging a
  sword, club, or fists in repeated melee strikes.
- **`combat_ranged_loop.png`** - loop, 4-8 frames. Ranged fighting: drawing and
  firing a bow or crossbow, or hurling thrown weapons, repeatedly.
- **`combat_magic_loop.png`** - loop, 4-8 frames. Spellcasting combat: hands or
  staff glowing, hurling bolts of magical energy, arcane gestures.

### Banking (loop)

- **`banking_loop.png`** - loop, 4-6 frames. At the bank, organizing belongings:
  rummaging in a chest or bag, sorting and depositing items, handing things across
  a counter. Calm and administrative.

### Skilling - generic (loop)

- **`skilling_loop.png`** - loop, 4-8 frames. A generic "hard at work" labor loop,
  shown whenever a skill is detected that has no specific sheet below. A neutral,
  repetitive working motion.

### Skilling - per skill (loops, except agility)

Each is a repetitive work cycle. Use a recognizable tool/action so the skill is
readable even at small size.

- **`woodcutting_loop.png`** - loop. Chopping a tree: repeated axe swings into a
  trunk, wood chips flying.
- **`mining_loop.png`** - loop. Swinging a pickaxe at rock/ore: repeated strikes
  with sparks and rubble.
- **`fishing_loop.png`** - loop. Working a fishing rod or net: casting and reeling,
  a bobbing line, the occasional catch flicked up.
- **`cooking_loop.png`** - loop. Cooking over a fire or range: stirring a pot,
  flipping food, tending flames, a puff of steam.
- **`firemaking_loop.png`** - loop. Lighting and tending a fire: striking a
  tinderbox, the flame catching and growing, feeding it.
- **`smithing_loop.png`** - loop. Hammering metal on an anvil: repeated hammer
  strikes with sparks flying off the workpiece.
- **`fletching_loop.png`** - loop. Carving and assembling from wood: whittling
  arrow shafts or a bow with a knife, stringing a bow.
- **`crafting_loop.png`** - loop. Handcrafting: working leather, gems, or pottery
  with hands and small tools, turning or shaping a piece.
- **`herblore_loop.png`** - loop. Mixing potions: grinding ingredients, stirring a
  bubbling vial, a wisp of colored smoke as it changes.
- **`runecraft_loop.png`** - loop. Channeling at an altar: holding up essence as
  glowing runes swirl into being and stream outward.
- **`construction_loop.png`** - loop. Building: sawing and hammering wood,
  measuring, assembling a piece of furniture, sawdust flying.
- **`agility_loop.png`** - ONE-SHOT, 6-8 frames. Crossing an obstacle: a clear
  start-to-finish vault, climb, leap, or balance across, ending with a landing.
  Because the player is already moving on, give it a definite beginning and end
  rather than a seamless loop.
- **`thieving_loop.png`** - loop. Sneaking and pickpocketing: creeping low,
  reaching a hand into a pocket or stall, a shifty glance around.
- **`slayer_loop.png`** - loop. Fighting a monster as a task: striking a creature -
  combat-flavored, but framed as deliberate skilling work.
- **`hunter_loop.png`** - loop. Trapping and catching: setting or springing a
  trap, creeping up, netting or scooping up prey.
- **`farming_loop.png`** - loop. Tending crops: planting seeds, raking soil,
  watering, or harvesting from a patch.

---

## 6. Delivery checklist

For each sheet:

- [ ] Single horizontal row, square frames, width = a whole multiple of height.
- [ ] Transparent background (PNG alpha), no baked-in backdrop.
- [ ] Same character, style, palette, size, anchor, and facing as every other
      sheet.
- [ ] Loops connect seamlessly (loops) OR have a clear start and end (one-shots).
- [ ] Last frame is a calm resting/settle pose (it is held twice as long).
- [ ] Named exactly per the catalog, lower-case with underscores.
- [ ] Readable as a bold silhouette at ~64 px.

Minimum viable avatar: `idle_loop.png`. Recommended first pass: idle, active,
walking, running, combat, skilling, logged_out, inventory_full, low_health,
low_prayer, afk_warning. Then add per-skill sheets, combat styles, the one-shots
(teleport / GE / level-up / agility / death), and idle variants.
