package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.Test;

public class WeightedVariantPickerTest
{
	@Test
	public void alwaysReturnsSingleVariantWhenOnlyOneExists()
	{
		Map<String, Integer> weights = new LinkedHashMap<>();
		weights.put("idle_loop", 100);
		WeightedVariantPicker picker = new WeightedVariantPicker(weights, new Random(0));
		for (int i = 0; i < 100; i++)
		{
			assertEquals("idle_loop", picker.pick());
		}
	}

	@Test
	public void respectsWeightDistributionOverManyTrials()
	{
		Map<String, Integer> weights = new LinkedHashMap<>();
		weights.put("idle_loop", 70);
		weights.put("idle_loop_uncommon", 25);
		weights.put("idle_loop_rare", 5);
		WeightedVariantPicker picker = new WeightedVariantPicker(weights, new Random(42));

		Map<String, Integer> counts = new HashMap<>();
		int trials = 10_000;
		for (int i = 0; i < trials; i++)
		{
			counts.merge(picker.pick(), 1, Integer::sum);
		}

		// Common variant should dominate (~70%), rare should be uncommon (<10%).
		assertTrue("common appeared " + counts.get("idle_loop"), counts.get("idle_loop") > 6000);
		assertTrue("rare appeared " + counts.get("idle_loop_rare"), counts.get("idle_loop_rare") < 1000);
		assertEquals(Integer.valueOf(trials),
			Integer.valueOf(counts.values().stream().mapToInt(Integer::intValue).sum()));
	}

	@Test
	public void equalWeightsProduceRoughlyEqualSplit()
	{
		Map<String, Integer> weights = new LinkedHashMap<>();
		weights.put("idle_loop", 1);
		weights.put("idle_loop_rare", 1);
		WeightedVariantPicker picker = new WeightedVariantPicker(weights, new Random(7));

		Map<String, Integer> counts = new HashMap<>();
		for (int i = 0; i < 1000; i++)
		{
			counts.merge(picker.pick(), 1, Integer::sum);
		}
		assertTrue(counts.get("idle_loop") > 350 && counts.get("idle_loop") < 650);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsEmptyWeightMap()
	{
		new WeightedVariantPicker(new LinkedHashMap<>(), new Random(0));
	}
}
