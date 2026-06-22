package com.idlefamiliar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SoundCueGateTest
{
	@Test
	public void suppressesMatchingAnimationStartOnce()
	{
		SoundCueGate gate = new SoundCueGate();

		gate.suppressAnimationStart("teleporting", 10, 2);

		assertTrue(gate.consumeSuppressedAnimationStart("teleporting", 11));
		assertFalse(gate.consumeSuppressedAnimationStart("teleporting", 11));
	}

	@Test
	public void doesNotSuppressDifferentAnimationKey()
	{
		SoundCueGate gate = new SoundCueGate();

		gate.suppressAnimationStart("grand_exchange", 5, 2);

		assertFalse(gate.consumeSuppressedAnimationStart("skilling", 6));
		assertTrue(gate.consumeSuppressedAnimationStart("grand_exchange", 6));
	}

	@Test
	public void suppressionExpires()
	{
		SoundCueGate gate = new SoundCueGate();

		gate.suppressAnimationStart("combat", 3, 1);

		assertFalse(gate.consumeSuppressedAnimationStart("combat", 5));
	}

	@Test
	public void tracksMultiplePendingSuppressions()
	{
		SoundCueGate gate = new SoundCueGate();

		gate.suppressAnimationStart("teleporting", 12, 2);
		gate.suppressAnimationStart("magic_loop", 12, 2);

		assertTrue(gate.consumeSuppressedAnimationStart("teleporting", 13));
		assertTrue(gate.consumeSuppressedAnimationStart("magic_loop", 13));
	}
}
