package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class XpRateTrackerTest
{
	private static final long HOUR = 3_600_000L;

	@Test
	public void computesRateFromGainOverElapsedTime()
	{
		XpRateTracker tracker = new XpRateTracker();
		// Baseline at login: 1,000,000 total XP at t=0.
		tracker.record("Fishing", 1_000_000L, 0);
		// Half an hour later: 50,000 XP gained -> 100,000/hr.
		tracker.record("Fishing", 1_050_000L, HOUR / 2);

		assertEquals(100_000L, tracker.ratePerHour("Fishing", HOUR / 2));
	}

	@Test
	public void suppressesRateUntilEnoughTimeElapsed()
	{
		XpRateTracker tracker = new XpRateTracker();
		tracker.record("Mining", 500_000L, 0);
		// A gain only 1s after baseline would imply a wild rate — suppress it.
		tracker.record("Mining", 500_100L, 1_000);
		assertEquals(0, tracker.ratePerHour("Mining", 1_000));

		// Once past the warm-up window, the rate is reported.
		tracker.record("Mining", 510_000L, HOUR);
		assertTrue(tracker.ratePerHour("Mining", HOUR) > 0);
	}

	@Test
	public void unknownOrUngainedSkillIsZero()
	{
		XpRateTracker tracker = new XpRateTracker();
		assertEquals(0, tracker.ratePerHour("Cooking", HOUR));

		// Seen but no XP gained beyond baseline -> still zero, never negative.
		tracker.record("Cooking", 200_000L, 0);
		tracker.record("Cooking", 200_000L, HOUR);
		assertEquals(0, tracker.ratePerHour("Cooking", HOUR));
	}

	@Test
	public void resetClearsBaselinesForNewSession()
	{
		XpRateTracker tracker = new XpRateTracker();
		tracker.record("Fishing", 1_000_000L, 0);
		tracker.record("Fishing", 1_100_000L, HOUR);
		assertTrue(tracker.ratePerHour("Fishing", HOUR) > 0);

		tracker.reset();
		// After reset the next reading becomes the new baseline; no rate yet.
		tracker.record("Fishing", 1_100_000L, 2 * HOUR);
		assertEquals(0, tracker.ratePerHour("Fishing", 2 * HOUR));
	}

	@Test
	public void formatRateIsCompact()
	{
		assertEquals("92.4k/hr", XpRateTracker.formatRate(92_400));
		assertEquals("1.3m/hr", XpRateTracker.formatRate(1_250_000));
		assertEquals("850/hr", XpRateTracker.formatRate(850));
		assertEquals("–/hr", XpRateTracker.formatRate(0));
	}
}
