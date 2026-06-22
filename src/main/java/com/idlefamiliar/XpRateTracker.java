package com.idlefamiliar;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks per-skill XP/hour for the current login session.
 *
 * <p>The first time a skill is seen after a {@link #reset()} (i.e. at login,
 * when RuneLite syncs every stat), its total XP becomes the session baseline.
 * The rate clock, however, does not start until the <em>first actual XP gain</em>
 * in that skill — so the figure reflects time spent training, not time since
 * login. (Anchoring at login would divide by the idle time before training and
 * report a rate well below RuneLite's XP Tracker.) The rate is then the XP gained
 * since the baseline divided by the elapsed hours since training began — a stable
 * "since you started" figure, not a decaying rolling average.
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
		private long startXp;
		private long startMillis;
		private long currentXp;
		/** False until the first XP gain; while false the baseline tracks the latest reading. */
		private boolean started;

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
		if (entry.started)
		{
			entry.currentXp = totalXp;
			return;
		}
		if (totalXp > entry.startXp)
		{
			// First gain since the (login) baseline: start the clock here so the rate
			// counts training time, not time since login. The earned XP is measured
			// from the baseline; the elapsed time runs from this first gain.
			entry.started = true;
			entry.startMillis = nowMillis;
			entry.currentXp = totalXp;
		}
		else
		{
			// No gain yet (the login stat-sync, or an XP correction): keep the baseline
			// current so idle time before training is never counted against the rate.
			entry.startXp = totalXp;
			entry.startMillis = nowMillis;
			entry.currentXp = totalXp;
		}
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
