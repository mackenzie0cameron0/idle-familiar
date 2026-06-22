package com.idlefamiliar;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("idlefamiliar")
public interface IdleFamiliarConfig extends Config
{
	/** Config group key, shared by {@link net.runelite.client.config.ConfigManager} writes. */
	String GROUP = "idlefamiliar";

	@ConfigSection(
		name = "Avatar",
		description = "Desktop avatar display and animation playback",
		position = 0
	)
	String avatarSection = "avatarSection";

	@ConfigSection(
		name = "Desktop widget",
		description = "The always-on-top desktop avatar window and its info panel",
		position = 1
	)
	String widgetSection = "widgetSection";

	@ConfigSection(
		name = "Warnings & reactions",
		description = "When the avatar reacts to HP, prayer, inventory and combat",
		position = 2
	)
	String warningsSection = "warningsSection";

	@ConfigSection(
		name = "Sounds",
		description = "Short local sound cues for animation and game events",
		position = 3
	)
	String soundSection = "soundSection";

	@ConfigSection(
		name = "Detection",
		description = "Tuning for how the plugin detects what you are doing",
		position = 4
	)
	String detectionSection = "detectionSection";

	@ConfigSection(
		name = "Debug",
		description = "Diagnostics and the animation preview",
		position = 5
	)
	String debugSection = "debugSection";

	// --- Avatar ------------------------------------------------------------

	@ConfigItem(
		keyName = "avatarScale",
		name = "Avatar scale",
		description = "Display size of the desktop avatar",
		section = avatarSection,
		position = 0
	)
	default ScaleMultiplier avatarScale()
	{
		return ScaleMultiplier.X2;
	}

