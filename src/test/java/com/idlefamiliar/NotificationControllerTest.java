package com.idlefamiliar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NotificationControllerTest
{
	@Test
	public void highPriorityNotificationReplacesLowerPriorityNotification()
	{
		NotificationController controller = new NotificationController();
		controller.show("XP gained", 1, 5000);
		controller.show("Low HP", 5, 5000);

		assertEquals("Low HP", controller.getMessage(AvatarState.PLAYER_ACTIVE, ActivityType.UNKNOWN));
	}

	@Test
	public void persistentStateMessageIsUsedWithoutNotification()
	{
		NotificationController controller = new NotificationController();

		assertEquals("Inventory full", controller.getMessage(AvatarState.INVENTORY_FULL, ActivityType.UNKNOWN));
	}

	@Test
	public void skillingMessageUsesActivityLabel()
	{
		NotificationController controller = new NotificationController();

		assertEquals("Fishing", controller.getMessage(AvatarState.SKILLING, ActivityType.SKILLING, "Fishing"));
	}
}
