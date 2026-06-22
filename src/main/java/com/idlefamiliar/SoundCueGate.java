package com.idlefamiliar;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Prevents a semantic game-event cue and the immediately-following animation
 * start cue from double-playing for the same action.
 */
public class SoundCueGate
{
	private final Map<String, Integer> suppressedAnimationStarts = new HashMap<>();

	public void suppressAnimationStart(String animationKey, int currentTick, int ttlTicks)
	{
		if (animationKey == null || animationKey.isEmpty())
		{
			return;
		}
		suppressedAnimationStarts.put(animationKey, currentTick + Math.max(0, ttlTicks));
	}

	public boolean consumeSuppressedAnimationStart(String animationKey, int currentTick)
	{
		pruneExpired(currentTick);
		Integer suppressUntilTick = suppressedAnimationStarts.get(animationKey);
		if (suppressUntilTick == null)
		{
			return false;
		}
		suppressedAnimationStarts.remove(animationKey);
		return true;
	}

	public void reset()
	{
		suppressedAnimationStarts.clear();
	}

	private void pruneExpired(int currentTick)
	{
		Iterator<Map.Entry<String, Integer>> iterator = suppressedAnimationStarts.entrySet().iterator();
		while (iterator.hasNext())
		{
			if (currentTick > iterator.next().getValue())
			{
				iterator.remove();
			}
		}
	}
}