	@Range(min = 20, max = 100)
	@ConfigItem(
		keyName = "avatarOpacityPercent",
		name = "Avatar opacity",
		description = "Avatar opacity as a percentage",
		section = avatarSection,
		position = 1
	)
	default int avatarOpacityPercent()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "animationSpeed",
		name = "Animation speed",
		description = "Playback speed of the avatar animations",
		section = avatarSection,
		position = 2
	)
	default ScaleMultiplier animationSpeed()
	{
		return ScaleMultiplier.X1;
	}

	@ConfigItem(
		keyName = "showSpeechBubble",
		name = "Show speech bubble",
		description = "Display short status messages above the avatar",
		section = avatarSection,
		position = 3
	)
	default boolean showSpeechBubble()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSkillIcon",
		name = "Show skill icon",
		description = "Display the active skill's icon on the desktop avatar while skilling",
		section = avatarSection,
		position = 4
	)
	default boolean showSkillIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playFullCycleAnimations",
		name = "Finish one-shot animations",
		description = "Let momentary animations (teleport, Grand Exchange, agility obstacles) play their full cycle once, even if the underlying state ends first, instead of being cut off",
		section = avatarSection,
		position = 5
	)
	default boolean playFullCycleAnimations()
	{
		return true;
	}

	// --- Desktop widget ----------------------------------------------------

	@ConfigItem(
		keyName = "showHpOrb",
		name = "HP readout",
		description = "Show current hitpoints on the desktop widget's info panel",
		section = widgetSection,
		position = 0
	)
	default boolean showHpOrb()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPrayerOrb",
		name = "Prayer readout",
		description = "Show current prayer points on the desktop widget's info panel",
		section = widgetSection,
		position = 1
	)
	default boolean showPrayerOrb()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInventoryCount",
		name = "Inventory count",
		description = "Show how many inventory slots are filled (out of 28) on the desktop widget",
		section = widgetSection,
		position = 2
	)
	default boolean showInventoryCount()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showAdvancedInfo",
		name = "XP/hour readout",
		description = "Show the active skill's current XP/hour (per login session) on the desktop widget's info panel",
		section = widgetSection,
		position = 3
	)
	default boolean showAdvancedInfo()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showGameButton",
		name = "Return-to-game button",
		description = "Show an X button on the desktop widget that brings the RuneLite game client back to the front",
		section = widgetSection,
		position = 4
	)
	default boolean showGameButton()
	{
		return true;
	}

	// --- Warnings & reactions ----------------------------------------------

	@ConfigItem(
		keyName = "enableInventoryFullWarning",
		name = "Inventory full warning",
		description = "Show an avatar reaction when the inventory is full",
		section = warningsSection,
		position = 0
	)
	default boolean enableInventoryFullWarning()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableLowHpWarning",
		name = "Low HP warning",
		description = "Show an avatar reaction when hitpoints are at or below the configured threshold",
		section = warningsSection,
		position = 1
	)
	default boolean enableLowHpWarning()
	{
		return true;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(
		keyName = "lowHpThreshold",
		name = "Low HP threshold",
		description = "Current hitpoints threshold for low HP warning",
		section = warningsSection,
		position = 2
	)
	default int lowHpThreshold()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "enableLowPrayerWarning",
		name = "Low prayer warning",
		description = "Show an avatar reaction when prayer is at or below the configured threshold",
		section = warningsSection,
		position = 3
	)
	default boolean enableLowPrayerWarning()
	{
		return true;
	}

	@Range(min = 0, max = 99)
	@ConfigItem(
		keyName = "lowPrayerThreshold",
		name = "Low prayer threshold",
		description = "Current prayer points threshold for low prayer warning",
		section = warningsSection,
		position = 4
	)
	default int lowPrayerThreshold()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "reactToCombat",
		name = "React to combat",
		description = "Show a combat state when real hit evidence is detected",
		section = warningsSection,
		position = 5
	)
	default boolean reactToCombat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "reactToSkilling",
		name = "React to skilling",
		description = "Show a skilling state from animation and XP activity",
		section = warningsSection,
		position = 6
	)
	default boolean reactToSkilling()
	{
		return true;
	}

	// --- Sounds ------------------------------------------------------------

	@ConfigItem(
		keyName = "enableSounds",
		name = "Enable sounds",
		description = "Play short bundled sound cues for avatar reactions",
		section = soundSection,
		position = 0
	)
	default boolean enableSounds()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "soundVolumePercent",
		name = "Sound volume",
		description = "Volume for Idle Familiar sound cues as a percentage",
		section = soundSection,
		position = 1
	)
	default int soundVolumePercent()
	{
		return 70;
	}

	@ConfigItem(
		keyName = "animationStartSounds",
		name = "Animation start sounds",
		description = "Play a cue when the avatar enters a new animation state",
		section = soundSection,
		position = 2
	)
	default boolean animationStartSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "animationEndSounds",
		name = "Animation end sounds",
		description = "Play a cue when the avatar leaves a resolved animation state. One-shot animations may still finish visually after the state has handed back.",
		section = soundSection,
		position = 3
	)
	default boolean animationEndSounds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "skillingSounds",
		name = "Skilling",
		description = "Play sounds when skilling activity starts",
		section = soundSection,
		position = 4
	)
	default boolean skillingSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "inventoryFullSounds",
		name = "Full inventory",
		description = "Play sounds when your inventory becomes full",
		section = soundSection,
		position = 5
	)
	default boolean inventoryFullSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "grandExchangeSounds",
		name = "GE item sold",
		description = "Play sounds when a Grand Exchange buy or sell offer fills",
		section = soundSection,
		position = 6
	)
	default boolean grandExchangeSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "idleSounds",
		name = "Idle",
		description = "Play sounds when your character becomes idle",
		section = soundSection,
		position = 7
	)
	default boolean idleSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "afkSounds",
		name = "AFK",
		description = "Play sounds when the AFK warning state begins",
		section = soundSection,
		position = 8
	)
	default boolean afkSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowPrayerSounds",
		name = "Low prayer",
		description = "Play sounds when prayer reaches the configured low-prayer threshold",
		section = soundSection,
		position = 9
	)
	default boolean lowPrayerSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lowHpSounds",
		name = "Low HP",
		description = "Play sounds when hitpoints reach the configured low-HP threshold",
		section = soundSection,
		position = 10
	)
	default boolean lowHpSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatMessageSounds",
		name = "Chat message",
		description = "Play a subtle cue when a chat line matches one of the configured chat filters",
		section = soundSection,
		position = 11
	)
	default boolean chatMessageSounds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatMessageFilters",
		name = "Chat message filters",
		description = "One phrase per line. Use at least three consecutive words from the chat line. Example: some cracks around the cave begin to ooze water.",
		section = soundSection,
		position = 12
	)
	default String chatMessageFilters()
	{
		return "# some cracks around the cave begin to ooze water.";
	}

	// --- Detection ---------------------------------------------------------

	@Range(min = 1, max = 300)
	@ConfigItem(
		keyName = "idleThresholdTicks",
		name = "Idle threshold",
		description = "Number of game ticks before the avatar enters idle state",
		section = detectionSection,
		position = 0
	)
	default int idleThresholdTicks()
	{
		return 15;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "afkWarningThresholdTicks",
		name = "AFK warning threshold",
		description = "Number of game ticks before the avatar shows an AFK warning",
		section = detectionSection,
		position = 1
	)
	default int afkWarningThresholdTicks()
	{
		return 60;
	}

	@Range(min = 1, max = 4)
	@ConfigItem(
		keyName = "skillingLingerTicks",
		name = "Skilling linger",
		description = "Bridge (in ticks) between skilling signals. Detection is per-tick from the live animation, so sustained skills (mining, fishing, woodcutting) stop almost immediately; this only smooths the sub-second gaps of click-per-item skills (fletching, cooking). 1 tick = 0.6s. Raise if a click-per-item skill flickers to idle between items; the effective value is capped at 4 ticks so a stale setting cannot make the avatar over-run an activity.",
		section = detectionSection,
		position = 2
	)
	default int skillingLingerTicks()
	{
		return 2;
	}

	// --- Debug -------------------------------------------------------------

	@ConfigItem(
		keyName = "debugState",
		name = "Debug state",
		description = "Show the current detected avatar state and activity",
		section = debugSection,
		position = 0
	)
	default boolean debugState()
	{
		return false;
	}

	@ConfigItem(
		keyName = "debugPreviewState",
		name = "Preview animation",
		description = "Force the avatar to render a chosen state or skill animation, ignoring detection, so you can check art without reproducing the in-game condition. Set to Off for normal behaviour.",
		section = debugSection,
		position = 1
	)
	default AvatarPreview debugPreviewState()
	{
		return AvatarPreview.OFF;
	}

	@ConfigItem(
		keyName = "widgetCollapsed",
		name = "Widget collapsed",
		description = "Internal: tracks whether the desktop widget is collapsed to its carrot icon",
		hidden = true
	)
	default boolean widgetCollapsed()
	{
		return false;
	}
}
