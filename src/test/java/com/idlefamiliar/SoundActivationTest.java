package com.idlefamiliar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SoundActivationTest
{
	@Test
	public void defaultSoundTriggersMatchSidebarDefaults()
	{
		IdleFamiliarConfig config = new IdleFamiliarConfig()
		{
		};

		assertTrue(SoundActivation.animationStart(config, AvatarState.PLAYER_IDLE));
		assertTrue(SoundActivation.animationStart(config, AvatarState.AFK_WARNING));
		assertTrue(SoundActivation.animationStart(config, AvatarState.SKILLING));
		assertFalse(SoundActivation.animationEnd(config, AvatarState.SKILLING));

		assertTrue(SoundActivation.gameEvent(config, GameSoundEvent.SKILLING));
		assertTrue(SoundActivation.gameEvent(config, GameSoundEvent.INVENTORY_FULL));
		assertTrue(SoundActivation.gameEvent(config, GameSoundEvent.GRAND_EXCHANGE));
		assertTrue(SoundActivation.gameEvent(config, GameSoundEvent.LOW_HEALTH));
		assertTrue(SoundActivation.gameEvent(config, GameSoundEvent.LOW_PRAYER));
		assertFalse(SoundActivation.chatMessage(config));
	}

	@Test
	public void triggerTogglesDisableMatchingAnimationAndGameEventSounds()
	{
		IdleFamiliarConfig config = disabledRequestedTriggers();

		assertFalse(SoundActivation.animationStart(config, AvatarState.PLAYER_IDLE));
		assertFalse(SoundActivation.animationStart(config, AvatarState.AFK_WARNING));
		assertFalse(SoundActivation.animationStart(config, AvatarState.SKILLING));
		assertFalse(SoundActivation.animationStart(config, AvatarState.INVENTORY_FULL));
		assertFalse(SoundActivation.animationStart(config, AvatarState.GRAND_EXCHANGE));
		assertFalse(SoundActivation.animationStart(config, AvatarState.LOW_HEALTH));
		assertFalse(SoundActivation.animationStart(config, AvatarState.LOW_PRAYER));
		assertFalse(SoundActivation.animationStart(config, AvatarState.CUSTOM_EVENT));

		assertFalse(SoundActivation.gameEvent(config, GameSoundEvent.SKILLING));
		assertFalse(SoundActivation.gameEvent(config, GameSoundEvent.INVENTORY_FULL));
		assertFalse(SoundActivation.gameEvent(config, GameSoundEvent.GRAND_EXCHANGE));
		assertFalse(SoundActivation.gameEvent(config, GameSoundEvent.LOW_HEALTH));
		assertFalse(SoundActivation.gameEvent(config, GameSoundEvent.LOW_PRAYER));
		assertFalse(SoundActivation.chatMessage(config));

		// States/events with no dedicated toggle (combat / level-up / death / ...) are
		// opt-in and stay silent, rather than falling through to a default-on cue.
		assertFalse(SoundActivation.animationStart(config, AvatarState.COMBAT));
		assertFalse(SoundActivation.gameEvent(config, GameSoundEvent.COMBAT));
	}

	@Test
	public void globalAnimationTogglesStillApply()
	{
		IdleFamiliarConfig config = new IdleFamiliarConfig()
		{
			@Override
			public boolean animationStartSounds()
			{
				return false;
			}

			@Override
			public boolean animationEndSounds()
			{
				return true;
			}
		};

		assertFalse(SoundActivation.animationStart(config, AvatarState.PLAYER_IDLE));
		assertTrue(SoundActivation.animationEnd(config, AvatarState.SKILLING));
	}

	private static IdleFamiliarConfig disabledRequestedTriggers()
	{
		return new IdleFamiliarConfig()
		{
			@Override
			public boolean skillingSounds()
			{
				return false;
			}

			@Override
			public boolean inventoryFullSounds()
			{
				return false;
			}

			@Override
			public boolean grandExchangeSounds()
			{
				return false;
			}

			@Override
			public boolean idleSounds()
			{
				return false;
			}

			@Override
			public boolean afkSounds()
			{
				return false;
			}

			@Override
			public boolean lowPrayerSounds()
			{
				return false;
			}

			@Override
			public boolean lowHpSounds()
			{
				return false;
			}

			@Override
			public boolean chatMessageSounds()
			{
				return false;
			}
		};
	}
}
