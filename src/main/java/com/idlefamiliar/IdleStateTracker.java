package com.idlefamiliar;

public class IdleStateTracker
{
	private int currentTick;
	private int lastActivityTick;
	private ActivityType lastActivityType = ActivityType.UNKNOWN;

	public void reset(int tick)
	{
		currentTick = tick;
		lastActivityTick = tick;
		lastActivityType = ActivityType.UNKNOWN;
	}

	public void setCurrentTick(int currentTick)
	{
		this.currentTick = currentTick;
	}

	public void markActivity(ActivityType activityType)
	{
		lastActivityTick = currentTick;
		lastActivityType = activityType;
	}

	public int getCurrentTick()
	{
		return currentTick;
	}

	public int getTicksSinceActivity()
	{
		return Math.max(0, currentTick - lastActivityTick);
	}

	public ActivityType getLastActivityType()
	{
		return lastActivityType;
	}

	public boolean isIdle(int idleThresholdTicks)
	{
		return getTicksSinceActivity() >= idleThresholdTicks;
	}

	public boolean isAfkWarning(int afkWarningThresholdTicks)
	{
		return getTicksSinceActivity() >= afkWarningThresholdTicks;
	}
}
