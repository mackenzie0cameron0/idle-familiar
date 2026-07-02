package com.idlefamiliar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Exercises the attention-shimmer arm/clear state machine without a client.
 * The key regression under test: a danger arm (low HP/prayer) must survive
 * {@code COMBAT}, because auto-retaliate puts an AFK player in combat without
 * any player input.
 */
public class ShimmerControllerTest
{
	/** A tick with every trigger false and safe vitals, in the given state. */
	private static void quietTick(ShimmerController c, AvatarState state)
	{
		c.tick(state, false, false, false, false, false, false, true, true);
	}

	// --- Arming on each trigger edge ---------------------------------------

	@Test
	public void armsOnInventoryFullEdge()
	{
		ShimmerController c = new ShimmerController();
		quietTick(c, AvatarState.SKILLING);
		c.tick(AvatarState.PLAYER_IDLE, true, false, false, false, false, false, true, true);
		assertTrue(c.isActive());
	}

	@Test
	public void armsOnAfkWarningEdge()
	{
		ShimmerController c = new ShimmerController();
		quietTick(c, AvatarState.PLAYER_IDLE);
		c.tick(AvatarState.AFK_WARNING, false, false, false, true, false, false, true, true);
		assertTrue(c.isActive());
	}

	@Test
	public void armsOnLowHpEdge()
	{
		ShimmerController c = new ShimmerController();
		quietTick(c, AvatarState.PLAYER_IDLE);
		c.tick(AvatarState.LOW_HEALTH, false, true, false, false, false, false, false, true);
		assertTrue(c.isActive());
	}

	@Test
	public void armsOnLowPrayerEdge()
	{
		ShimmerController c = new ShimmerController();
		quietTick(c, AvatarState.PLAYER_IDLE);
		c.tick(AvatarState.LOW_PRAYER, false, false, true, false, false, false, true, false);
		assertTrue(c.isActive());
	}

	@Test
	public void armsOnDeathEdge()
	{
		ShimmerController c = new ShimmerController();
		quietTick(c, AvatarState.PLAYER_IDLE);
		c.tick(AvatarState.DEATH, false, false, false, false, true, false, false, true);
		assertTrue(c.isActive());
	}

	@Test
	public void armsOnLevelUpEdge()
	{
		ShimmerController c = new ShimmerController();
		quietTick(c, AvatarState.SKILLING);
		c.tick(AvatarState.LEVEL_UP, false, false, false, false, false, true, true, true);
		assertTrue(c.isActive());
	}

	@Test
	public void doesNotArmWithoutAnEdge()
	{
		ShimmerController c = new ShimmerController();
		quietTick(c, AvatarState.PLAYER_IDLE);
		quietTick(c, AvatarState.PLAYER_IDLE);
		assertFalse(c.isActive());
	}

	// --- Normal-arm clearing ------------------------------------------------

	@Test
	public void normalArmClearsOnActiveState()
	{
		ShimmerController c = new ShimmerController();
		c.tick(AvatarState.PLAYER_IDLE, true, false, false, false, false, false, true, true);
		assertTrue(c.isActive());
		// Inventory still full, but the player is skilling again: clear.
		c.tick(AvatarState.SKILLING, true, false, false, false, false, false, true, true);
		assertFalse(c.isActive());
	}

	@Test
	public void normalArmClearsOnCombat()
	{
		ShimmerController c = new ShimmerController();
		c.tick(AvatarState.AFK_WARNING, false, false, false, true, false, false, true, true);
		assertTrue(c.isActive());
		c.tick(AvatarState.COMBAT, false, false, false, false, false, false, true, true);
		assertFalse(c.isActive());
	}

	@Test
	public void normalArmSurvivesNonActiveStates()
	{
		ShimmerController c = new ShimmerController();
		c.tick(AvatarState.PLAYER_IDLE, true, false, false, false, false, false, true, true);
		quietTick(c, AvatarState.PLAYER_IDLE);
		quietTick(c, AvatarState.DEFAULT);
		c.tick(AvatarState.INVENTORY_FULL, true, false, false, false, false, false, true, true);
		assertTrue(c.isActive());
	}

	// --- Danger-arm clearing (the key regression) ----------------------------

	@Test
	public void dangerArmIsNotClearedByCombat()
	{
		ShimmerController c = new ShimmerController();
		// Low HP arms while idle; auto-retaliate then drags the player into combat.
		c.tick(AvatarState.LOW_HEALTH, false, true, false, false, false, false, false, true);
		assertTrue(c.isActive());
		c.tick(AvatarState.COMBAT, false, true, false, false, false, false, false, true);
		assertTrue("COMBAT must not clear a danger arm (auto-retaliate)", c.isActive());
	}

