# Findings & Optimizations — Activity-Detection (Session 2)

**Date:** 2026-06-15
**Continues:** `HANDOFF-activity-detection.md`
**Scope:** Verify the animation-ID detection approach, root-cause the two reported in-game issues, and optimize the pipeline. Code changes below compile-clean by review but were **not** run through the test harness in this environment (no JDK/`javac` available here) — run the harness or IntelliJ tests locally to confirm green.

---

## 1. Is the approach correct? — YES, with caveats

Reading `localPlayer.getAnimation()` each tick and mapping it via `net.runelite.api.gameval.AnimationID` is the **correct, idiomatic RuneLite technique** for "what is the player doing right now." The animation ID is the game's own ground truth; nothing else (XP, interaction, inventory) tells you *which* sustained skilling action is on screen this frame.

I verified the registry against the **real `AnimationID` constants** (extracted from the bundled `net/runelite/api/gameval/AnimationID.class` inside `build/libs/idle-familiar-0.1.0-all.jar`). The prefix-reflection families are sound:

| Prefix | Real constants | Verdict |
|---|---|---|
| `HUMAN_WOODCUTTING` | 15 (all `_<TIER>_AXE`) | clean |
| `HUMAN_MINING` | 28 (all `_<TIER>_PICKAXE`) | clean |
| `HUMAN_FISHING` + `HUMAN_FISH_ONSPOT` + `HUMAN_HARPOON` + `HUMAN_SMALLNET` | 6+5+7+1 | clean (ONSPOT fix confirmed correct) |
| `HUMAN_COOKING` | 2 (`COOKING`, `COOKING_LOOP`) | clean |
| `HUMAN_RUNECRAFT`, `HUMAN_FARMING`, `HUMAN_PICKPOCKET`, `HUMAN_HERBING` | small, on-skill | clean |
| `HUMAN_SMITHING` | 5 — **includes `_IDLE` and `_ENTER`** | **false positives (fixed)** |
| `HUMAN_FLETCHING` | 7 — **includes `_NO_ITEMS`** | **false positive (fixed)** |

So the *method* is right; the *prefix sweep was slightly too greedy* — it swept in non-active poses (see §3A).

---

## 2. Root causes of the two reported issues

### Issue A — Fishing flicker → real bug was the missing `HUMAN_FISH_ONSPOT` stem
The source fix is correct and verified against the real constants. **But re-test only after a clean rebuild** — see the stale-artifact finding below, which can mask the fix entirely.

### Issue B — Fletching plays "~3 animations too many" → NOT the linger; it's a stale build / persisted config
With `skillingLingerTicks = 2` (~1.2 s), the detection layer **mathematically cannot** produce an ~8 s tail: `isSkilling = (tick − lastSignalTick) < lingerTicks`, and `lastSignalTick` is only ever written by a genuine skilling signal. Once fletching stops, the window closes in < 2 ticks. Verified there is no other code path (inventory change, combat linger, XP drops) that secretly extends it.

The tail is explained by **build/config staleness**, confirmed by disassembling the actual artifacts:

| Artifact | `skillingLingerTicks` default in bytecode | Age |
|---|---|---|
| `build/libs/idle-familiar-0.1.0-all.jar` (the **sideloadable fat jar**) | **45** (= 27 s, the OLD default) | Jun 1 |
| `build/libs/idle-familiar-0.1.0.jar` (slim jar) | **3** | Jun 15 20:18 (older than the source fix at 20:28) |
| current source / `build/classes` | **2** | current |

If RuneLite is loading the prebuilt fat jar, the user is running **27-second-linger code without the fishing fix** — which matches *both* reported symptoms exactly. Separately, RuneLite **persists config per profile**: if `skillingLingerTicks` was ever set to a large value, the new code default of `2` is ignored and the stored value wins.

**→ The single highest-impact action: delete stale build artifacts, rebuild from source, and reset the stored config value (see §5).** `run-idle-familiar.ps1` already recompiles from source each launch, so launching via that script side-steps the stale jar — a good way to confirm.

---

## 3. Optimizations implemented (this session)

### A. Registry hardening — exclude non-active poses
`ActivityAnimationRegistry` now drops names ending in `_IDLE`, `_ENTER`, `_NO_ITEMS` after a skill prefix matches, so `HUMAN_SMITHING_IDLE` (standing at an anvil), `HUMAN_SMITHING_ENTER`, and `HUMAN_FLETCHING_NO_ITEMS` no longer read as active skilling. Suffix-token based (not hard-coded IDs), so future variants are filtered automatically. Tests added: `excludesNonActivePoseAnimations`, `stillMapsTheActiveAnimationsOfPartlyExcludedSkills`.

