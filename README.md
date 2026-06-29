# Idle Familiar

Idle Familiar is a RuneLite plugin that shows a small desktop avatar reacting to
what you are doing in Old School RuneScape — skilling, combat, banking,
teleporting, idling, and more. It is a passive status companion, with a focus on
idle/AFK awareness, that lives in its own always-on-top window outside the game
client.

> Screenshots / GIFs: _add before Plugin Hub submission_ — the desktop widget plus
> a couple of skill animations.

## Features

- **Desktop avatar widget** — a transparent, always-on-top Swing window you can
  drag anywhere on the desktop. It stays on top even after long uptime.
- **Activity detection** — per-skill skilling animations, walking/running,
  combat (with melee/ranged/magic sub-styles), banking, teleporting, a filled
  Grand Exchange offer, inventory-full, low HP, low prayer, level-up, death,
  idle, and an AFK warning. The most important reaction wins via a fixed
  priority ladder.
- **Weighted random variants** — any state can ship several sprite sheets that
  the plugin re-rolls every loop, so a state never looks repetitive. Relative
  odds live in `weights.json`.
- **Info panel** — optional HP, prayer, inventory count (out of 28), and XP/hour
  readouts beside the avatar. Opacity-aware, and it stays visible when the
  avatar is collapsed to its control chevron.
- **Sound cues** — optional bundled cues for animation starts/ends, game events,
  and matched chat messages, with per-category toggles and a volume control.
  Sounds are off by default.
- **Chat-message filters** — enter a distinctive three-or-more-word phrase; a
  matching chat line triggers a desktop notification, a dedicated chat-notification
  avatar animation, and an optional sound.
- **Return-to-game button** — an optional control on the widget that brings the
  RuneLite client back to the front in one click.

## Drop-in animations

Sprite sheets are bundled in the jar, but you can override or add to them at
runtime without a rebuild: drop PNGs into `<RuneLite home>/idle-familiar/avatar/`
(created on first run). A drop-in file wins over the bundled sheet of the same
name. Use the **Reload animations** / **Validate animations** buttons in the
plugin's Debug config to pick up changes without toggling the plugin.

## Configuration

The config panel is grouped into sections: **Avatar** (scale, opacity, speed,
speech bubble, skill icon), **Desktop widget** (which info-panel rows to show,
the return-to-game button), **Warnings & reactions** (HP/prayer thresholds,
combat and skilling toggles), **Sounds** (master toggle, volume, per-category
cues, chat-message filters), **Detection** (idle / AFK thresholds, skilling
linger), and **Debug** (state logging, the animation preview, reload/validate).

## License

BSD 2-Clause — see [LICENSE](LICENSE).
