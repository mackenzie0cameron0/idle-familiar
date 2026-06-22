# Handoff — Idle Familiar Activity-Detection Redesign

**Date:** 2026-06-15
**Status:** Unit tests green (57). **In-game behavior NOT verified by the author** (no game client available in the dev environment). User reports two issues still present in-game — see "Open issues".

This doc is self-contained: a fresh engineer (or a fresh Claude session) should be able to continue from here without re-reading the chat history.

---

## 1. TL;DR

We replaced the old "any non-idle animation = skilling" heuristic with:
- An **animation-ID whitelist** (`ActivityAnimationRegistry`) built by reflection over `net.runelite.api.gameval.AnimationID`.
- **Per-tick polling** of the live player animation as ground truth for "skilling now".
- A short **linger** (default now **2 ticks**) that only bridges sub-second gaps.
- **Movement cancels** skilling instantly.
- New avatar states: `WALKING`, `RUNNING`, `TELEPORTING`, `GRAND_EXCHANGE`.
- External drop-in animation folder (`~/.runelite/idle-familiar/avatar/`).

The implementation compiles and all 57 unit tests pass. **The remaining problems are in-game timing/feel issues that can only be diagnosed by observing the running client** — that is the crux of this handoff.

---

## 2. Open issues (what's still reported broken)

> The author cannot run the OSRS client in the dev sandbox, so these are reported by the user and were reasoned about analytically, not observed.

### Issue A — Fishing flicker (a real bug was found & fixed; re-verify)
- **Root cause found:** the sustained "fishing on the spot" animation is `HUMAN_FISH_ONSPOT` (id 623, + `HUMAN_FISH_ONSPOT_PEARL*`). The whitelist prefix was `HUMAN_FISHING`, which does **not** match the `HUMAN_FISH_` stem, so the main fishing loop was invisible to detection. Only sparse casts (`HUMAN_FISHING_CASTING` 622) + XP drops registered → flicker.
- **Fix applied:** added prefix `HUMAN_FISH_ONSPOT` in `ActivityAnimationRegistry.buildSkillPrefixes()` (tests: `ActivityAnimationRegistryTest.mapsTheSustainedFishingOnSpotAnimation`, `doesNotMisclassifyTheFishSizeCutsceneAnimation`).
- **Status:** should be fixed; **needs in-game re-test after a rebuild.** If it still flickers, capture the per-tick animation log (Section 6) while fishing and check whether 623 (or whatever id your fishing method uses) is actually being returned by `getAnimation()` every tick.

### Issue B — Fletching plays "~3 animations too many" after the activity ends
- **Not root-caused.** With the current linger (2 ticks ≈ 1.2s) and an 8-frame fletching sheet (~2.7s/cycle), a 3-cycle (~8s) tail can **not** come from the linger alone. So one of these is true:
  1. The **in-game character is still animating** fletching while the avatar shows it — in which case the avatar is *correctly* reflecting the game, and the user's "activity ended" is earlier than the game's animation end. Fixing this means deliberately stopping *before* the real animation ends (trade accuracy for snappiness).
  2. The **avatar keeps animating after the in-game character has stopped** — a real bug. Most likely suspect: the **wall-clock playback anchor** in `AvatarAnimation.getFrame` (`target = (now*speed) % totalDuration`), which the original design doc explicitly listed as unfinished/non-goal. The animation free-runs on wall-clock and is not re-anchored to state entry; this can make loops look misaligned but should *not* by itself extend the tail. Worth confirming.
- **THE KEY DIAGNOSTIC QUESTION (unanswered):** When fletching ends, does the avatar keep fletching **after** the in-game character has visibly stopped, or do they stop together? The answer selects the fix path (1 vs 2). Get this first.

---

## 3. Detection pipeline (current design)

Per `IdleFamiliarPlugin.onGameTick` order:
```
updateMovement(localPlayer)          // sets walking/running; if moved tile -> skillingTracker.cancelLinger(), movedThisTick=true
updateSkillingFromAnimation(lp)      // per-tick: if getAnimation() is whitelisted -> markSkillingSignal(label)
updateTransientStates()              // teleport / GE linger counters
updateVitals() / updateInventoryFull()
updateLingeringActivity(localPlayer) // combat linger; sets skilling = tracker.isSkilling(tick, lingerTicks)
resolveCurrentState()                // PlayerActivityService priority ladder
```
Signal sources that refresh the skilling linger:
1. `updateSkillingFromAnimation` (per-tick live animation) — primary for sustained skills.
2. `onAnimationChanged` (event) — catches brief one-shot animations between tick boundaries; also sets TELEPORTING.
3. `onStatChanged` (XP drop) — authoritative label for skills with NO clean animation namespace (combat/ranged/magic/etc.).

