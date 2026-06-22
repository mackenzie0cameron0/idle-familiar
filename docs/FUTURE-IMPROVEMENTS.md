# Future Improvements & Feature Ideas - Idle Familiar

**Date:** 2026-06-17
**Context:** Idle Familiar is a self-contained RuneLite plugin: an animated avatar
(in-client overlay + optional always-on-top desktop widget) that reacts to what
the player is doing. The detection pipeline and the variant/animation system are
stable; the near-term focus is filling out the animation library and getting the
plugin Hub-ready. Nothing below is committed - it's a menu, roughly ordered
"polish" to "new direction."

---

## Status - what's shipped

- **Detection:** per-tick live-animation truth, an animation-ID whitelist, target-
  based sustain for NPC skills (fishing/thieving), movement cancels skilling,
  inventory-full rising edge drops skilling immediately, and the effective
  skilling linger is clamped (<= 4 ticks) so a stale config can't cause a tail.
- **State priority ladder** is centralized and documented in
  `PlayerActivityService.resolveState` - reorder the `if` rungs to retune.
- **Variants:** weighted selection from `weights.json`, re-rolled **every loop**
  (anchored playback so each loop starts on frame 0), plus a right-click
  "Reload animations" action that re-reads sheets and weights without a restart.
- **Desktop widget:** side-panel layout (HP / prayer / inventory / XP-hr), honors
  the opacity setting, stays visible when collapsed, and re-asserts always-on-top
  so it survives long sessions.
- **Removed for the public release:** the desktop-app / IPC companion link
  (now RuneLite-only), the AFK interaction-time timer, the top-left skill badge,
  and assorted dead code. The avatar's AFK_warning reaction is retained.

---

## A. Animation system

### A1. Intro / outro one-shots (`_start` / `_end`)
Play a `_start` sheet once on state entry, settle into `_loop`, and optionally
play an `_end` sheet on exit. Makes transitions feel deliberate instead of
hard-cut. (The old vestigial `_start` stub was removed, so this would be built
fresh and properly wired into `AnimationController`/`AvatarAnimation`.)

### A2. Directional / tier-aware variants
Walking-left vs walking-right, or skill-tier sheets (bronze axe vs dragon axe).
The detection layer already knows the animation ID, so the variant picker could
key off more than the skill name.

### A3. Per-sheet metadata sidecar
A small optional JSON next to a sheet to override frame duration, mark it
loop-once, or set a hold-on-last-frame value - so a fast skill and a slow idle
don't have to share one global cadence. Keeps the PNG-only path working for
simple cases.

### A4. Larger / freer variant sets
Today variants are the primary plus `_uncommon`, `_rare`, and `_2`..`_9`. Allow
arbitrary suffixes and more than nine, so a heavily-animated state (idle) can
carry a deep pool without running out of slots.

### A5. Long-idle "sleep" / special fidgets
Per-loop re-roll already gives short-term variety. A separate, longer timer could
trigger a distinct "bored/asleep" state after minutes of idle - the classic
desktop-pet touch - distinct from the AFK_warning reaction.

---

## B. Detection & accuracy

### B1. Broader activity coverage
States that exist but ship without art (fall back to idle today): `walking`,
`running`, `teleporting`, `grand_exchange`, `banking`. Plus candidates with no
state yet: agility courses, prayer-at-altar, bossing phases, questing cutscenes,
emotes. Each is an art task and sometimes a small detection hook.

### B2. Combat sub-states
Combat is one bucket. The XP-drop label already distinguishes melee / ranged /
magic, so the avatar could mirror the combat style with three sheets.

### B3. Generalized "still engaged" sustain
Target-based sustain currently only covers Actor interactions (NPCs). Extending
the same idea to GameObjects (anvils, ranges, trees) and open interfaces would
let non-NPC skills bridge gaps just as cleanly, reducing reliance on the linger
entirely.

### B4. Per-activity linger map (optional)
A `Map<skill, lingerTicks>` for click-per-item skills. Lower priority now that the
global linger is clamped; only worth it if the debug log shows a real flicker for
a specific skill.

