package com.idlefamiliar;

final class SoundActivation
{
	private SoundActivation()
	{
	}

	static boolean chatMessage(IdleFamiliarConfig config)
	{
		return config.chatMessageSounds();
	}

	static boolean animationStart(IdleFamiliarConfig config, AvatarState state)
	{
		return config.animationStartSounds() && avatarTrigger(config, state);
	}

	static boolean animationEnd(IdleFamiliarConfig config, AvatarState state)
	{
		return config.animationEndSounds() && avatarTrigger(config, state);
	}

	static boolean gameEvent(IdleFamiliarConfig config, GameSoundEvent event)
	{
		switch (event)
		{
			case SKILLING:
				return config.skillingSounds();
			case INVENTORY_FULL:
				return config.inventoryFullSounds();
			case GRAND_EXCHANGE:
				return config.grandExchangeSounds();
			case LOW_HEALTH:
				return config.lowHpSounds();
			case LOW_PRAYER:
				return config.lowPrayerSounds();
			default:
				return true;
		}
	}

	private static boolean avatarTrigger(IdleFamiliarConfig config, AvatarState state)
	{
		switch (state)
		{
			case PLAYER_IDLE:
				return config.idleSounds();
			case AFK_WARNING:
				return config.afkSounds();
			case SKILLING:
				return config.skillingSounds();
			case INVENTORY_FULL:
				return config.inventoryFullSounds();
			case GRAND_EXCHANGE:
				return config.grandExchangeSounds();
			case LOW_HEALTH:
				return config.lowHpSounds();
			case LOW_PRAYER:
				return config.lowPrayerSounds();
			case CUSTOM_EVENT:
				return config.chatMessageSounds();
			default:
				return true;
		}
	}
}
