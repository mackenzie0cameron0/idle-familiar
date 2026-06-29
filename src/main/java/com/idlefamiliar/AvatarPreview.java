package com.idlefamiliar;

/**
 * Debug-only selector that forces the avatar to render a chosen state (or a
 * specific skilling animation) regardless of what the player is actually doing,
 * so animation art can be previewed without reproducing the in-game condition.
 * {@link #OFF} restores normal, detection-driven behaviour.
 *
 * <p>Each entry maps to the {@link AvatarState} and activity label the renderer
 * needs; skilling entries share {@link AvatarState#SKILLING} and differ only by
 * their label (which resolves to {@code <skill>_loop.png}).
 */
public enum AvatarPreview
{
	OFF("Off", null, ""),
	IDLE("Idle", AvatarState.PLAYER_IDLE, ""),
	ACTIVE("Active", AvatarState.PLAYER_ACTIVE, ""),
	WALKING("Walking", AvatarState.WALKING, ""),
	RUNNING("Running", AvatarState.RUNNING, ""),
	COMBAT("Combat", AvatarState.COMBAT, ""),
	AFK_WARNING("AFK warning", AvatarState.AFK_WARNING, ""),
	INVENTORY_FULL("Inventory full", AvatarState.INVENTORY_FULL, ""),
	LOW_HEALTH("Low health", AvatarState.LOW_HEALTH, ""),
	LOW_PRAYER("Low prayer", AvatarState.LOW_PRAYER, ""),
	DEATH("Death", AvatarState.DEATH, ""),
	LEVEL_UP("Level up", AvatarState.LEVEL_UP, ""),
	TELEPORTING("Teleporting", AvatarState.TELEPORTING, ""),
	GRAND_EXCHANGE("Grand Exchange", AvatarState.GRAND_EXCHANGE, ""),
	BANKING("Banking", AvatarState.BANKING, ""),
	CHAT_NOTIFICATION("Chat notification", AvatarState.CUSTOM_EVENT, ""),
	LOGGED_OUT("Logged out", AvatarState.LOGGED_OUT, ""),
	SKILLING("Skilling (generic)", AvatarState.SKILLING, ""),
	WOODCUTTING("Skill: Woodcutting", AvatarState.SKILLING, "Woodcutting"),
	MINING("Skill: Mining", AvatarState.SKILLING, "Mining"),
	FISHING("Skill: Fishing", AvatarState.SKILLING, "Fishing"),
	COOKING("Skill: Cooking", AvatarState.SKILLING, "Cooking"),
	FIREMAKING("Skill: Firemaking", AvatarState.SKILLING, "Firemaking"),
	SMITHING("Skill: Smithing", AvatarState.SKILLING, "Smithing"),
	FLETCHING("Skill: Fletching", AvatarState.SKILLING, "Fletching"),
	CRAFTING("Skill: Crafting", AvatarState.SKILLING, "Crafting"),
	HERBLORE("Skill: Herblore", AvatarState.SKILLING, "Herblore"),
	PRAYER("Skill: Prayer", AvatarState.SKILLING, "Prayer"),
	RUNECRAFT("Skill: Runecraft", AvatarState.SKILLING, "Runecraft"),
	FARMING("Skill: Farming", AvatarState.SKILLING, "Farming"),
	THIEVING("Skill: Thieving", AvatarState.SKILLING, "Thieving"),
	AGILITY("Skill: Agility", AvatarState.SKILLING, "Agility");

	private final String displayName;
	private final AvatarState state;
	private final String label;

	AvatarPreview(String displayName, AvatarState state, String label)
	{
		this.displayName = displayName;
		this.state = state;
		this.label = label;
	}

	/** @return the avatar state this preview renders ({@code null} only for {@link #OFF}). */
	public AvatarState state()
	{
		return state;
	}

	/** @return the activity label (skill name) for skilling previews, else empty. */
	public String label()
	{
		return label;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
