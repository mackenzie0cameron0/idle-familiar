package com.idlefamiliar;

import java.util.Map;
import net.runelite.api.SpriteID;

/**
 * Maps a confirmed skill name to its RuneLite skill sprite id, so the desktop
 * widget can draw the matching skill badge.
 */
public final class SkillIcons
{
	private static final Map<String, Integer> SKILL_SPRITE_IDS = Map.ofEntries(
		Map.entry("attack", SpriteID.SKILL_ATTACK),
		Map.entry("strength", SpriteID.SKILL_STRENGTH),
		Map.entry("defence", SpriteID.SKILL_DEFENCE),
		Map.entry("ranged", SpriteID.SKILL_RANGED),
		Map.entry("prayer", SpriteID.SKILL_PRAYER),
		Map.entry("magic", SpriteID.SKILL_MAGIC),
		Map.entry("runecraft", SpriteID.SKILL_RUNECRAFT),
		Map.entry("hitpoints", SpriteID.SKILL_HITPOINTS),
		Map.entry("crafting", SpriteID.SKILL_CRAFTING),
		Map.entry("mining", SpriteID.SKILL_MINING),
		Map.entry("smithing", SpriteID.SKILL_SMITHING),
		Map.entry("fishing", SpriteID.SKILL_FISHING),
		Map.entry("cooking", SpriteID.SKILL_COOKING),
		Map.entry("firemaking", SpriteID.SKILL_FIREMAKING),
		Map.entry("woodcutting", SpriteID.SKILL_WOODCUTTING),
		Map.entry("agility", SpriteID.SKILL_AGILITY),
		Map.entry("herblore", SpriteID.SKILL_HERBLORE),
		Map.entry("thieving", SpriteID.SKILL_THIEVING),
		Map.entry("fletching", SpriteID.SKILL_FLETCHING),
		Map.entry("slayer", SpriteID.SKILL_SLAYER),
		Map.entry("farming", SpriteID.SKILL_FARMING),
		Map.entry("construction", SpriteID.SKILL_CONSTRUCTION),
		Map.entry("hunter", SpriteID.SKILL_HUNTER)
	);

	private SkillIcons()
	{
	}

	/**
	 * @return the RuneLite sprite id for {@code skill}, or {@code null} if the
	 *         name is unknown.
	 */
	public static Integer spriteId(String skill)
	{
		return skill == null ? null : SKILL_SPRITE_IDS.get(skill.toLowerCase());
	}
}
