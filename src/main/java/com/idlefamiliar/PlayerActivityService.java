package com.idlefamiliar;

public class PlayerActivityService
{
	private ActivityType currentActivity = ActivityType.UNKNOWN;
	private boolean loggedIn;
	private boolean inventoryFull;
	private boolean lowHitpoints;
	private boolean lowPrayer;
	private boolean inCombat;
	private boolean skilling;
	private boolean banking;
	private boolean grandExchangeFilled;
	private boolean teleporting;
	private boolean walking;
	private boolean running;
	private boolean dead;
	private boolean levelUp;
	private boolean customEvent;
	/** Written and read on the client thread; it reaches the widget only via the plugin's widget snapshot. */
	private String activityLabel = "";

	public void reset()
	{
		currentActivity = ActivityType.UNKNOWN;
		loggedIn = false;
		inventoryFull = false;
		lowHitpoints = false;
		lowPrayer = false;
		inCombat = false;
		skilling = false;
		banking = false;
		grandExchangeFilled = false;
		teleporting = false;
		walking = false;
		running = false;
		dead = false;
		levelUp = false;
		customEvent = false;
		activityLabel = "";
	}

	/**
	 * The avatar state PRIORITY LADDER: the highest-priority active condition wins.
	 * Each {@code if} below is one rung, highest first; move a whole block to re-rank.
	 * Order: LOGGED_OUT, DEATH, LOW_HEALTH, LOW_PRAYER, LEVEL_UP, CUSTOM_EVENT,
	 * AFK_WARNING, BANKING, COMBAT, TELEPORTING, SKILLING, INVENTORY_FULL,
	 * GRAND_EXCHANGE, WALKING/RUNNING, PLAYER_IDLE, PLAYER_ACTIVE (fallthrough).
	 */
	public AvatarState resolveState(boolean idle, boolean afkWarning)
	{
		if (!loggedIn)
		{
			return AvatarState.LOGGED_OUT;
		}

		// Death tops the logged-in ladder: HP 0 would also trip LOW_HEALTH, so it must win.
		if (dead)
		{
			return AvatarState.DEATH;
		}

		if (lowHitpoints)
		{
			return AvatarState.LOW_HEALTH;
		}

		if (lowPrayer)
		{
			return AvatarState.LOW_PRAYER;
		}

		// A brief flourish that interrupts ordinary activity, but not vitals/death.
		if (levelUp)
		{
			return AvatarState.LEVEL_UP;
		}

		if (customEvent)
		{
			return AvatarState.CUSTOM_EVENT;
		}

		if (afkWarning)
		{
			return AvatarState.AFK_WARNING;
		}

		// Banking sits above combat so interacting with a banker is never misread as combat.
		if (banking)
		{
			return AvatarState.BANKING;
		}

		if (inCombat)
		{
			return AvatarState.COMBAT;
		}

		// Teleporting sits above skilling: a teleport also drops Magic XP, which would
		// otherwise mask it as "skilling Magic".
		if (teleporting)
		{
			return AvatarState.TELEPORTING;
		}

		if (skilling)
		{
			return AvatarState.SKILLING;
		}

		if (inventoryFull)
		{
			return AvatarState.INVENTORY_FULL;
		}

		// A filled GE offer surfaces even while walking, so it sits above locomotion.
		if (grandExchangeFilled)
		{
			return AvatarState.GRAND_EXCHANGE;
		}

		// Locomotion is the default-active state; walking and running use distinct sheets.
		if (walking)
		{
			return AvatarState.WALKING;
		}

		if (running)
		{
			return AvatarState.RUNNING;
		}

		if (idle)
		{
			return AvatarState.PLAYER_IDLE;
		}

		return AvatarState.PLAYER_ACTIVE;
	}

	public ActivityType getCurrentActivity()
	{
		return currentActivity;
	}

	public void setCurrentActivity(ActivityType currentActivity)
	{
		this.currentActivity = currentActivity;
	}

	public String getActivityLabel()
	{
		return activityLabel;
	}

	public void setActivityLabel(String activityLabel)
	{
		this.activityLabel = activityLabel == null ? "" : activityLabel;
	}

	public void setLoggedIn(boolean loggedIn)
	{
		this.loggedIn = loggedIn;
	}

	public void setInventoryFull(boolean inventoryFull)
	{
		this.inventoryFull = inventoryFull;
	}

	public void setLowHitpoints(boolean lowHitpoints)
	{
		this.lowHitpoints = lowHitpoints;
	}

	public void setLowPrayer(boolean lowPrayer)
	{
		this.lowPrayer = lowPrayer;
	}

	public void setInCombat(boolean inCombat)
	{
		this.inCombat = inCombat;
	}

	public void setSkilling(boolean skilling)
	{
		this.skilling = skilling;
	}

	public boolean isSkilling()
	{
		return skilling;
	}

	public void setBanking(boolean banking)
	{
		this.banking = banking;
	}

	public void setGrandExchangeFilled(boolean grandExchangeFilled)
	{
		this.grandExchangeFilled = grandExchangeFilled;
	}

	public void setTeleporting(boolean teleporting)
	{
		this.teleporting = teleporting;
	}

	public void setDead(boolean dead)
	{
		this.dead = dead;
	}

	public void setLevelUp(boolean levelUp)
	{
		this.levelUp = levelUp;
	}

	public void setCustomEvent(boolean customEvent)
	{
		this.customEvent = customEvent;
	}

	public void setWalking(boolean walking)
	{
		this.walking = walking;
	}

	public void setRunning(boolean running)
	{
		this.running = running;
	}

}
