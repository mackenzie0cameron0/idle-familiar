# Code Cleanup Tasks - Idle Familiar

**Audience:** a Claude Code agent performing a cleanup pass on this RuneLite plugin.
**Goal:** reduce dead code, fix inconsistencies, tighten thread-safety, and get the
project Plugin-Hub-presentable - **without changing runtime behavior** unless a task
explicitly says so.

---

## Ground rules (read first)

1. **Investigate before you delete.** This codebase has evolved across many sessions;
   a symbol that looks unused may be wired up indirectly (config enums, Guice
   injection, test-only access). Always `grep` the whole `src/` tree (main AND test)
   for every symbol before removing it.
2. **Keep the tests green.** Run `./gradlew test` before and after. If a change
   requires a test update, update the test to match the new reality and explain why
   in the commit - do not weaken an assertion just to make it pass.
3. **Verify with a real build.** `./gradlew build` (compile + checkstyle + test). The
   plugin targets the RuneLite Plugin Hub, so a clean checkstyle pass matters.
4. **Do not touch art intent.** `weights.json` values and which sheets exist are the
   author's creative decisions. You may remove provably-dead/duplicate/typo'd image
   files (see Task A), but do not re-balance weights or delete valid art.
5. **Behavior-preserving by default.** Detection logic (`SkillingActivityTracker`,
   `PlayerActivityService.resolveState`, the `IdleFamiliarPlugin` tick pipeline) is
   load-bearing and was tuned over many iterations. Treat it as frozen unless a task
   targets it.
6. **Commit in small, reviewable steps**, one task (or sub-task) per commit.

---

## Task A - Remove dead / orphaned / mis-named asset files

Location: `src/main/resources/com/idlefamiliar/avatar/default/`

These were found by comparing the files on disk against how `AnimationController`
resolves names (`resolveAssetName`, `resolvePrimaryBase`, `discoverVariants`).

- [ ] **`theiving_loop.png`** - misspelling of "thieving". `thieving_loop.png` (the
      correct one) also exists, so this file is never loaded. Delete it.
- [ ] **`idle_uncommon.png`** - orphan. Variant discovery for the `idle` state keys
      off the primary base `idle_loop`, so it looks for `idle_loop_uncommon` (which
      exists). A bare `idle_uncommon` is never discovered. Delete it.
- [ ] **Legacy bare duplicates** - `idle.png`, `banking.png`, `afk_warning.png`
      exist alongside `idle_loop.png`, `banking_loop.png`, `afk_warning_loop.png`.
      The `_loop` form wins (`resolvePrimaryBase` tries `_loop` first), so the bare
      ones are dead fallbacks. Confirm by checking `resolvePrimaryBase`, then delete
      the bare duplicates **only where a `_loop` twin exists**.
- [ ] **Do NOT delete** the bare names that have no `_loop` twin and are the actual
      primary: `inventory_full.png`, `low_health.png`, `low_prayer.png`,
      `teleporting.png`. (Optionally rename them to `_loop` for consistency - see
      Task E - but that is a naming change, not a deletion.)
- [ ] **Keep `prayer_loop.png`** - it is NOT orphaned. Prayer is an XP-detected skill
      (`recordXpSkillSignal`), so `resolveAssetName(SKILLING, "Prayer")` -> `prayer_loop`.

After deleting, run the in-plugin **"Validate animations"** action (or call
`AvatarAssetValidator.validate(folder)`) and confirm zero issues, then `./gradlew test`.

> Note: `AvatarAssetValidator` will NOT flag `theiving_loop` or `idle_uncommon`
> automatically (the former isn't a variant pattern; the latter's base `idle.png`
> currently exists). Consider improving the validator to also report **unreferenced
> sheets** - a `.png` whose base name maps to no avatar state, skill, or discoverable
> variant. That would have caught both. (Optional, see Task F.)

---

## Task B - Find and resolve unreachable code from the overlay removal

The in-client overlay was removed (the plugin is now desktop-widget-only); there is
no `IdleFamiliarOverlay` class anymore.

- [ ] **Verify the reload/validate triggers still exist.** Those actions
      (`AnimationController.reloadAnimations()` and the "Validate animations" path in
      `IdleFamiliarPlugin`) were originally wired to overlay right-click menu entries.
      Grep for who calls them now. If they are no longer reachable by the user,
      either (a) re-wire them to a reachable trigger (a config toggle/button, a chat
      command, or a menu on the desktop widget), or (b) if intentionally dropped,
      remove the now-dead methods and `AvatarAssetValidator` wiring. **Decide with
      the maintainer which** - do not silently delete a useful feature.
- [ ] **Prune stale doc-comments referencing the overlay.** e.g.
      `IdleFamiliarPlugin.getConfirmedSkill()` javadoc says "Used by the overlay to
      draw the matching skill icon badge" - the overlay is gone; the desktop widget
      uses it now. Grep comments for "overlay" and fix the wording.

---

## Task C - Thread-safety audit (client thread vs Swing EDT)

The desktop widget (`DesktopPetWindow`) paints on the **Swing EDT** and is now the
**only** renderer, while plugin state is mutated on the **client thread**. The author
already made the vitals `volatile` and recently `currentAvatarState`, `combatStyle`,
and `PlayerActivityService.activityLabel` were made `volatile` for this reason.