---

## C. Presentation & UX

### C1. Speech-bubble content system
The bubble shows a fixed line per state. A small randomized pool keyed to
state + skill ("Nice catch!", "Almost full...") would add character. Keep it
toggleable and non-spammy.

### C2. Overlay & widget positioning
Drag-to-place with position remembered per profile, snap-to-corner, optional
click-through, multi-monitor awareness, and a resize handle. The widget already
supports dragging; persistence and snapping are the missing polish.

### C3. Alternate widget layouts & graphical orbs
The side panel is in; the stat-bar, flanking-orbs, and 2x2 grid mockups could be
offered as a config choice, and HP/prayer could render as real OSRS-style orbs
instead of text.

### C4. Session stats surface
`XpRateTracker` already computes per-session XP/hr. It could grow into a compact
session readout (skills trained, time active, notable events) - in-client panel or
a separate window.

### C5. Native RuneLite notifications
Optional, toggleable notifications for low HP, full inventory, or AFK threshold,
reusing the detection that already exists. Useful for players who tab out.

### C6. Click-to-pet interaction
Clicking the avatar plays a brief one-shot reaction (and a rare easter-egg). Cheap
charm that fits the desktop-pet framing.

### C7. Accessibility
High-contrast speech text, an adjustable text size, and a colorblind-safe palette
for any colored UI elements.

---

## D. Distribution & community

### D1. Theming / avatar packs
The external drop-in folder already swaps the whole sheet set. Formalize it into
named packs (a subfolder per theme + a config dropdown) so users can switch entire
avatar styles, not just individual sheets.

### D2. Shareable pack format
A zip + manifest (`weights.json` + sheets + metadata) plus an import action, so the
community can publish and install avatar packs in one step. Pairs with D1. (No
external network needed - purely local files.)

### D3. Bundled starter art
A couple of complete example packs so a first-time user sees variety immediately
and has a reference for authoring their own.

---

## E. Engineering & release readiness

### E1. Asset validation tooling
A dev/debug command that scans the avatar folder and reports malformed sheets
(width not divisible by height), orphaned variants (a `_rare` with no primary),
and `weights.json` keys pointing at missing files. Pays for itself while the
library grows.

### E2. weights.json merge instead of override
An external `weights.json` currently replaces the bundled one wholesale - drop one
file in and every bundled weight is lost. A per-key merge (external overrides
bundled, others retained) would be far less surprising.

### E3. Hot-reload of the drop-in folder
Watch the external folder and reload on change, so artists see edits live without
even the right-click reload. Builds on the existing reload path.

### E4. Config sectioning & copy pass
Group the config with `@ConfigSection` (Overlay / Desktop widget / Detection /
Debug), order items sensibly, and tighten descriptions. Small effort, big
first-impression win for a public release.

### E5. Plugin Hub readiness
README with screenshots/GIFs, plugin metadata and icon, a clean checkstyle pass,
and unit-test coverage for the changed detection paths. Now that the plugin is
RuneLite-only with no external network, Hub review should be considerably simpler -
worth confirming against the current Hub guidelines.

### E6. State-preview / test mode
A debug control to force-render a chosen avatar state (and variant) so art can be
iterated without reproducing the in-game condition.

### E7. Animation performance
Pause or throttle the repaint loop when the client is unfocused or the widget is
fully occluded, to keep idle CPU/GPU negligible.

---

## Suggested near-term order

1. Finish the core per-skill `_loop` sheets; add `_uncommon`/`_rare` variants to
   the highest-traffic states (idle, common skills) and tune `weights.json`.
2. **E1 (asset validator)** - immediate payoff while adding art.
3. **E4 + E5 (config sectioning + Hub prep)** - the plugin is close to releasable
   now that it's RuneLite-only.
4. **C2 (overlay/widget position persistence)** - the most-felt UX gap.
5. **B1 (art for walking/running + banking)** - fills the most common idle
   fallbacks.
