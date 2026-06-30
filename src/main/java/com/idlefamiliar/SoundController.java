package com.idlefamiliar;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.audio.AudioPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small local-only sound layer for Idle Familiar. It maps high-level plugin
 * events to bundled WAV resources and plays them as short, fire-and-forget cues
 * through RuneLite's {@link AudioPlayer}.
 */
public class SoundController
{
	private static final Logger log = LoggerFactory.getLogger(SoundController.class);
	static final String RESOURCE_DIR = "/com/idlefamiliar/sounds/";

	private final Map<AvatarState, SoundEffect> animationStartSounds = new EnumMap<>(AvatarState.class);
	private final Map<AvatarState, SoundEffect> animationEndSounds = new EnumMap<>(AvatarState.class);
	private final Map<GameSoundEvent, SoundEffect> gameEventSounds = new EnumMap<>(GameSoundEvent.class);

	private volatile boolean enabled = true;
	private volatile int volumePercent = 70;

	/** Supplied by the plugin once Guice has injected it; until then playback is a no-op. */
	private volatile AudioPlayer audioPlayer;

	public SoundController()
	{
		registerDefaults();
	}

	private void registerDefaults()
	{
		// Only the five bundled WAVs are mapped; states/events share the closest-
		// fitting cue. Add more sheets to SoundEffect (with matching .wav files) to
		// give any of these its own sound later.
		animationStartSounds.put(AvatarState.PLAYER_IDLE, SoundEffect.PRAYER_CHIME);
		animationStartSounds.put(AvatarState.SKILLING, SoundEffect.INVENTORY_POP);
		animationStartSounds.put(AvatarState.COMBAT, SoundEffect.LOW_HP_ALARM);
		animationStartSounds.put(AvatarState.AFK_WARNING, SoundEffect.FROG_CROAK);
		animationStartSounds.put(AvatarState.BANKING, SoundEffect.COIN_CHIME);
		animationStartSounds.put(AvatarState.GRAND_EXCHANGE, SoundEffect.COIN_CHIME);
		animationStartSounds.put(AvatarState.LEVEL_UP, SoundEffect.PRAYER_CHIME);
		animationStartSounds.put(AvatarState.DEATH, SoundEffect.FROG_CROAK);

		animationEndSounds.put(AvatarState.SKILLING, SoundEffect.INVENTORY_POP);
		animationEndSounds.put(AvatarState.COMBAT, SoundEffect.INVENTORY_POP);
		animationEndSounds.put(AvatarState.GRAND_EXCHANGE, SoundEffect.COIN_CHIME);

		gameEventSounds.put(GameSoundEvent.INVENTORY_FULL, SoundEffect.INVENTORY_POP);
		gameEventSounds.put(GameSoundEvent.LOW_HEALTH, SoundEffect.LOW_HP_ALARM);
		gameEventSounds.put(GameSoundEvent.LOW_PRAYER, SoundEffect.PRAYER_CHIME);
		gameEventSounds.put(GameSoundEvent.GRAND_EXCHANGE, SoundEffect.COIN_CHIME);
		gameEventSounds.put(GameSoundEvent.LEVEL_UP, SoundEffect.PRAYER_CHIME);
		gameEventSounds.put(GameSoundEvent.DEATH, SoundEffect.FROG_CROAK);
		gameEventSounds.put(GameSoundEvent.COMBAT, SoundEffect.LOW_HP_ALARM);
		gameEventSounds.put(GameSoundEvent.SKILLING, SoundEffect.INVENTORY_POP);
	}

	public void setAudioPlayer(AudioPlayer audioPlayer)
	{
		this.audioPlayer = audioPlayer;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	public void setVolumePercent(int volumePercent)
	{
		this.volumePercent = Math.max(0, Math.min(100, volumePercent));
	}

	public void playAnimationStart(AvatarState state, String activityLabel)
	{
		play(soundForAnimationStart(state));
	}

	public void playAnimationEnd(AvatarState state, String activityLabel)
	{
		play(soundForAnimationEnd(state));
	}

	public void playGameEvent(GameSoundEvent event)
	{
		play(soundForGameEvent(event));
	}

	public void playChatMessage()
	{
		play(SoundEffect.INVENTORY_POP);
	}

	boolean hasSoundResource(SoundEffect effect)
	{
		if (effect == null)
		{
			return false;
		}
		try (InputStream stream = openSound(effect))
		{
			return stream != null;
		}
		catch (IOException ex)
		{
			return false;
		}
	}

	SoundEffect soundForAnimationStart(AvatarState state)
	{
		return animationStartSounds.get(state);
	}

	SoundEffect soundForAnimationEnd(AvatarState state)
	{
		return animationEndSounds.get(state);
	}

	SoundEffect soundForGameEvent(GameSoundEvent event)
	{
		return gameEventSounds.get(event);
	}

	private void play(SoundEffect effect)
	{
		final AudioPlayer player = audioPlayer;
		if (!enabled || volumePercent <= 0 || effect == null || player == null)
		{
			return;
		}

		try
		{
			// Catch broad Exception on purpose: AudioPlayer#play declares the
			// javax.sound checked exceptions, and referencing those types here is
			// disallowed on the Plugin Hub. Exception covers them without naming them.
			player.play(SoundController.class, RESOURCE_DIR + effect.fileName(), gainDecibels());
		}
		catch (Exception ex)
		{
			log.debug("Unable to play Idle Familiar sound {}", effect.fileName(), ex);
		}
	}

	/** Convert the 0-100 volume into a dB gain (0 dB at 100%, quieter below). */
	private float gainDecibels()
	{
		float normalized = Math.max(0.0001f, volumePercent / 100.0f);
		return (float) (20.0 * Math.log10(normalized));
	}

	private InputStream openSound(SoundEffect effect)
	{
		return SoundController.class.getResourceAsStream(RESOURCE_DIR + effect.fileName());
	}
}