### B. Target-based sustain for NPC skills (the structural fix for the §5 tension)
A single global linger has to be both the *bridge* between repeated actions and the *tail* after stopping — opposing goals. For NPC-interaction skills (fishing, thieving) these are now **decoupled** using interaction identity:

- While `getInteracting()` keeps returning the **same actor** that a skilling animation was confirmed against, skilling stays alive across animation gaps **regardless of the linger** (no mid-action flicker).
- The instant the target changes or clears (spot depletes, you walk away), the bridge ends **immediately** (crisp tail).

Lives in `SkillingActivityTracker` (`recordInteractionTarget`, `isSustainedByTarget`) as pure POJO logic; the plugin feeds it `System.identityHashCode(getInteracting())` from `updateLingeringActivity`. No fishing-spot ID list needed; combat can't trigger it (it requires a *recently confirmed skilling animation*, which combat never produces). Tests added: bridges-gaps, drops-on-end, requires-confirmed-signal, cancel-ends-sustain. Note: trees/rocks/ranges are GameObjects (not Actors) so `getInteracting()` is null for them — woodcutting/mining/cooking are unaffected and keep using per-tick polling, which is already snappy for continuous animations.

### C. In-game diagnostic logging (Section 6 of the handoff)
Added `logDebugState()` at the end of `onGameTick`, gated behind the existing `debugState` config. Each tick logs `anim`, registry label, `moved`, `skilling`, `sustained`, resolved `state`, and `label`. This is the tool that turns the remaining "timing only visible in the client" questions into a readable log — enable **Debug state**, fish/fletch, and read the RuneLite log.

---

## 4. Recommended but NOT implemented (deliberate)

- **Per-activity linger map** (e.g. `Fletching → 6 ticks`) for non-NPC click-per-item skills. Only worth adding if the Section-6 log shows fletching genuinely flickering at linger 2 *after* the rebuild. Don't add it blind — it trades snappiness for smoothness.
- **Re-anchor the avatar playback** in `AvatarAnimation.getFrame` (it free-runs on `System.currentTimeMillis()` so loops can start mid-cycle). This is a cosmetic *phase* issue only — it does **not** extend the tail (the played sheet is chosen by `currentAvatarState`, which flips immediately on state change). Lower priority; left as a follow-up to avoid destabilising `AnimationController`'s tests without an in-game check.

---

## 5. Do this first (in order)

1. **Clean the stale build.** Delete `build/` (at minimum `build/libs/*.jar`) so RuneLite cannot sideload the Jun-1 fat jar that hard-codes linger 45 and lacks the fishing fix. Rebuild from current source.
2. **Reset the persisted config.** In RuneLite, reset **Skilling linger** to its default (or delete the stored `idlefamiliar.skillingLingerTicks` value) — a previously-saved value overrides the new code default of 2.
3. **Relaunch via `run-idle-familiar.ps1`** (compiles from source) to guarantee current code.
4. **Enable Debug state**, then fish and fletch while watching the RuneLite log. Confirm: fishing shows `regLabel=Fishing`/`sustained=true` continuously; fletching's `skilling=false` within ~2 ticks of the animation ending. If fletching still tails, the log will show whether the *game* is still animating (`anim=` non-`-1`) — that's a product decision, not a bug.
5. **Run the unit tests** (`bash if_test.sh` or IntelliJ) and confirm green. New tests: 2 in `ActivityAnimationRegistryTest`, 4 in `SkillingActivityTrackerTest`.

---

## 6. Files changed this session

| File | Change |
|---|---|
| `ActivityAnimationRegistry.java` | `EXCLUDED_POSE_SUFFIXES` + `isExcludedPose()`; guard in `build()` to drop idle/enter/no-items poses. |
| `SkillingActivityTracker.java` | `recordInteractionTarget()`, `isSustainedByTarget()`, `NO_TARGET`; `isSkilling()` honours sustain; `cancelLinger()`/`reset()` clear target state. |
| `IdleFamiliarPlugin.java` | `Actor` import; feed interaction identity into the tracker in `updateLingeringActivity`; `logDebugState()` per-tick debug log. |
| `ActivityAnimationRegistryTest.java` | +2 pose-exclusion tests. |
| `SkillingActivityTrackerTest.java` | +4 target-sustain tests (file was also repaired — a tail region had on-disk corruption; see note). |

> **Disk note:** `SkillingActivityTrackerTest.java` had a corrupted tail on the `H:` drive (binary/MFT data had overwritten the final test). It was rewritten cleanly. If corruption recurs on `H:`, consider running `chkdsk` — it suggests a filesystem/sector issue rather than anything in the code.
