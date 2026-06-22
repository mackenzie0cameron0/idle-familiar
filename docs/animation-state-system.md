# Idle Familiar — Animation & State System (current behaviour)

> Scope: this describes the **Idle Familiar** RuneLite plugin, which decides what the
> avatar does. "Golden Toad" is the separate companion desktop app the plugin
> *broadcasts* to over a local TCP socket; the broadcast mapping is covered in the
> last section. Everything else here is the plugin's own logic.

The system answers four questions every game tick:

1. **What is the player doing?** (read client events → activity flags)
2. **Which avatar state does that map to?** (priority ladder → one `AvatarState`)
3. **Which sprite sheet plays for that state?** (state + skill label → asset → variant)
4. **How does that sheet advance / loop / stop?** (time-based playback)

It is worth saying up front: there is **no real state machine**. There are no
enter/exit/transition events. Every tick the plugin throws away the previous
decision and recomputes the current state from a set of boolean flags and a few
linger timers. The renderer then independently notices when the chosen sheet
changed. This is the root of the behaviour that needs foundational work, and the
"Limitations" section spells out why.

---

## 1. Reading the client — event subscriptions

All input comes through RuneLite `@Subscribe` handlers in `IdleFamiliarPlugin`.
None of them directly choose an animation; they only push facts into two
trackers and a couple of linger counters.

| Event | What it reads | What it does |
|---|---|---|
| `onGameTick` | logged-in state, player, vitals, inventory | The heartbeat. Advances the tick clock, recomputes everything, picks the state, refreshes the cached skill icon, broadcasts. |
| `onStatChanged` | XP/level/boosted-level change for a `Skill` | Detects level-ups (one-shot to the toad). Routes combat-skill XP to combat; **ignores passive skills** (`prayer`, `hitpoints`); otherwise records a *skilling signal* with the skill name as the label. |
| `onAnimationChanged` | local player's current animation id | If the player is animating (`getAnimation() != -1`), records a generic *skilling signal* and remembers fishing/mining/woodcutting for the toad. |
| `onInteractingChanged` | the actor the player started interacting with | Arms the **combat linger** (`combatTicksRemaining = 4`) and marks combat activity. |
| `onItemContainerChanged` | inventory container | Marks skilling (if already skilling) or an inventory event; recomputes "inventory full". |
| `onGameStateChanged` | LOGGED_IN / LOGIN_SCREEN / HOPPING | Flips the logged-in flag and forces `LOGGED_OUT` on the way out. |
| `onConfigChanged` | the `idlefamiliar` config group | Shows/hides the desktop widget; starts/stops the companion server. |

### The two trackers and the linger counters

- **`IdleStateTracker`** — pure tick arithmetic. Holds `lastActivityTick`;
  `getTicksSinceActivity()` drives `isIdle(threshold)` (default 15 ticks) and
  `isAfkWarning(threshold)` (default 60 ticks). Any `markActivity(...)` call
  resets the idle clock.
- **`PlayerActivityService`** — a bag of booleans (`loggedIn`, `lowHitpoints`,
  `lowPrayer`, `inCombat`, `skilling`, `inventoryFull`) plus an `activityLabel`
  string (e.g. `"Fishing"`) and a `currentActivity` enum. It owns the priority
  ladder (`resolveState`).
- **`SkillingActivityTracker`** — the skilling linger. Remembers
  `lastSignalTick` and the `confirmedSkill`. `isSkilling(tick, lingerTicks)` is
  simply `tick - lastSignalTick < lingerTicks`. Passive skills are never
  recorded and never overwrite the confirmed skill.
- **`combatTicksRemaining`** — a plain countdown on the plugin. Interaction
  arms it to 4; each tick without a (non-skilling) target decrements it.

So "what is the player doing" is reconstructed from: *the idle clock*, *the
combat countdown*, *the skilling linger*, and *the vitals/inventory booleans*.

---

## 2. Choosing the state — the priority ladder

Once per tick `resolveCurrentState()` calls
`PlayerActivityService.resolveState(idle, afkWarning)`, which returns the **first**
matching rule, highest priority first:

```
LOGGED_OUT  >  LOW_HEALTH  >  LOW_PRAYER  >  AFK_WARNING  >  COMBAT
            >  SKILLING  >  INVENTORY_FULL  >  PLAYER_IDLE  >  PLAYER_ACTIVE
```

