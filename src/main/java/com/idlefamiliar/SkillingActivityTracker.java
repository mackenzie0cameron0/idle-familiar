package com.idlefamiliar;

import java.util.Set;

/**
 * Tracks whether the player is actively skilling, using the plugin's native
 * game-tick clock and a linger window.
 *
 * <p>This is the testable heart of the two skilling state-machine fixes:
 *
 * <ul>
 *   <li><b>Flicker fix:</b> a skilling signal keeps the SKILLING state alive for
 *       {@code lingerTicks} ticks. As long as fresh signals keep arriving inside
 *       that window (e.g. the gap between fishing casts), {@link #isSkilling}
 *       stays {@code true} and the avatar never drops to idle for a frame.</li>
 *   <li><b>Linger fix:</b> once signals stop, the window expires exactly
 *       {@code lingerTicks} ticks after the <em>last</em> active signal — no
 *       longer. Passive XP sources (prayer drain, hitpoints regeneration) are
 *       ignored entirely, so they can neither start nor extend skilling, and
 *       they never overwrite the confirmed skill label.</li>
 * </ul>
 *
 * <p>The class is intentionally free of RuneLite and Swing dependencies so it
 * can be unit tested in isolation.
 */
public class SkillingActivityTracker
{
	/**
	 * Skills whose stat changes are passive side effects rather than active
	 * player skilling. Hitpoints regenerate over time and prayer points drain,
	 * both of which fire stat changes that must not be mistaken for skilling unless
	 * the plugin has separately confirmed a real XP gain.
	 */
	static final Set<String> PASSIVE_SKILLS = Set.of("prayer", "hitpoints");

	/** Sentinel meaning "no skilling signal has ever been recorded". */
	private static final int NO_SIGNAL = Integer.MIN_VALUE;

	/** Sentinel meaning "not interacting with any actor". */
	static final int NO_TARGET = 0;

	private int lastSignalTick = NO_SIGNAL;
	private String confirmedSkill;

	/**
	 * Opaque identity token of the actor the player is currently interacting with
	 * (e.g. a fishing spot or pickpocket target), or {@link #NO_TARGET}. The plugin
	 * supplies this from {@code System.identityHashCode(getInteracting())} so the
	 * tracker stays free of RuneLite types.
	 */
	private int targetToken = NO_TARGET;

	/**
	 * Tick at which {@link #targetToken} was last confirmed as a skilling target
	 * (i.e. a whitelisted skilling animation fired while interacting with it), or
	 * {@link #NO_SIGNAL} if the current target has not been confirmed.
	 */
	private int targetConfirmedTick = NO_SIGNAL;

	/**
	 * Returns {@code true} if {@code skill} is a passive XP source that should
	 * not count as active skilling.
	 */
	public static boolean isPassiveSkill(String skill)
	{
		return skill != null && PASSIVE_SKILLS.contains(skill.toLowerCase());
	}

	/**
	 * Record a confirmed skilling signal from a stat change.
	 *
	 * @param skill the skill name (e.g. {@code "Fishing"}); may be {@code null}
	 * @param tick  the current game tick
	 * @return {@code true} if the signal counted as active skilling, {@code false}
	 *         if it was ignored as a passive source
	 */
	public boolean recordSkillSignal(String skill, int tick)
	{
		if (isPassiveSkill(skill))
		{
			return false;
		}
		return recordConfirmedSkillSignal(skill, tick);
	}

	/**
	 * Record a skill signal that has already been proven active by a real XP gain.
	 * This lets Prayer XP animate while plain prayer-point drain remains ignored.
	 */
	public boolean recordXpSkillSignal(String skill, int tick)
	{
		return recordConfirmedSkillSignal(skill, tick);
	}

	private boolean recordConfirmedSkillSignal(String skill, int tick)
	{
		lastSignalTick = tick;
		if (skill != null && !skill.isEmpty())
		{
			confirmedSkill = skill;
		}
		return true;
	}

	/**
	 * Record a non-stat skilling signal (e.g. an ongoing tool animation) that
	 * refreshes the linger window without changing the confirmed skill label.
	 */
	public void recordActivitySignal(int tick)
	{
		lastSignalTick = tick;
	}

