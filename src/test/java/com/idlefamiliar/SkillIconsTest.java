package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import net.runelite.api.SpriteID;
import org.junit.Test;

public class SkillIconsTest
{
	@Test
	public void mapsKnownSkillNamesToSprites()
	{
		assertEquals(Integer.valueOf(SpriteID.SKILL_FISHING), SkillIcons.spriteId("Fishing"));
		assertEquals(Integer.valueOf(SpriteID.SKILL_WOODCUTTING), SkillIcons.spriteId("Woodcutting"));
		assertEquals(Integer.valueOf(SpriteID.SKILL_COOKING), SkillIcons.spriteId("Cooking"));
	}

	@Test
	public void skillNameLookupIsCaseInsensitive()
	{
		assertNotNull(SkillIcons.spriteId("MINING"));
		assertEquals(SkillIcons.spriteId("MINING"), SkillIcons.spriteId("mining"));
	}

	@Test
	public void unknownOrNullSkillHasNoSprite()
	{
		assertNull(SkillIcons.spriteId("NotASkill"));
		assertNull(SkillIcons.spriteId(null));
		assertNull(SkillIcons.spriteId("Skilling"));
	}
}
