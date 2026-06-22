package com.idlefamiliar;

public class NotificationController
{
	private AvatarNotification current;

	public void clear()
	{
		current = null;
	}

	public void show(String text, int priority, long durationMillis)
	{
		long now = System.currentTimeMillis();
		AvatarNotification next = new AvatarNotification(text, priority, now + durationMillis);
		if (next.canReplace(current, now))
		{
			current = next;
		}
	}

	public String getMessage(AvatarState state, ActivityType activityType)
	{
		return getMessage(state, activityType, "");
	}

	public String getMessage(AvatarState state, ActivityType activityType, String activityLabel)
	{
		long now = System.currentTimeMillis();
		if (current != null && !current.isExpired(now))
		{
			return current.getText();
		}

		current = null;
		switch (state)
		{
			case LOGGED_OUT:
				return "Logged out";
			case LOW_HEALTH:
				return "Low HP";
			case LOW_PRAYER:
				return "Low prayer";
			case AFK_WARNING:
				return "Still there?";
			case INVENTORY_FULL:
				return "Inventory full";
			case DEATH:
				return "You died!";
			case LEVEL_UP:
				return "Level up!";
			case BANKING:
				return "Banking";
			case COMBAT:
				return "Combat";
			case SKILLING:
				return activityLabel == null || activityLabel.isEmpty() ? "Skilling" : activityLabel;
			case PLAYER_IDLE:
				return "Idle";
			case PLAYER_ACTIVE:
				return activityType == ActivityType.WALKING ? "Walking" : "Active";
			default:
				return "";
		}
	}
}
