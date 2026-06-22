package com.idlefamiliar;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * A looping (or one-shot) sprite animation.
 *
 * <p>Frames play at a fixed base duration, except the final frame, which is
 * held for {@link #LAST_FRAME_MULTIPLIER} times as long. This gives each loop a
 * brief "settle" on the last pose before restarting, which reads better for the
 * idle/skilling poses than a perfectly uniform cadence.
 *
 * <p>Playback can be sped up or slowed down at render time via a speed
 * multiplier (1.0 = normal, 2.0 = twice as fast, 0.5 = half speed) without
 * rebuilding the animation, so the user-facing "animation speed" config can be
 * applied live.
 */
public class AvatarAnimation
{
	/** The final frame of every animation is held this many times as long. */
	static final double LAST_FRAME_MULTIPLIER = 2.0;

	private final List<BufferedImage> frames;
	private final long[] frameDurations;
	private final long totalDuration;
	private final boolean loop;

	public AvatarAnimation(List<BufferedImage> frames, long frameDurationMillis, boolean loop)
	{
		this.frames = frames;
		this.loop = loop;
		this.frameDurations = buildFrameDurations(frames.size(), frameDurationMillis);

		long total = 0;
		for (long duration : frameDurations)
		{
			total += duration;
		}
		this.totalDuration = Math.max(1, total);
	}

	private static long[] buildFrameDurations(int frameCount, long baseDurationMillis)
	{
		long base = Math.max(1, baseDurationMillis);
		long[] durations = new long[Math.max(0, frameCount)];
		for (int i = 0; i < durations.length; i++)
		{
			boolean isLastFrame = i == durations.length - 1;
			durations[i] = isLastFrame ? Math.round(base * LAST_FRAME_MULTIPLIER) : base;
		}
		return durations;
	}

	/** @return the full play time of one loop in millis (final frame held longer). */
	public long getTotalDurationMillis()
	{
		return totalDuration;
	}

	/** @return the number of frames; 1 means a static, non-looping pose. */
	public int frameCount()
	{
		return frames.size();
	}

	public BufferedImage getFrame(long now)
	{
		return getFrame(now, 1.0);
	}

	/**
	 * @param now             a monotonically increasing time source in millis
	 * @param speedMultiplier 1.0 = normal, &gt;1 faster, &lt;1 slower
	 */
	public BufferedImage getFrame(long now, double speedMultiplier)
	{
		if (frames.isEmpty())
		{
			return null;
		}

		if (frames.size() == 1)
		{
			return frames.get(0);
		}

		double speed = speedMultiplier > 0 ? speedMultiplier : 1.0;
		// Advance "animation time" faster or slower than wall-clock time.
		long animTime = (long) (now * speed);

		long target;
		if (loop)
		{
			target = ((animTime % totalDuration) + totalDuration) % totalDuration;
		}
		else
		{
			target = Math.max(0, animTime);
		}

		long accumulated = 0;
		for (int i = 0; i < frameDurations.length; i++)
		{
			accumulated += frameDurations[i];
			if (target < accumulated)
			{
				return frames.get(i);
			}
		}

		return frames.get(frames.size() - 1);
	}
}
