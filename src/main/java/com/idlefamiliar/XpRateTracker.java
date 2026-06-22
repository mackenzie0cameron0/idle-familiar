package com.idlefamiliar;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks per-skill XP/hour for the current login session.
 *
 * <p>The first time a skill is seen after a {@link #reset()} (i.e. at login,
 * when RuneLite syncs every stat), its total XP and the wall-clock time become
 * the session baseline. The rate is then simply the XP gained since that
 * baseline divided by the elapsed hours — a stable "since login" figure, as
 * opposed to a decaying rolling average.
 *
 * <p>The class is free of RuneLite and Swing dependencies (time is passed in)
 * so it can be unit tested in isolation.
 */
public class XpRateTracker
{
	/**
	 * Minimum elapsed time before a rate is reported. Below this, dividing a
	 * tiny XP gain by a near-zero interval produces absurd spikes, so the rate
	 * is suppressed (returned as 0) until enough time has passed.
	 */
	static final long MIN_ELAPSED_MILLIS = 5_000L;

	private static final double MILLIS_PER_HOUR = 3_600_000.0;

	/** Per-skill baseline + latest reading. */
	private static final class Entry
	{
		private final long startXp;
		private final long startMillis;
		private long currentXp;

		private Entry(long startXp, long startMillis)
		{
			this.startXp = startXp;
			this.startMillis = startMillis;
			this.currentXp = startXp;
		}
	}

	private final Map<String, Entry> bySkill = new HashMap<>();

	/**
	 * Record the latest <em>total</em> XP for {@code skill} at {@code nowMillis}.
	 * The first call for a skill (after a reset) sets its session baseline;
	 * later calls only advance the current reading.
	 */
	public void record(String skill, long totalXp, long nowMillis)
	{
		if (skill == null || skill.isEmpty())
		{
			return;
		}
		Entry entry = bySkill.get(skill);
		if (entry == null)
		{
			bySkill.put(skill, new Entry(totalXp, nowMillis));
			return;
		}
		entry.currentXp = totalXp;
	}

	/**
	 * @return XP/hour for {@code skill} at {@code nowMillis}, or {@code 0} if the
	 *         skill is unknown, no XP has been gained, or too little time has
	 *         elapsed for a meaningful figure
	 */
	public long ratePerHour(String skill, long nowMillis)
	{
		if (skill == null)
		{
			return 0;
		}
		Entry entry = bySkill.get(skill);
		if (entry == null)
		{
			return 0;
		}
		long elapsed = nowMillis - entry.startMillis;
		long gained = entry.currentXp - entry.startXp;
		if (elapsed < MIN_ELAPSED_MILLIS || gained <= 0)
		{
			return 0;
		}
		return Math.round(gained * MILLIS_PER_HOUR / elapsed);
	}

	/** Clears all tracked rates (e.g. on logout or plugin start). */
	public void reset()
	{
		bySkill.clear();
	}

	/**
	 * Format a per-hour value compactly for display, e.g. {@code 92_400 ->
	 * "92.4k/hr"}, {@code 1_250_000 -> "1.3m/hr"}. A non-positive value renders
	 * as an em-dash so the panel still shows the skill while the rate warms up.
	 */
	public static String formatRate(long perHour)
	{
		if (perHour <= 0)
		{
			return "–/hr";
		}
		if (perHour >= 1_000_000)
		{
			return String.format("%.1fm/hr", perHour / 1_000_000.0);
		}
		if (perHour >= 100_000)
		{
			return String.format("%.0fk/hr", perHour / 1_000.0);
		}
		if (perHour >= 1_000)
		{
			return String.format("%.1fk/hr", perHour / 1_000.0);
		}
		return perHour + "/hr";
	}
}
