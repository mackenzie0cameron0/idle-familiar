package com.idlefamiliar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlayerActivityServiceTest
{
	@Test
	public void loggedOutHasHighestPriority()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(false);
		service.setLowHitpoints(true);

		assertEquals(AvatarState.LOGGED_OUT, service.resolveState(false, false));
	}

	@Test
	public void resolvesUrgentStatesBeforeActivityStates()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setInCombat(true);
		service.setLowPrayer(true);

		assertEquals(AvatarState.LOW_PRAYER, service.resolveState(false, false));
	}

	@Test
	public void resolvesAfkBeforeInventoryAndCombat()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setInventoryFull(true);
		service.setInCombat(true);

		assertEquals(AvatarState.AFK_WARNING, service.resolveState(true, true));
	}

	@Test
	public void customEventResolvesAboveAfkButBelowLevelUp()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setCustomEvent(true);

		assertEquals(AvatarState.CUSTOM_EVENT, service.resolveState(true, true));
		service.setLevelUp(true);
		assertEquals(AvatarState.LEVEL_UP, service.resolveState(true, true));
	}

	@Test
	public void resolvesSkillingBeforeInventoryFull()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setInventoryFull(true);
		service.setSkilling(true);

		assertEquals(AvatarState.SKILLING, service.resolveState(false, false));
	}

	@Test
	public void resolvesIdleWhenNoHigherPriorityStateApplies()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);

		assertEquals(AvatarState.PLAYER_IDLE, service.resolveState(true, false));
		assertEquals(AvatarState.PLAYER_ACTIVE, service.resolveState(false, false));
	}

	@Test
	public void inventoryFullResolvesAboveGrandExchange()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setInventoryFull(true);
		service.setGrandExchangeFilled(true);

		assertEquals(AvatarState.INVENTORY_FULL, service.resolveState(false, false));
	}

	@Test
	public void grandExchangeResolvesAboveLocomotion()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setWalking(true);
		service.setGrandExchangeFilled(true);

		assertEquals(AvatarState.GRAND_EXCHANGE, service.resolveState(false, false));
	}

	@Test
	public void bankingResolvesAboveCombat()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setBanking(true);
		service.setInCombat(true);

		// Opening a bank involves interacting with a banker NPC/booth; it must never
		// be misread as combat, so BANKING outranks COMBAT.
		assertEquals(AvatarState.BANKING, service.resolveState(false, false));
	}

	@Test
	public void teleportingResolvesAboveSkilling()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setSkilling(true);
		service.setTeleporting(true);

		// A teleport cast also drops Magic XP (which marks skilling); the teleport
		// must win so it isn't masked as "skilling Magic".
		assertEquals(AvatarState.TELEPORTING, service.resolveState(false, false));
	}

	@Test
	public void teleportingResolvesAboveInventoryFull()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setInventoryFull(true);
		service.setTeleporting(true);

		// Teleporting is a momentary committed action, shown above standing conditions.
		assertEquals(AvatarState.TELEPORTING, service.resolveState(false, false));
	}

	@Test
	public void teleportingResolvesAboveWalking()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setWalking(true);
		service.setTeleporting(true);

		assertEquals(AvatarState.TELEPORTING, service.resolveState(false, false));
	}

	@Test
	public void walkingResolvesAboveIdleAndActive()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setWalking(true);

		// Walking outranks both idle and active.
		assertEquals(AvatarState.WALKING, service.resolveState(true, false));
		assertEquals(AvatarState.WALKING, service.resolveState(false, false));
	}

	@Test
	public void runningResolvesAboveIdleButBelowWalking()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setRunning(true);

		assertEquals(AvatarState.RUNNING, service.resolveState(false, false));
	}

	@Test
	public void skillingResolvesAboveGrandExchangeAndLocomotion()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setSkilling(true);
		service.setGrandExchangeFilled(true);
		service.setRunning(true);

		assertEquals(AvatarState.SKILLING, service.resolveState(false, false));
	}

	@Test
	public void resetClearsNewFlags()
	{
		PlayerActivityService service = new PlayerActivityService();
		service.setLoggedIn(true);
		service.setWalking(true);
		service.setRunning(true);
		service.setTeleporting(true);
		service.setGrandExchangeFilled(true);
		service.setCustomEvent(true);
		service.reset();
		service.setLoggedIn(true);

		assertEquals(AvatarState.PLAYER_ACTIVE, service.resolveState(false, false));
	}
}
