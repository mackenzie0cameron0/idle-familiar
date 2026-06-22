package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

public class ActivityAnimationRegistryTest
{
	private final ActivityAnimationRegistry registry = ActivityAnimationRegistry.build();

	@Test
	public void mapsGatheringSkillAnimationsToLabels()
	{
		assertEquals(Optional.of("Mining"),
			registry.getActivityForAnimation(AnimationID.HUMAN_MINING_BRONZE_PICKAXE));
		assertEquals(Optional.of("Woodcutting"),
			registry.getActivityForAnimation(AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE));
		assertEquals(Optional.of("Farming"),
			registry.getActivityForAnimation(AnimationID.HUMAN_FARMING));
	}

	@Test
	public void mapsAllFishingMethodsToFishing()
	{
		// Rod casting, harpoon, and small-net all resolve to a single Fishing label.
		assertEquals(Optional.of("Fishing"),
			registry.getActivityForAnimation(AnimationID.HUMAN_FISHING_CASTING));
		assertEquals(Optional.of("Fishing"),
			registry.getActivityForAnimation(AnimationID.HUMAN_HARPOON));
		assertEquals(Optional.of("Fishing"),
			registry.getActivityForAnimation(AnimationID.HUMAN_SMALLNET));
	}

	@Test
	public void mapsTheSustainedFishingOnSpotAnimation()
	{
		// HUMAN_FISH_ONSPOT (623) is the looping "fishing at the spot" animation —
		// the one shown continuously while fishing. It uses the HUMAN_FISH_ stem,
		// NOT HUMAN_FISHING_, so it must be whitelisted explicitly or the avatar
		// flickers to idle between casts.
		assertEquals(Optional.of("Fishing"),
			registry.getActivityForAnimation(AnimationID.HUMAN_FISH_ONSPOT));
		assertEquals(Optional.of("Fishing"),
			registry.getActivityForAnimation(AnimationID.HUMAN_FISH_ONSPOT_PEARL));
	}

	@Test
	public void doesNotMisclassifyTheFishSizeCutsceneAnimation()
	{
		// HUMAN_FISHSIZE (802) shares the HUMAN_FISH stem but is a cutscene, not
		// skilling — the prefix must be precise enough to exclude it.
		assertFalse(registry.getActivityForAnimation(AnimationID.HUMAN_FISHSIZE).isPresent());
	}

	@Test
	public void mapsLargeNetFishingToFishing()
	{
		// Big-net fishing animates with HUMAN_LARGENET (620) and its sustained loop
		// HUMAN_LARGENET_LOOPING (11042). These use the HUMAN_LARGENET stem — NOT
		// HUMAN_SMALLNET — so, exactly like HUMAN_FISH_ONSPOT, they must be whitelisted
		// explicitly or net fishing flickers to idle between casts.
		assertEquals(Optional.of("Fishing"),
			registry.getActivityForAnimation(AnimationID.HUMAN_LARGENET));
		assertEquals(Optional.of("Fishing"),
			registry.getActivityForAnimation(AnimationID.HUMAN_LARGENET_LOOPING));
	}

	@Test
	public void mapsCookingOnAFireToCooking()
	{
		// HUMAN_FIRECOOKING (897) is cooking on a fire — the animation shown
		// continuously while cooking away from a range. It uses the HUMAN_FIRECOOKING
		// stem, NOT HUMAN_COOKING, so without an explicit entry the avatar flickers to
		// idle between items. (Range cooking is already smooth: HUMAN_COOKING and its
		// HUMAN_COOKING_LOOP both match the HUMAN_COOKING prefix.) This is the cooking
		// analogue of the HUMAN_FISH_ONSPOT fix, generalised per the reported bug.
		assertEquals(Optional.of("Cooking"),
			registry.getActivityForAnimation(AnimationID.HUMAN_FIRECOOKING));
	}

	@Test
	public void mapsArtisanAndSupportSkillAnimations()
	{
		assertEquals(Optional.of("Smithing"),
			registry.getActivityForAnimation(AnimationID.HUMAN_SMITHING));
		assertEquals(Optional.of("Cooking"),
			registry.getActivityForAnimation(AnimationID.HUMAN_COOKING));
		assertEquals(Optional.of("Fletching"),
			registry.getActivityForAnimation(AnimationID.HUMAN_FLETCHING));
		assertEquals(Optional.of("Herblore"),
			registry.getActivityForAnimation(AnimationID.HUMAN_HERBING_VIAL));
		assertEquals(Optional.of("Runecraft"),
			registry.getActivityForAnimation(AnimationID.HUMAN_RUNECRAFT));
		assertEquals(Optional.of("Thieving"),
			registry.getActivityForAnimation(AnimationID.HUMAN_PICKPOCKET));
	}

	@Test
	public void coversEveryVariantOfAWhitelistedSkill()
	{
		// The registry is built from the whole HUMAN_MINING_* family, not a single
		// pickaxe, so dragon/infernal/crystal variants all map too.
		assertEquals(Optional.of("Mining"),
			registry.getActivityForAnimation(AnimationID.HUMAN_MINING_DRAGON_PICKAXE));
		assertEquals(Optional.of("Mining"),
			registry.getActivityForAnimation(AnimationID.HUMAN_MINING_INFERNAL_PICKAXE));
	}

	@Test
	public void excludesNonActivePoseAnimations()
	{
		// These share a whitelisted skill's stem but are non-active poses — standing
		// idle at an anvil, stepping up to it, or having nothing left to fletch — and
		// must produce no skilling signal, or the avatar keeps "skilling" while idle.
		assertFalse(registry.getActivityForAnimation(AnimationID.HUMAN_SMITHING_IDLE).isPresent());
		assertFalse(registry.getActivityForAnimation(AnimationID.HUMAN_SMITHING_ENTER).isPresent());
		assertFalse(registry.getActivityForAnimation(AnimationID.HUMAN_FLETCHING_NO_ITEMS).isPresent());
	}

	@Test
	public void stillMapsTheActiveAnimationsOfPartlyExcludedSkills()
	{
		// Excluding the idle/enter/no-items poses must not break the genuine active
		// animation of the same skill family.
		assertEquals(Optional.of("Smithing"),
			registry.getActivityForAnimation(AnimationID.HUMAN_SMITHING));
		assertEquals(Optional.of("Fletching"),
			registry.getActivityForAnimation(AnimationID.HUMAN_FLETCHING));
	}

	@Test
	public void incidentalAnimationsProduceNoSignal()
	{
		// No animation (-1), an emote (855 = cheer), and a teleport are NOT skilling.
		assertFalse(registry.getActivityForAnimation(-1).isPresent());
		assertFalse(registry.getActivityForAnimation(855).isPresent());
		assertFalse(registry.getActivityForAnimation(AnimationID.HUMAN_CASTTELEPORT).isPresent());
	}

	@Test
	public void recognisesTeleportAnimations()
	{
		assertTrue(registry.isTeleportAnimation(AnimationID.HUMAN_CASTTELEPORT));
		// A skilling animation is not a teleport.
		assertFalse(registry.isTeleportAnimation(AnimationID.HUMAN_MINING_BRONZE_PICKAXE));
	}
}
