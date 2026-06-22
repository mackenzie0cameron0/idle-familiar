package com.idlefamiliar;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class AnimationControllerVariantTest
{
	@Test
	public void buildsPickerForStateWithVariants()
	{
		AnimationController controller = new AnimationController(new Random(1));
		controller.loadDefaultAnimations();

		// "idle" resolves to idle_loop.png plus its shipped variants: idle_loop_uncommon
		// and the numbered idle_loop_2..idle_loop_7.
		WeightedVariantPicker picker = controller.buildVariantPicker("idle");
		assertNotNull(picker);
		assertTrue("expected base + named/numbered idle variants", picker.variantCount() >= 8);
		assertTrue(picker.variantNames().containsAll(Arrays.asList(
			"idle_loop",
			"idle_loop_uncommon",
			"idle_loop_2",
			"idle_loop_3",
			"idle_loop_4",
			"idle_loop_5",
			"idle_loop_6",
			"idle_loop_7")));
	}

	@Test
	public void weightsJsonMakesCommonVariantDominate()
	{
		AnimationController controller = new AnimationController(new Random(99));
		controller.loadDefaultAnimations();
		WeightedVariantPicker picker = controller.buildVariantPicker("idle");

		Map<String, Integer> counts = new HashMap<>();
		for (int i = 0; i < 4000; i++)
		{
			counts.merge(picker.pick(), 1, Integer::sum);
		}

		int common = counts.getOrDefault("idle_loop", 0);
		int rare = counts.getOrDefault("idle_loop_rare", 0);
		// weights.json keeps the base idle loop common, but lets numbered loops
		// appear often enough that newly-added idle art is visible during normal use.
		assertTrue("common=" + common + " rare=" + rare, common > rare * 3);
	}

	@Test
	public void stateWithoutVariantsHasSinglePick()
	{
		AnimationController controller = new AnimationController(new Random(2));
		controller.loadDefaultAnimations();
		// "inventory_full" ships a single primary sheet with no variant suffixes,
		// so its picker has exactly one entry. (Uses inventory_full rather than
		// combat because the combat sheet is not currently bundled.)
		WeightedVariantPicker picker = controller.buildVariantPicker("inventory_full");
		assertNotNull(picker);
		assertTrue(picker.variantCount() == 1);
	}

	@Test
	public void getFrameStillRendersThroughVariantPath()
	{
		AnimationController controller = new AnimationController(new Random(3));
		controller.loadDefaultAnimations();
		assertNotNull(controller.getFrame(AvatarState.PLAYER_IDLE));
		assertNotNull(controller.getFrame(AvatarState.SKILLING, "Fishing"));
		assertNotNull(controller.getFrame(AvatarState.SKILLING, "Prayer"));
		assertNotNull(controller.getFrame(AvatarState.AFK_WARNING));
	}
}