`SkillingActivityTracker`: holds `lastSignalTick` + `confirmedSkill`. `isSkilling(tick, linger) = (tick - lastSignalTick) < linger`. `cancelLinger()` zeroes it (used on movement).

Linger config: `IdleFamiliarConfig.skillingLingerTicks()` default **2**, range 1–200. It is the post-stop tail AND the inter-action bridge — a single global value, which is the core tension (see Section 5).

---

## 4. Why the whitelist works the way it does (important context)

The original design doc assumed `AnimationID` had clean `HUMAN_<SKILL>_*` namespaces. **It does not.** Reality (RuneLite 1.12.28): ~13,900 constants named by tool/action, not skill. Clean, start-anchored, unambiguous prefixes exist only for the skills in `ActivityAnimationRegistry.SKILL_PREFIXES`:

```
HUMAN_WOODCUTTING, HUMAN_MINING, HUMAN_FISHING, HUMAN_FISH_ONSPOT, HUMAN_HARPOON,
HUMAN_SMALLNET, HUMAN_SMITHING, HUMAN_FLETCHING, HUMAN_HERBING, HUMAN_COOKING,
HUMAN_RUNECRAFT, HUMAN_FARMING, HUMAN_PICKPOCKET
```

Combat/Ranged/Magic/Hunter/Firemaking/Agility/Construction etc. have **no** clean prefix; their label comes from XP drops. **Lesson learned the hard way:** a skill's *sustained* animation may use a different stem than you'd guess (fishing = `HUMAN_FISH_ONSPOT`, not `HUMAN_FISHING_ONSPOT`). **If any other skill misbehaves, the FIRST thing to check is whether its real in-game animation id is actually covered by a prefix** (Section 6 tells you how to find the id).

To list the real ids for a skill:
```
JAVAP="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2/bin/javap.exe"
API=$(find ~/.gradle -name 'runelite-api-1.12.28.jar' | grep -v sources | head -1)
"$JAVAP" -cp "$API" -constants -p net.runelite.api.gameval.AnimationID | grep -iE "FLETCH|COOK|..."
```

---

## 5. The core design tension (read before "fixing" the tail)