This is the entire "state machine". It is stateless: the previous state has no
influence on the next one. There is no concept of *entering* SKILLING or
*leaving* COMBAT — only "what is true right now, in priority order".

`updateLingeringActivity()` is what feeds the two activity rows of that ladder
each tick:

- **Combat**: bare interaction arms `combatTicksRemaining`, **unless we are
  already skilling** (so fishing spots — which are NPCs — aren't read as combat).
  `inCombat = reactToCombat && combatTicksRemaining > 0`.
- **Skilling**: a non-idle animation, while not in a combat linger, refreshes
  the skilling linger. `skilling = reactToSkilling && isSkilling(tick, linger)`.
  While skilling, the label is set to the confirmed skill (or generic
  `"Skilling"`).

---

## 3. Choosing the sheet — state + label → asset → variant

The overlay and the desktop widget both call `plugin.getCurrentFrame()`, which
sets the playback speed from config and calls
`AnimationController.getFrame(currentAvatarState, activityLabel)`.

**Step 3a — state → asset key** (`resolveAssetName`):

| State | Asset key |
|---|---|
| `SKILLING` + real skill label | `"<skill>_loop"` (e.g. `fishing_loop`) |
| `SKILLING` + empty/`"Skilling"` | `"skilling"` |
| `PLAYER_ACTIVE` / `CUSTOM_EVENT` | `"active"` |
| `PLAYER_IDLE` / `DEFAULT` | `"idle"` |
| `AFK_WARNING` | `"afk_warning"` |
| `COMBAT` | `"combat"` |
| `INVENTORY_FULL` | `"inventory_full"` |
| `LOW_HEALTH` / `LOW_PRAYER` | `"low_health"` / `"low_prayer"` |
| `LOGGED_OUT` | `"logged_out"` |

**Step 3b — asset key → variant** (`selectVariantAnimation`): the controller
keeps a `currentAnimationKey`. **When that key changes**, it builds (and caches)
a `WeightedVariantPicker` for the key and rolls a variant; otherwise it keeps the
current variant. Variants are sibling files of the resolved primary sheet
(`idle_loop`, `idle_loop_uncommon`, `idle_loop_rare`, `idle_loop_2..9`), weighted
by `weights.json` (default weight 1). This "re-roll on key change" is the closest
thing to a state-entry hook in the whole system — but it lives in the *renderer*,
keyed off a string comparison, not off any real transition event.

**Step 3c — file → frames** (`SpriteSheetLoader` → `AvatarAnimation`): a sheet is
sliced into **square frames using the sheet height as the frame size** (a
`1792×256` sheet is seven `256×256` frames). Each frame shows for 300 ms, except
the **last frame, held 2×** (a small "settle" before the loop restarts).

---

## 4. Replaying, ending, and switching

### Replay — a free-running wall-clock loop

`AvatarAnimation.getFrame(now, speed)` maps **absolute wall-clock time** to a
frame:

```
animTime = now * speed                     // now = System.currentTimeMillis()
target   = animTime % totalDuration        // loop
return the frame whose cumulative duration contains `target`
```

The crucial consequence: **the loop is not anchored to when the state was
entered.** It is a global clock. Entering SKILLING does not start the fishing
animation at frame 0 — it starts at whatever frame the global clock happens to be
on. The animation "replays" simply because `now` keeps advancing and wraps modulo
`totalDuration`. There is no per-entry frame counter and no "play from the start".

### Ending — linger expiry, evaluated every tick

Nothing explicitly ends an animation. A state stops being shown the moment the
ladder picks a different state, which happens when the underlying signal lapses:

- **Skilling ends** when `isSkilling` goes false — i.e. `lingerTicks` (default
  45 ≈ 27 s) elapse with no new skilling signal.
- **Combat ends** when `combatTicksRemaining` counts down to 0 (4 ticks after the
  last interaction).
- **AFK/idle** flip purely on the `IdleStateTracker` thresholds.

### Switching — recomputed per tick, applied at next render

`currentAvatarState` is recomputed every `onGameTick`. The overlay/widget read it
on their own render cadence and call `getFrame` with the latest state+label. When
the resolved **asset key** changes, the controller re-rolls the variant and loads
the new sheet (cached after first load). Because playback is time-based, the
switch is visible immediately on the next paint — there is no transition,
blend, or "finish the current loop first".

### Putting it together (one tick)

```
onGameTick
  ├─ idleStateTracker.tick++
  ├─ updateMovement / updateVitals / updateInventoryFull   → booleans + idle clock
  ├─ updateLingeringActivity                                → combat & skilling flags
  ├─ resolveCurrentState  → currentAvatarState              (priority ladder)
  ├─ updateConfirmedSkillIcon (client thread, cached)
  └─ broadcastState (companion app)

render (overlay every frame / widget every 100 ms)
  └─ getCurrentFrame
       └─ AnimationController.getFrame(state, label)
            ├─ resolveAssetName            state → "fishing_loop"
            ├─ selectVariantAnimation      re-roll iff key changed
            └─ AvatarAnimation.getFrame    now % totalDuration → a frame
```

---

## 5. Limitations — why this needs foundational changes

1. **No state machine, no transitions.** State is recomputed from a flag soup
   each tick with no enter/exit. You cannot hang `start` / `end` / one-shot
   behaviour off transitions, even though `*_start.png` / `*_end.png` sheets are
   already a documented convention. Today only `*_loop` actually plays.

2. **Playback is anchored to wall-clock time, not state entry.** `now %
   totalDuration` means animations never restart on entry and one-shots are
   impossible. "Replay from the beginning", "play the cast once", "play an end
   flourish" — none are expressible without a per-entry playback clock (a frame
   counter that resets on entry and knows loop vs one-shot vs done).