	/**
	 * Record the actor the player is interacting with this tick and decide whether
	 * <em>target-based sustain</em> currently holds skilling alive.
	 *
	 * <p>NPC-interaction skills (fishing, thieving) have real animation gaps between
	 * actions, so a global linger has to choose between flickering mid-activity (too
	 * short) and a long tail after it ends (too long). Interaction identity breaks
	 * that tension: while the player keeps interacting with the <em>same</em> actor
	 * that a skilling animation was confirmed against, skilling stays alive across
	 * the gaps regardless of the linger; the instant the target changes or clears,
	 * the bridge ends and only the (now short) linger remains. This is the accurate
	 * model for NPC skills and needs no fishing-spot ID list.
	 *
	 * @param targetToken opaque per-actor identity (e.g.
	 *                    {@code System.identityHashCode(actor)}), or
	 *                    {@link #NO_TARGET} when there is no interaction target
	 * @param tick        the current game tick
	 * @return {@code true} if target-based sustain currently holds skilling alive
	 */
	public boolean recordInteractionTarget(int targetToken, int tick)
	{
		// A changed or cleared target ends any existing bridge immediately — this is
		// the crisp post-stop drop (walk away / spot depletes / new target).
		if (targetToken != this.targetToken)
		{
			this.targetToken = targetToken;
			this.targetConfirmedTick = NO_SIGNAL;
		}

		// Same target AND a skilling animation was confirmed on/near this tick:
		// promote it to a sustained skilling target so future ticks bridge gaps.
		// The <= 1 window tolerates the signal landing on this tick or the previous
		// one (markSkillingSignal runs earlier in the same onGameTick).
		if (targetToken != NO_TARGET
			&& lastSignalTick != NO_SIGNAL
			&& (tick - lastSignalTick) <= 1
			&& (tick - lastSignalTick) >= 0)
		{
			this.targetConfirmedTick = tick;
		}

		return isSustainedByTarget();
	}

	/**
	 * @return {@code true} while the current interaction target has been confirmed
	 *         as a skilling target and has not since changed or cleared. Unlike the
	 *         linger window, this does not expire on its own — only a target change
	 *         or {@link #cancelLinger()} ends it.
	 */
	public boolean isSustainedByTarget()
	{
		return targetToken != NO_TARGET && targetConfirmedTick != NO_SIGNAL;
	}

	/**
	 * @return {@code true} if the most recent active signal is still within the
	 *         linger window at {@code tick}, or target-based sustain is active
	 */
	public boolean isSkilling(int tick, int lingerTicks)
	{
		// NPC-skill bridge: alive while the same confirmed target persists.
		if (isSustainedByTarget())
		{
			return true;
		}
		if (lastSignalTick == NO_SIGNAL)
		{
			return false;
		}
		return (tick - lastSignalTick) < lingerTicks;
	}

	/**
	 * @return the confirmed skill name while skilling, or {@code null} once the
	 *         linger window has expired
	 */
	public String getConfirmedSkill(int tick, int lingerTicks)
	{
		return isSkilling(tick, lingerTicks) ? confirmedSkill : null;
	}

	/**
	 * Immediately cancel any active skilling linger and forget the confirmed
	 * skill. Called when the player moves: a skilling action and walking are
	 * mutually exclusive, so movement must drop the avatar out of skilling on the
	 * same tick rather than letting the linger window ride out (the fishing →
	 * walk-to-bank false positive). Clearing {@code confirmedSkill} as well stops
	 * a stale label (e.g. "Fishing") from re-attaching to a later, unrelated
	 * skilling signal.
	 */
	public void cancelLinger()
	{
		lastSignalTick = NO_SIGNAL;
		confirmedSkill = null;
		targetToken = NO_TARGET;
		targetConfirmedTick = NO_SIGNAL;
	}

	/** Clears all tracked skilling state (e.g. on logout or plugin start). */
	public void reset()
	{
		lastSignalTick = NO_SIGNAL;
		confirmedSkill = null;
		targetToken = NO_TARGET;
		targetConfirmedTick = NO_SIGNAL;
	}
}
