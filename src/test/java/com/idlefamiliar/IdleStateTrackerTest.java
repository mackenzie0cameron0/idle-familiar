package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IdleStateTrackerTest
{
	@Test
	public void tracksIdleThresholds()
	{
		IdleStateTracker tracker = new IdleStateTracker();
		tracker.reset(0);
		tracker.setCurrentTick(14);
		assertFalse(tracker.isIdle(15));

		tracker.setCurrentTick(15);
		assertTrue(tracker.isIdle(15));

		tracker.setCurrentTick(60);
		assertTrue(tracker.isAfkWarning(60));
	}

	@Test
	public void markActivityResetsElapsedTicks()
	{
		IdleStateTracker tracker = new IdleStateTracker();
		tracker.reset(0);
		tracker.setCurrentTick(20);
		tracker.markActivity(ActivityType.WALKING);

		assertEquals(0, tracker.getTicksSinceActivity());
		assertEquals(ActivityType.WALKING, tracker.getLastActivityType());
		assertFalse(tracker.isIdle(15));
	}
}
