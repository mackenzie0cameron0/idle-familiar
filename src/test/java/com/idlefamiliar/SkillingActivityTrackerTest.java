package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SkillingActivityTrackerTest
{
	private static final int LINGER = 10;

	@Test
	public void skillingDoesNotFlickerToIdleBetweenSignals()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();

		// First skilling signal at tick 0.
		assertTrue(tracker.recordSkillSignal("Fishing", 0));
		assertTrue(tracker.isSkilling(0, LINGER));

		// Four ticks later, still within the linger window, no new signal yet.
		// The avatar must NOT flicker to idle during this gap.
		assertTrue(tracker.isSkilling(4, LINGER));

		// A second signal arrives at tick 5 and keeps it alive.
		assertTrue(tracker.recordSkillSignal("Fishing", 5));
		assertTrue(tracker.isSkilling(5, LINGER));
	}

	@Test
	public void skillingExpiresAfterLingerWindow()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordSkillSignal("Fishing", 0);

		// Just inside the window: still skilling.
		assertTrue(tracker.isSkilling(LINGER - 1, LINGER));
		// At and beyond the window with no new signals: no longer skilling.
		assertFalse(tracker.isSkilling(LINGER, LINGER));
		assertFalse(tracker.isSkilling(LINGER + 5, LINGER));
		assertNull(tracker.getConfirmedSkill(LINGER, LINGER));
	}

	@Test
	public void passiveSkillsDoNotStartOrExtendLinger()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();

		// Player fishes at tick 0, then stops.
		tracker.recordSkillSignal("Fishing", 0);

		// A passive prayer-drain stat change arrives near the end of the window.
		assertFalse(tracker.recordSkillSignal("Prayer", 9));
		// And a hitpoints-regen stat change too.
		assertFalse(tracker.recordSkillSignal("Hitpoints", 9));

		// Neither passive source extended the fishing linger: it still expires
		// LINGER ticks after the original fishing signal, not the passive ones.
		assertFalse(tracker.isSkilling(LINGER, LINGER));
		// And the confirmed skill was never overwritten to a passive skill.
		assertEquals("Fishing", tracker.getConfirmedSkill(LINGER - 1, LINGER));
	}

	@Test
	public void passiveSkillAloneNeverCountsAsSkilling()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		assertFalse(tracker.recordSkillSignal("Hitpoints", 0));
		assertFalse(tracker.isSkilling(0, LINGER));
		assertNull(tracker.getConfirmedSkill(0, LINGER));
	}

	@Test
	public void confirmedPrayerXpCountsAsSkilling()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();

		assertTrue(tracker.recordXpSkillSignal("Prayer", 0));
		assertTrue(tracker.isSkilling(0, LINGER));
		assertEquals("Prayer", tracker.getConfirmedSkill(0, LINGER));
	}

	@Test
	public void activitySignalRefreshesLingerWithoutChangingSkill()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordSkillSignal("Woodcutting", 0);

		// An ongoing tool animation refreshes the window at tick 8.
		tracker.recordActivitySignal(8);

		// Still skilling at tick 17 (8 + LINGER - 1), thanks to the refresh.
		assertTrue(tracker.isSkilling(17, LINGER));
		// The confirmed skill label is unchanged.
		assertEquals("Woodcutting", tracker.getConfirmedSkill(17, LINGER));
	}

	@Test
	public void targetSustainBridgesAnimationGapsBeyondLinger()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		int spot = 0xF15; // opaque fishing-spot identity token

		// Tick 0: a confirmed fishing animation while interacting with the spot.
		assertTrue(tracker.recordSkillSignal("Fishing", 0));
		assertTrue(tracker.recordInteractionTarget(spot, 0));

		// Far past the linger window with NO new animation signal, but still
		// interacting with the same spot: skilling stays alive and labelled.
		int tick = LINGER + 50;
		assertTrue(tracker.recordInteractionTarget(spot, tick));
		assertTrue(tracker.isSkilling(tick, LINGER));
		assertEquals("Fishing", tracker.getConfirmedSkill(tick, LINGER));
	}

	@Test
	public void targetSustainDropsInstantlyWhenInteractionEnds()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		int spot = 0xF15;
		tracker.recordSkillSignal("Fishing", 0);
		tracker.recordInteractionTarget(spot, 0);
		assertTrue(tracker.isSkilling(5, LINGER));

		// Spot depletes / player walks away: the target clears this tick and the
		// bridge ends immediately (only the short linger could now apply).
		assertFalse(tracker.recordInteractionTarget(SkillingActivityTracker.NO_TARGET, 6));
		assertFalse(tracker.isSustainedByTarget());
	}

	@Test
	public void targetSustainRequiresAConfirmedSkillingSignal()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		int npc = 0xC0B;

		// Interacting with an NPC but no skilling animation was ever confirmed
		// (e.g. plain combat): target-based sustain must NOT engage, or combat
		// would be misread as skilling.
		assertFalse(tracker.recordInteractionTarget(npc, 0));
		assertFalse(tracker.isSustainedByTarget());
		assertFalse(tracker.isSkilling(0, LINGER));
	}

	@Test
	public void cancelLingerEndsTargetSustain()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		int spot = 0xF15;
		tracker.recordSkillSignal("Fishing", 0);
		tracker.recordInteractionTarget(spot, 0);
		assertTrue(tracker.isSustainedByTarget());

		// Movement cancels the linger; target sustain must end on the same tick so
		// walking away from a fishing spot drops skilling immediately.
		tracker.cancelLinger();
		assertFalse(tracker.isSustainedByTarget());
		assertFalse(tracker.isSkilling(0, LINGER));
	}

	@Test
	public void cancelLingerImmediatelyStopsSkillingAndClearsLabel()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordSkillSignal("Fishing", 0);
		assertTrue(tracker.isSkilling(0, LINGER));

		// Player moves: the linger must be cancelled this very tick, not allowed
		// to ride out the remaining window (the fishing-walk-to-bank false positive).
		tracker.cancelLinger();

		assertFalse(tracker.isSkilling(0, LINGER));
		assertNull(tracker.getConfirmedSkill(0, LINGER));
	}

	@Test
	public void cancelLingerClearsStaleSkillSoItCannotReattach()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordSkillSignal("Fishing", 0);
		tracker.cancelLinger();

		// A later, unrelated activity signal (e.g. an animation) must not resurrect
		// the old "Fishing" label — the confirmed skill was cleared on cancel.
		tracker.recordActivitySignal(20);
		assertNull(tracker.getConfirmedSkill(20, LINGER));
	}

	@Test
	public void resetClearsAllState()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordSkillSignal("Fishing", 0);
		tracker.reset();

		// After a reset (logout / plugin start) nothing is skilling and no label
		// or sustained target survives.
		assertFalse(tracker.isSkilling(0, LINGER));
		assertFalse(tracker.isSustainedByTarget());
		assertNull(tracker.getConfirmedSkill(0, LINGER));
	}
}