A single global `lingerTicks` does two jobs:
1. **Bridge** the gaps *between* repeated actions (so the avatar doesn't flicker mid-activity).
2. Be the **tail** shown *after* the activity stops.

These pull in opposite directions. Per-tick polling helps a lot for **continuous** animations (mining, woodcutting, fishing-on-spot) — those are present every tick, so the linger can be tiny and the tail is ~immediate. But **click-per-item** skills (fletching, some cooking) have real animation gaps; if the linger is below the gap they flicker, above it they leave a tail.

If you need both snappy stop AND no flicker, a single global linger is insufficient. Options:
- **Per-activity linger** (small map of label → ticks).
- **Target-based sustain for NPC skills** (fishing/thieving): while `getInteracting()` is the same fishing-spot NPC, keep skilling alive regardless of animation gaps; drop instantly when interaction ends. (Was scoped but NOT implemented — a clean NPC fishing-spot list doesn't exist in gameval, but you don't need ids: "interacting NPC + a skilling animation was recently confirmed" is enough to disambiguate from combat.)
- **Fix the playback anchor** so the avatar animation completes/zeroes on state transitions instead of free-running on wall-clock.

---

## 6. How to actually diagnose (in-game instrumentation)

The bottleneck is that these are timing bugs only visible in the client. Add temporary per-tick logging, gated behind the existing `debugState` config, at the end of `onGameTick`:

```java
if (config.debugState() && client.getLocalPlayer() != null)
{
    int anim = client.getLocalPlayer().getAnimation();
    log.info("tick={} anim={} regLabel={} moved={} skilling={} state={}",
        idleStateTracker.getCurrentTick(),
        anim,
        activityRegistry.getActivityForAnimation(anim).orElse("-"),
        movedThisTick,
        activityService.isSkilling(),
        currentAvatarState);
}
```

Then: enable **Debug state** in the plugin config, fish / fletch, and read the RuneLite client log. You will see exactly:
- which animation id the game reports each tick,
- whether the registry recognises it,
- when skilling flips on/off and the resolved state.

That log makes both issues self-evident (e.g. "fishing returns anim=X every tick but regLabel=- " → X is missing from the whitelist; or "anim=-1 for 2 ticks then back" → it's a bridge-gap/flicker tuning issue; or "anim=1248 still firing 12 ticks after I clicked away" → the game is genuinely still animating).

---

## 7. Build / test (Gradle is blocked in the sandbox; here's the workaround)

- **Gradle does NOT run in the author's sandbox**: wrapper 9.2.1 daemon + forked test worker fail with `Unable to establish loopback connection` (even `--no-daemon`, even sandbox-off). **This is almost certainly a sandbox-only restriction — Gradle/IntelliJ should work normally on a real desktop.**
- **No-Gradle test harness** (compiles main+test against cached jars, runs JUnit directly): `C:\Users\BWAMAC~1\AppData\Local\Temp\if_test.sh`. Gotchas baked in: javac `@argfile` treats `\` as an escape (use `cygpath -m`), classpath too long for the command line (use argfiles / `CLASSPATH` env). JDK at `~/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2`.
- **In-game launch without Gradle:** `run-idle-familiar.ps1` in the project root — recompiles from source and launches RuneLite (`--developer-mode --debug`) with the plugin as a builtin. Use it to guarantee you're testing current code.
- **In IntelliJ:** set Gradle JVM + Project SDK to JDK 21; run the Gradle `run` task (recompiles + launches), or run `IdleFamiliarPluginTest.main` with args `--developer-mode --debug` on the test module classpath. `Build → Rebuild Project` to force-clean.

> NOTE: a `build/` directory with stale compiled `.class` files exists in the project from an earlier session. If the client loads old behavior, do a clean rebuild so stale classes aren't picked up.

---

## 8. File-by-file change map

| File | Change |
|---|---|
| `ActivityAnimationRegistry.java` | **New.** Reflection whitelist (`getActivityForAnimation`, `isTeleportAnimation`, `build()`). **Fishing fix lives here** (`HUMAN_FISH_ONSPOT` prefix). |
| `SkillingActivityTracker.java` | Added `cancelLinger()`. |
| `PlayerActivityService.java` | Added WALKING/RUNNING/TELEPORTING/GRAND_EXCHANGE flags + priority ladder. |
| `AvatarState.java` | +4 enum values. |
| `AnimationController.java` | +4 asset keys; external drop-in folder loading (`setExternalAssetDir`, `openAsset`, `assetExists`, `loadSheet`). |
| `SpriteSheetLoader.java` | Added `load(InputStream, name, dur)` overload. |
| `IdleFamiliarConfig.java` | `skillingLingerTicks` default 45→**2**, range min→1, new description. |
| `IdleFamiliarPlugin.java` | Per-tick `updateSkillingFromAnimation`; movement-cancel + `movedThisTick`; whitelist-driven `onAnimationChanged`; `onGrandExchangeOfferChanged`; teleport/GE linger counters; removed old `ANIM_*` constants + heuristic refresh. |
| `ADDING_ANIMATIONS.md` | New guide for the external animation folder. |
| Tests | `ActivityAnimationRegistryTest` (new), plus additions to `SkillingActivityTrackerTest`, `PlayerActivityServiceTest`, `AnimationControllerTest`. 57 total, all green. |

Animation sheet facts: `fishing_loop.png` 1792×256 = 7 frames (~2.4s/cycle); `fletching_loop.png` 2048×256 = 8 frames (~2.7s/cycle). Frames are square (= sheet height), sliced left→right.

---

## 9. Concrete next steps (in order)

1. **Rebuild & relaunch** via the Gradle `run` task or `run-idle-familiar.ps1`. Confirm you're on current code (delete stale `build/` if unsure).
2. **Add the Section 6 logging**, enable Debug state, and capture a log while **fishing** and while **fletching**.
3. **Fishing:** confirm 623/your-method's id now shows `regLabel=Fishing` every tick. If a method still flickers, find its real id in the log and add the matching prefix to `SKILL_PREFIXES` (+ a test).
4. **Fletching:** answer the key diagnostic question (avatar stops with the character, or after?). Then:
   - If after, and it's the wall-clock anchor → re-anchor `AvatarAnimation` playback to state entry.
   - If the game is genuinely still animating → decide product-side whether to cut early (per-activity linger) or accept it.
5. Consider implementing **target-based sustain** for fishing/thieving (Section 5) if per-tick + linger still feels off — it's the most accurate model for NPC-interaction skills.
6. Re-run the harness (`bash if_test.sh`) or IntelliJ tests after any change; keep them green.

---

## 10. Honest status line

- Compiles: **yes** (against real RuneLite 1.12.28 API).
- Unit tests: **57 passing.**
- In-game verified by author: **no** (no client access here).
- Known-good in-game: **unconfirmed.** User reports fishing flicker (a real bug was fixed; re-verify) and a fletching tail (not root-caused; needs the diagnostic in Section 6).
Treat anything not covered by a unit test as unverified until you watch it in the client with the Section 6 log on.
