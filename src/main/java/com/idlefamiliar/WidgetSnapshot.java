package com.idlefamiliar;

import java.awt.image.BufferedImage;

/**
 * An immutable, point-in-time view of everything the desktop widget needs to
 * paint, assembled once per game tick on the client thread and published through
 * a single {@code volatile} reference in {@link IdleFamiliarPlugin}.
 *
 * <p>The widget paints on the Swing EDT. Rather than reach across the thread
 * boundary for each individual value (state, vitals, label, icon, message…) —
 * which would require every backing field to be {@code volatile} and could still
 * tear a paint across several inconsistent values — the widget reads one snapshot
 * and gets a coherent set captured at the same instant. The only thing the widget
 * still pulls live is the animation frame itself ({@link IdleFamiliarPlugin#getCurrentFrame()}),
 * because that advances on the 100ms repaint timer rather than the game tick.
 *
 * <p>{@code skillIcon} is a {@link BufferedImage}, which is not deeply immutable;
 * it is treated as read-only and never mutated after the sprite is fetched, so
 * sharing the reference across threads is safe.
 */
public final class WidgetSnapshot
{
	/** A neutral logged-out view, used before the first tick has built a real one. */
	private static final WidgetSnapshot LOGGED_OUT =
		new WidgetSnapshot(AvatarState.LOGGED_OUT, "", 0, 0, 0, 0, 0, null, null, null, false);

	private final AvatarState state;
	private final String animationLabel;
	private final int hitpoints;
	private final int maxHitpoints;
	private final int prayer;
	private final int maxPrayer;
	private final int inventoryCount;
	private final String xpHr;
	private final String message;
	private final BufferedImage skillIcon;
	private final boolean shimmerActive;

	public WidgetSnapshot(AvatarState state, String animationLabel, int hitpoints, int maxHitpoints,
		int prayer, int maxPrayer, int inventoryCount, String xpHr, String message, BufferedImage skillIcon,
		boolean shimmerActive)
	{
		this.state = state;
		this.animationLabel = animationLabel;
		this.hitpoints = hitpoints;
		this.maxHitpoints = maxHitpoints;
		this.prayer = prayer;
		this.maxPrayer = maxPrayer;
		this.inventoryCount = inventoryCount;
		this.xpHr = xpHr;
		this.message = message;
		this.skillIcon = skillIcon;
		this.shimmerActive = shimmerActive;
	}

	/** @return a shared, neutral logged-out snapshot for use before the first tick. */
	public static WidgetSnapshot loggedOut()
	{
		return LOGGED_OUT;
	}

	public AvatarState getState()
	{
		return state;
	}

	/** The label fed to the animation lookup (combat sub-style, or the skill name while skilling). */
	public String getAnimationLabel()
	{
		return animationLabel;
	}

	public int getHitpoints()
	{
		return hitpoints;
	}

	public int getMaxHitpoints()
	{
		return maxHitpoints;
	}

	public int getPrayer()
	{
		return prayer;
	}

	public int getMaxPrayer()
	{
		return maxPrayer;
	}

	public int getInventoryCount()
	{
		return inventoryCount;
	}

	/** Compact XP/hour string, or {@code null} when not skilling. */
	public String getXpHr()
	{
		return xpHr;
	}

	/** Status-pill text for the current state, or {@code null}/empty for none. */
	public String getMessage()
	{
		return message;
	}

	/** Active skill-icon sprite, or {@code null} when not skilling / unmapped / still loading. */
	public BufferedImage getSkillIcon()
	{
		return skillIcon;
	}

	/** Whether the info-panel attention shimmer should be drawn this tick. */
	public boolean isShimmerActive()
	{
		return shimmerActive;
	}
}