3. **Activity detection is heuristic and conflated.** "Skilling" is inferred from
   *any* non-idle animation plus XP deltas; combat from *any* actor interaction.
   So emotes, eating, and teleports read as skilling; fishing spots read as
   combat until the skilling guard kicks in; passive XP had to be special-cased
   by name. There is no authoritative `(animation id, interacting target, XP
   source) → activity` mapping — only layered guesses, which is why each new skill
   tends to need another patch.

4. **The renderer owns "state entry".** Variant re-rolls happen because the
   renderer notices an asset-key string changed, using shared mutable fields
   (`currentAnimationKey` / `currentVariant`) read from both the overlay (client
   thread) and the widget (EDT) without synchronisation. Transition logic has
   nowhere clean to live.

5. **The skill label is overloaded.** One string is simultaneously the activity
   label, the animation asset key, and the badge key. It is brittle and couples
   detection, rendering, and UI.

6. **Mixed time units.** Idle/AFK/skilling reason in game ticks; animation
   reasons in milliseconds; combat is a raw tick counter. There is no single
   clock the whole system agrees on.

---

## 6. Suggested shape of a foundational redesign

A genuine fix would separate three concerns that are currently tangled:

- **An `ActivityResolver`** that turns client facts into a small, explicit set of
  activities with documented precedence and an authoritative mapping from
  animation ids / interaction target types / XP sources to a specific activity
  (fishing, mining, combat, …) — replacing the "any animation = skilling" guess.

- **An `AnimationStateMachine`** with real `onEnter` / `onExit` / `onTick`
  transitions, so a state can declare an optional `start` (one-shot) → `loop` →
  `end` (one-shot) sequence, and so variant selection happens exactly once on
  entry.

- **A per-entry playback clock** inside `AvatarAnimation` (frames advanced by a
  counter seeded at entry, with loop / once / hold-last semantics) instead of
  `now % totalDuration`, so replay, one-shots, and transitions all become
  expressible and deterministic.

Detection, state, and rendering would then each have one job, and adding a new
activity or a transition animation would be a data change rather than another
heuristic patch.

---

## 7. Appendix — the Golden Toad companion broadcast

Independently of the avatar, every tick `broadcastState()` sends a compact state
string to the Golden Toad app via `IpcServer` / `IpcStateEmitter` (TCP 7777).
`computeAppState()` collapses the `AvatarState` to the toad's vocabulary:

| Plugin state | Toad state |
|---|---|
| `AFK_WARNING` | `afk_alert` |
| `COMBAT` | `combat` |
| `SKILLING` (with known tool anim) | `fishing` / `mining` / `woodcutting` |
| `SKILLING` (tool unknown) / everything else | `idle` |

Tool identity comes from `updateSkillAnimState()` matching specific animation ids
(fishing rod 623 / barbarian 6710, mining 624, woodcutting 879). Two one-shot
events are also emitted directly: `level_up` (when a real level rises) and
`death` (boosted HP hits 0). The toad refreshes a per-state TTL, which is why an
unchanged looping state is re-sent every few ticks.
