package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Test;

public class SoundControllerTest
{
	@Test
	public void everyBundledSoundEffectHasReadableWavResource() throws Exception
	{
		SoundController controller = new SoundController();

		assertEquals(5, SoundEffect.values().length);
		for (SoundEffect effect : SoundEffect.values())
		{
			assertTrue("Missing resource for " + effect.fileName(), controller.hasSoundResource(effect));
			try (InputStream raw = SoundController.class.getResourceAsStream(SoundController.RESOURCE_DIR + effect.fileName());
				AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(raw)))
			{
				assertNotNull(audio.getFormat());
				assertTrue("Empty sound: " + effect.fileName(), audio.getFrameLength() > 0);
			}
		}
	}

	@Test
	public void semanticGameEventsHaveDefaultSoundMappings()
	{
		SoundController controller = new SoundController();

		for (GameSoundEvent event : GameSoundEvent.values())
		{
			assertNotNull("Missing mapping for " + event, controller.soundForGameEvent(event));
		}
	}

	@Test
	public void animationStartAndEndMappingsUseBundledSounds()
	{
		SoundController controller = new SoundController();

		assertEquals(SoundEffect.PRAYER_CHIME, controller.soundForAnimationStart(AvatarState.PLAYER_IDLE));
		assertEquals(SoundEffect.INVENTORY_POP, controller.soundForAnimationStart(AvatarState.SKILLING));
		assertEquals(SoundEffect.FROG_CROAK, controller.soundForAnimationStart(AvatarState.AFK_WARNING));
		// Teleporting is intentionally silent (no placeholder cue).
		assertNull(controller.soundForAnimationStart(AvatarState.TELEPORTING));
		assertEquals(SoundEffect.INVENTORY_POP, controller.soundForAnimationEnd(AvatarState.SKILLING));
	}

	@Test
	public void disabledPlaybackIsANoOp()
	{
		SoundController controller = new SoundController();

		controller.setEnabled(false);
		controller.playGameEvent(GameSoundEvent.LEVEL_UP);
		controller.playAnimationStart(AvatarState.SKILLING, "Fishing");
		controller.playAnimationEnd(AvatarState.SKILLING, "Fishing");
		controller.playChatMessage();
	}
}
