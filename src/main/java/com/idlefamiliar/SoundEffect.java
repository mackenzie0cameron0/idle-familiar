package com.idlefamiliar;

/**
 * Bundled short audio cues. Filenames are intentionally stable so avatar packs or
 * future config can refer to the same ids without depending on Java enum names.
 */
public enum SoundEffect
{
	FROG_CROAK("frog_croak.wav"),
	COIN_CHIME("coin_chime.wav"),
	INVENTORY_POP("inventory_pop.wav"),
	PRAYER_CHIME("prayer_chime.wav"),
	LOW_HP_ALARM("low_hp_alarm.wav");

	private final String fileName;

	SoundEffect(String fileName)
	{
		this.fileName = fileName;
	}

	public String fileName()
	{
		return fileName;
	}
}