	@Test
	public void dangerArmClearsOnActiveNonCombatState()
	{
		ShimmerController c = new ShimmerController();
		c.tick(AvatarState.LOW_HEALTH, false, true, false, false, false, false, false, true);
		c.tick(AvatarState.WALKING, false, true, false, false, false, false, false, true);
		assertFalse(c.isActive());
	}

	@Test
	public void dangerArmClearsWhenVitalRecovers()
	{
		ShimmerController c = new ShimmerController();
		c.tick(AvatarState.LOW_HEALTH, false, true, false, false, false, false, false, true);
		assertTrue(c.isActive());
		// HP back above the threshold, still no input: clear.
		c.tick(AvatarState.PLAYER_IDLE, false, false, false, false, false, false, true, true);
		assertFalse(c.isActive());
	}

	@Test
	public void lowPrayerDangerArmNeedsPrayerRecoveryNotHp()
	{
		ShimmerController c = new ShimmerController();
		c.tick(AvatarState.LOW_PRAYER, false, false, true, false, false, false, true, false);
		assertTrue(c.isActive());
		// HP is safe throughout; prayer still low — must stay armed.
		c.tick(AvatarState.COMBAT, false, false, true, false, false, false, true, false);
		assertTrue(c.isActive());
		// Prayer recovers: clear.
		c.tick(AvatarState.PLAYER_IDLE, false, false, false, false, false, false, true, true);
		assertFalse(c.isActive());
	}

	@Test
	public void doubleDangerArmNeedsBothVitalsToRecover()
	{
		ShimmerController c = new ShimmerController();
		c.tick(AvatarState.LOW_HEALTH, false, true, false, false, false, false, false, true);
		c.tick(AvatarState.LOW_PRAYER, false, true, true, false, false, false, false, false);
		// Only HP recovers; prayer still low — stays armed.
		c.tick(AvatarState.PLAYER_IDLE, false, false, true, false, false, false, true, false);
		assertTrue(c.isActive());
		// Prayer recovers too: clear.
		c.tick(AvatarState.PLAYER_IDLE, false, false, false, false, false, false, true, true);
		assertFalse(c.isActive());
	}

	@Test
	public void dangerTriggerUpgradesAnExistingNormalArm()
	{
		ShimmerController c = new ShimmerController();
		c.tick(AvatarState.PLAYER_IDLE, true, false, false, false, false, false, true, true);
		assertTrue(c.isActive());
		// Low HP fires while armed: upgrade to danger; combat must no longer clear it.
		c.tick(AvatarState.PLAYER_IDLE, true, true, false, false, false, false, false, true);
		c.tick(AvatarState.COMBAT, true, true, false, false, false, false, false, true);
		assertTrue(c.isActive());
	}

	// --- Re-arm behaviour -----------------------------------------------------

	@Test
	public void doesNotRearmWhileConditionStaysTrue()
	{
		ShimmerController c = new ShimmerController();
		c.tick(AvatarState.PLAYER_IDLE, true, false, false, false, false, false, true, true);
		// Skilling with a full inventory (e.g. power-mining) clears it...
		c.tick(AvatarState.SKILLING, true, false, false, false, false, false, true, true);
		assertFalse(c.isActive());
		// ...and it must NOT re-arm while the inventory simply stays full.
		c.tick(AvatarState.PLAYER_IDLE, true, false, false, false, false, false, true, true);
		c.tick(AvatarState.PLAYER_IDLE, true, false, false, false, false, false, true, true);
		assertFalse(c.isActive());
	}

	@Test
	public void rearmsOnAFreshEdge()
	{
		ShimmerController c = new ShimmerController();
		c.tick(AvatarState.PLAYER_IDLE, true, false, false, false, false, false, true, true);
		c.tick(AvatarState.SKILLING, true, false, false, false, false, false, true, true);
		assertFalse(c.isActive());
		// Inventory drops an item (goes false) then fills again: fresh edge, re-arm.
		c.tick(AvatarState.PLAYER_IDLE, false, false, false, false, false, false, true, true);
		c.tick(AvatarState.PLAYER_IDLE, true, false, false, false, false, false, true, true);
		assertTrue(c.isActive());
	}

	// --- Reset ----------------------------------------------------------------

	@Test
	public void resetClearsEverything()
	{
		ShimmerController c = new ShimmerController();
		c.tick(AvatarState.LOW_HEALTH, true, true, false, false, false, false, false, true);
		assertTrue(c.isActive());
		c.reset();
		assertFalse(c.isActive());
		// Edge trackers are cleared too: a still-true condition after reset is a
		// fresh edge on the next tick (login with a full inventory should shimmer).
		c.tick(AvatarState.PLAYER_IDLE, true, false, false, false, false, false, true, true);
		assertTrue(c.isActive());
	}
}