- [ ] **Audit every value the widget reads** during paint and make the cross-thread
      access safe. Start from `DesktopPetWindow.paintComponent` and follow each
      `plugin.getX()` / `config.x()` call. Anything that reads mutable plugin/service
      state written on the client thread must be either `volatile`, an immutable
      snapshot, or guarded. Candidates to check: everything in `PlayerActivityService`
      reached via `getCurrentMessage()` / `getCurrentActivity()` /
      `getActivityLabel()`, and `IdleFamiliarPlugin.getConfirmedSkillIcon()`
      (already volatile) / `getAdvancedInfoText()` (`cachedXpHr`, already volatile).
- [ ] **Preferred pattern:** rather than scatter `volatile` across many fields,
      consider building a single immutable "widget snapshot" object on the client
      thread each tick (state, label, icon, hp, prayer, inv, xpHr, message) and
      publishing it via one `volatile` reference the widget reads. Cleaner and removes
      the per-field race surface. Treat as a refactor - keep behavior identical and
      keep tests green.

---

## Task D - Documentation cleanup / consolidation

`docs/` has overlapping and stale files:

- [ ] **Stale (pre-refactor) - prune or clearly archive:**
      `HANDOFF-activity-detection.md`, `FINDINGS-activity-detection-session2.md` -
      these describe the old in-client overlay, the removed IPC/"Golden Toad"
      companion app, and old linger values (27s). Mark them clearly as historical or
      delete; do not let them masquerade as current.
- [ ] **Overlapping animation-state docs:** `ANIMATION-STATES.md`,
      `animation-state-system.md`, and `AVATAR-ANIMATION-SPEC.md` cover similar
      ground. Decide on ONE canonical doc (the spec is the most current) and fold in
      anything unique from the others, then remove the duplicates.
- [ ] **Verify `README.md`, `ADDING_ANIMATIONS.md`, `FUTURE-IMPROVEMENTS.md`** match
      the current code (desktop-only, no IPC, no AFK interaction-timer, sounds present,
      chat filters present). Fix any drift.

---

## Task E - Naming / consistency (low risk, optional)

- [ ] **Standardize sheet names on `_loop`.** Rename the bare primaries
      (`inventory_full`, `low_health`, `low_prayer`, `teleporting`) to `*_loop.png`
      so all states follow one convention. The loader already accepts `_loop`, so this
      is safe - but update any doc/example that lists the bare names.
- [ ] **Check `ActivityType`** for values that are never set/consumed (it was trimmed
      before; confirm nothing crept back).
- [ ] **`AvatarState`** - confirm `DEFAULT` and `CUSTOM_EVENT` are still used; if
      `CUSTOM_EVENT` is wired to the chat-filter "custom avatar event" feature, keep
      it, otherwise remove.
- [ ] **Constants:** confirm `DesktopPetWindow.CARROT_SIZE` is still referenced (a
      test asserts its value); remove any other now-unused constants.

---

## Task F - Tests and coverage

- [ ] **Confirm the full suite is green** (`./gradlew test`). There are 20 test
      classes; recent churn touched sounds, variants, and the XP path.
- [ ] **Fill coverage gaps** for behavior added across recent sessions, where it can
      be tested without a live client (pure POJO logic only):
      combat sub-style selection, the LEVEL_UP / DEATH transient holds, the agility
      one-shot hold, and the one-shot latch. Mirror the existing
      `AnimationControllerOneShotTest` / `...RerollTest` style.
- [ ] **Optional validator improvement (from Task A):** add an "unreferenced sheet"
      check to `AvatarAssetValidator` and a test for it, so typo/orphan files like
      `theiving_loop` are caught automatically in future.

---

## Task G - Plugin Hub / checkstyle readiness

- [ ] **Run checkstyle** (part of `./gradlew build`) and fix violations: import order,
      unused imports, fully-qualified names that should be imports, trailing
      whitespace, line length.
- [ ] **No fully-qualified annotations/types in code** unless unavoidable - prefer an
      import. (Recent edits briefly used `com.google.inject.Inject(optional=true)`
      fully-qualified; that path was removed, but scan for similar.)
- [ ] **Confirm no external network usage and no disallowed APIs** (the plugin must
      stay visual/local-only). Grep for `Socket`, `URL`, `HttpURLConnection`,
      `Runtime.exec`, reflection into other plugins - there should be none.
- [ ] **`runelite-plugin.properties`** - verify `displayName`, `author`,
      `description`, `tags`, `version` are accurate and the description matches the
      `@PluginDescriptor`.
- [ ] **License headers** - check whether the repo/Hub requires the standard RuneLite
      BSD header on each source file; add if so.

---

## Final verification checklist

Run after each task and at the end:

```
./gradlew test      # all green
./gradlew build     # compile + checkstyle + test, no errors
```

In-client smoke test (manual, needs the RuneLite client):
- Plugin loads with no Guice/injector error.
- Avatar transitions through idle -> skilling -> combat -> walking as you play
  (enable "Debug state" and watch the `[idle-familiar] ... state=` log line).
- Idle variants visibly cycle; one-shots (teleport/GE/agility/level-up) play fully.
- Desktop widget: collapse chevron stays in a fixed spot; info panel + skill icon
  show when collapsed; HP/prayer/inv/XP-hr update.
- "Validate animations" reports zero issues after Task A.

## Do NOT

- Do not alter detection tuning (linger values, the state priority ladder ordering,
  the target-sustain logic) - it is intentional and hard-won.
- Do not delete `XpRateTracker` (it is the XP/hr source) or any class that only
  appears unused because its sole caller is a unit test.
- Do not re-introduce the in-client overlay, the IPC/companion app, or the AFK
  interaction-time estimate - all were removed on purpose.
- Do not change `weights.json` numbers or remove valid art.
