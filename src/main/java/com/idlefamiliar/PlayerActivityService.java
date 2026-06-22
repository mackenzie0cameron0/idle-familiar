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
	 * The avatar state PRIORITY LADDER. When several conditions are true at once,
	 * the avatar can only show one — the highest-priority match wins. This method
	 * is the single place that ordering lives: each {@code if} below is one rung,
	 * highest priority first. To change which reaction wins, MOVE a whole
	 * {@code if (...) return ...;} block up or down. The current order, top to
	 * bottom, is:
	 *
	 * <ol>
	 *   <li>LOGGED_OUT      - not logged in (overrides everything)</li>
	 *   <li>DEATH           - the player just died (hitpoints hit 0)</li>
	 *   <li>LOW_HEALTH      - hitpoints at/under the low-HP threshold</li>
	 *   <li>LOW_PRAYER      - prayer at/under the low-prayer threshold</li>
	 *   <li>LEVEL_UP        - a real level just increased (momentary flourish)</li>
	 *   <li>CUSTOM_EVENT    - a configured chat message just matched</li>
	 *   <li>AFK_WARNING     - idle past the AFK-warning threshold</li>
	 *   <li>BANKING         - bank interface open (kept above combat)</li>
	 *   <li>COMBAT          - confirmed combat (real hit evidence)</li>
	 *   <li>TELEPORTING     - teleport just cast (above skilling so Magic XP can't mask it)</li>
	 *   <li>SKILLING        - actively skilling</li>
	 *   <li>INVENTORY_FULL  - inventory just filled</li>
	 *   <li>GRAND_EXCHANGE  - a GE offer just filled</li>
	 *   <li>WALKING / RUNNING - locomotion</li>
	 *   <li>PLAYER_IDLE     - idle threshold reached, nothing else happening</li>
	 *   <li>PLAYER_ACTIVE   - logged in, doing something unclassified (fallthrough)</li>
	 * </ol>
	 */
	public AvatarState resolveState(boolean idle, boolean afkWarning)
	{
		if (!loggedIn)
		{
			return AvatarState.LOGGED_OUT;
		}

		// Death sits at the top of the logged-in ladder: hitpoints hit 0, which would
		// also trip LOW_HEALTH, so it must win.
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

		// A real level-up is a brief celebratory flourish that interrupts ordinary
		// activity, but not a low-vitals warning or death.
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

		// Banking sits above combat: opening a bank means interacting with a banker
		// NPC / booth, which must never be misread as combat. It is also a clear,
		// deliberate activity worth its own state.
		if (banking)
		{
			return AvatarState.BANKING;
		}

		if (inCombat)
		{
			return AvatarState.COMBAT;
		}

		// Teleporting is a committed, momentary action and sits ABOVE skilling: a
		// teleport cast also drops Magic XP, which would otherwise mark the avatar
		// as "skilling Magic" and mask the teleport. Showing the teleport is the
		// accurate read of what just happened.
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

		// A filled GE offer is a discrete, actionable event worth surfacing even
		// while the player is walking, so it sits just above the locomotion states.
		if (grandExchangeFilled)
		{
			return AvatarState.GRAND_EXCHANGE;
		}

		// Locomotion is the "default active" state when nothing more specific is
		// happening; walking and running are distinct so the avatar can show
		// different sheets for each.
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

	public boolean isInCombat()
	{
		return inCombat;
	}

	public void setBanking(boolean banking)
	{
		this.banking = banking;
	}

	public boolean isBanking()
	{
		return banking;
	}

	public void setGrandExchangeFilled(boolean grandExchangeFilled)
	{
		this.grandExchangeFilled = grandExchangeFilled;
	}

	public boolean isGrandExchangeFilled()
	{
		return grandExchangeFilled;
	}

	public void setTeleporting(boolean teleporting)
	{
		this.teleporting = teleporting;
	}

	public boolean isTeleporting()
	{
		return teleporting;
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

	public boolean isWalking()
	{
		return walking;
	}

	public void setRunning(boolean running)
	{
		this.running = running;
	}

	public boolean isRunning()
	{
		return running;
	}
}
