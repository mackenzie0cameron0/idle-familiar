# Idle Familiar

Idle Familiar is a RuneLite plugin that displays a small desktop avatar that
reacts to what you are doing in Old School RuneScape - skilling, combat, banking,
teleporting, idling, and more. It is a passive status companion, with a focus on
idle/AFK awareness and a fully swappable animation set.

> Screenshots / GIFs: _add before Plugin Hub submission_ (desktop widget and a
> couple of skill animations).

## Features

- Always-on-top desktop avatar widget with drag-to-place positioning.
- Activity detection: per-skill skilling animations, combat, banking, teleporting,
  Grand Exchange, walking/running, inventory-full, low HP, low prayer, idle, and
  AFK warning.
- Sprite-sheet animations with **weighted random variants** that re-roll every
  loop, so a state can show several different animations (`weights.json`).
- **One-shot animations** (teleport, Grand Exchange, agility, level-up) play their
  full cycle once even though the underlying state ends immediately.
- **External drop-in folder** - add or swap animations at runtime with no rebuild
  (see `ADDING_ANIMATIONS.md`).
- Desktop widget info panel: HP, prayer, inventory count, and XP/hour, opacity-
  aware, and it stays visible when the avatar is collapsed.
- Optional bundled **sound cues** for animation starts/ends, game events, and chat
  messages, with category toggles, volume control, and an expandable OSRS-inspired
  sound bank.
- Configurable chat-message filters: enter a distinctive three-or-more-word phrase
  and matching chat lines can trigger a desktop notification, custom avatar event,
  and optional chat sound.
- Debug **Preview animation** selector to force-render any state or skill while
  iterating on art.

## Animations

Animations are PNG sprite sheets, one row, square frames (frame size = sheet
height), sliced as `width / height` frames. Drop replacements into your RuneLite
home at `idle-familiar/avatar/` (created on first run) or bundle them in
`src/main/resources/com/idlefamiliar/avatar/default/`. The drop-in folder wins.

Name a sheet `<state>_loop.png` (e.g. `idle_loop.png`, `combat_loop.png`); per
skill, use `<skill>_loop.png` (e.g. `fishing_loop.png`), which falls back to
`skilling_<skill>.png` then the generic `skilling_loop.png`. Add weighted variants
as `<base>_loop_uncommon`, `<base>_loop_rare`, and `<base>_loop_2` .. `_9`, with
relative weights in `weights.json`. Full details and a checklist are in
`ADDING_ANIMATIONS.md`.

## Compliance boundaries

Idle Familiar is visual-only. It does not automate gameplay, send clicks or menu
actions, simulate keyboard or mouse input, path the player, switch prayer or gear,
or provide any bot logic. It makes **no network connections** - it only reads
normal RuneLite client state and events to draw the informational desktop widget,
and reads/writes PNG and `weights.json` files in its own avatar folder. Sound cues
are short bundled WAV files played locally by the plugin.

## Development

Run the unit tests:

```powershell
.\gradlew.bat test
```

Launch RuneLite in developer mode with the plugin loaded:

```powershell
.\gradlew.bat run
```

The desktop widget renders outside the RuneLite client as a transparent,
always-on-top Swing window and can be dragged around the desktop.
