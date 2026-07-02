package com.idlefamiliar;

import java.util.EnumSet;
import java.util.Set;

/**
 * Arm/clear state machine for the desktop widget's "attention shimmer" — the
 * passive on-panel gleam shown when the player should tab back to the game.
 *
 * <p><b>Arming.</b> The shimmer arms on the <i>rising edge</i> of a trigger
 * (the condition became true this tick): inventory just filled, AFK warning,
 * low HP, low prayer, death, or level-up. Low HP/prayer are <b>danger</b>
 * triggers; the rest are normal. A danger trigger while already armed upgrades
 * the arm to danger.
 *
 * <p><b>Clearing.</b> A normal arm clears as soon as the avatar enters any
 * {@linkplain #ACTIVE_STATES active state} — evidence of player input.
 * A danger arm clears only when every vital that armed it recovers above its
 * threshold, or the player enters an active <b>non-combat</b> state:
 * {@code COMBAT} is deliberately whitelisted for danger arms, because
 * auto-retaliate puts an AFK player in combat without any input, and a low-HP
 * warning must survive exactly that situation. Combat still clears normal arms.
 *
 * <p><b>Re-arming.</b> Only a fresh edge re-arms. Once cleared, a condition
 * that stays true (e.g. a still-full inventory while power-mining) does not
 * re-shimmer until it goes false and true again.
 *
 * <p>Pure and client-free, following the pattern of {@link SoundCueGate} and
 * {@link WeightedVariantPicker}, so the full state machine is unit-testable.
 * Driven once per game tick on the client thread.
 */
public class ShimmerController
{
	/** States that count as evidence of player input and can clear the shimmer. */
	private static final Set<AvatarState> ACTIVE_STATES = EnumSet.of(
		AvatarState.PLAYER_ACTIVE,
		AvatarState.SKILLING,
		AvatarState.COMBAT,
		AvatarState.BANKING,
		AvatarState.GRAND_EXCHANGE,
		AvatarState.TELEPORTING,
		AvatarState.WALKING,
		AvatarState.RUNNING);

	private boolean active;
	/** Whether the current arm came from a danger trigger (low HP / low prayer). */
	private boolean danger;
	/** Which vital(s) armed the danger; a danger arm needs all of its vitals safe to vital-clear. */
	private boolean dangerLowHp;
	private boolean dangerLowPrayer;

	// Previous-tick values for rising-edge detection.
	private boolean prevInvFull;
	private boolean prevAfk;
	private boolean prevLowHp;
	private boolean prevLowPrayer;
	private boolean prevDeath;
	private boolean prevLevelUp;

	/**
	 * Advance the state machine one game tick. Clears are evaluated before arms,
	 * so a fresh trigger edge on the same tick still arms the shimmer.
	 *
	 * @param state          the avatar state resolved this tick
	 * @param inventoryFull  RAW inventory fullness (28/28 slots), not the resolved
	 *                       {@code INVENTORY_FULL} state (skilling outranks that state)
	 * @param lowHp          the low-HP warning condition this tick
	 * @param lowPrayer      the low-prayer warning condition this tick
	 * @param afkWarning     the AFK-warning condition this tick
	 * @param death          the death reaction condition this tick
	 * @param levelUp        the level-up flourish condition this tick
	 * @param hpSafe         hitpoints strictly above the low-HP threshold (false at 0 HP)
	 * @param prayerSafe     prayer strictly above the low-prayer threshold
	 */
	public void tick(
		AvatarState state,
		boolean inventoryFull,
		boolean lowHp, boolean lowPrayer,
		boolean afkWarning, boolean death, boolean levelUp,
		boolean hpSafe, boolean prayerSafe)
	{
		if (active)
		{
			boolean activeState = ACTIVE_STATES.contains(state);
			if (danger)
			{
				boolean vitalsRecovered = (!dangerLowHp || hpSafe) && (!dangerLowPrayer || prayerSafe);
				boolean activeNonCombat = activeState && state != AvatarState.COMBAT;
				if (vitalsRecovered || activeNonCombat)
				{
					clear();
				}
			}
			else if (activeState)
			{
				clear();
			}
		}

		boolean invEdge = inventoryFull && !prevInvFull;
		boolean afkEdge = afkWarning && !prevAfk;
		boolean lowHpEdge = lowHp && !prevLowHp;
		boolean lowPrayerEdge = lowPrayer && !prevLowPrayer;
		boolean deathEdge = death && !prevDeath;
		boolean levelUpEdge = levelUp && !prevLevelUp;

		if (invEdge || afkEdge || deathEdge || levelUpEdge)
		{
			active = true;
		}
		if (lowHpEdge || lowPrayerEdge)
		{
			active = true;
			danger = true;
			dangerLowHp |= lowHpEdge;
			dangerLowPrayer |= lowPrayerEdge;
		}

		prevInvFull = inventoryFull;
		prevAfk = afkWarning;
		prevLowHp = lowHp;
		prevLowPrayer = lowPrayer;
		prevDeath = death;
		prevLevelUp = levelUp;
	}

	/** @return whether the shimmer should be drawn this tick. */
	public boolean isActive()
	{
		return active;
	}

	/** Clear everything, including the edge trackers. Call on logout. */
	public void reset()
	{
		clear();
		prevInvFull = false;
		prevAfk = false;
		prevLowHp = false;
		prevLowPrayer = false;
		prevDeath = false;
		prevLevelUp = false;
	}

	private void clear()
	{
		active = false;
		danger = false;
		dangerLowHp = false;
		dangerLowPrayer = false;
	}
}
