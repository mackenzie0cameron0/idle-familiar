package com.idlefamiliar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Picks one variant name from a weighted set, proportionally to each variant's
 * weight. Used to choose which sprite-sheet variant of an animation state to
 * play (e.g. a common idle loop most of the time and a rare one occasionally).
 *
 * <p>The {@link Random} source is injectable so the distribution can be tested
 * deterministically. Weights are positive integers; a variant with weight 0 is
 * effectively never chosen.
 */
public class WeightedVariantPicker
{
	private final List<String> names;
	private final int[] cumulativeWeights;
	private final int totalWeight;
	private final Random random;

	/**
	 * @param weightMap insertion-ordered map of variant name &rarr; weight
	 * @param random    random source (injectable for deterministic tests)
	 */
	public WeightedVariantPicker(Map<String, Integer> weightMap, Random random)
	{
		if (weightMap == null || weightMap.isEmpty())
		{
			throw new IllegalArgumentException("weightMap must contain at least one variant");
		}
		this.random = random;
		this.names = new ArrayList<>(weightMap.size());
		this.cumulativeWeights = new int[weightMap.size()];

		int cumulative = 0;
		int index = 0;
		for (Map.Entry<String, Integer> entry : weightMap.entrySet())
		{
			int weight = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
			cumulative += weight;
			names.add(entry.getKey());
			cumulativeWeights[index++] = cumulative;
		}
		this.totalWeight = cumulative;
	}

	/** @return a variant name chosen proportionally to its weight. */
	public String pick()
	{
		if (names.size() == 1 || totalWeight <= 0)
		{
			return names.get(0);
		}

		int roll = random.nextInt(totalWeight);
		for (int i = 0; i < cumulativeWeights.length; i++)
		{
			if (roll < cumulativeWeights[i])
			{
				return names.get(i);
			}
		}
		return names.get(names.size() - 1); // unreachable in practice
	}

	/** @return the number of variants this picker chooses among. */
	public int variantCount()
	{
		return names.size();
	}

	/** @return the variant names this picker can choose from, in discovery order. */
	List<String> variantNames()
	{
		return Collections.unmodifiableList(names);
	}
}
