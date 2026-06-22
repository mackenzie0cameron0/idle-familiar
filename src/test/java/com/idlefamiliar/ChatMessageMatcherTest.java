package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChatMessageMatcherTest
{
	@Test
	public void matchesThreeConsecutiveWordsInsideLongerMessage()
	{
		String message = "Some cracks around the cave begin to ooze water.";

		assertTrue(ChatMessageMatcher.matches(message, "the cave begin"));
		assertEquals("the cave begin", ChatMessageMatcher.matchedFilter(message, "the cave begin"));
	}

	@Test
	public void normalizesCaseAndPunctuation()
	{
		assertTrue(ChatMessageMatcher.matches(
			"Some cracks around the cave begin to ooze water.",
			"CAVE begin to"));
	}

	@Test
	public void ignoresShortFiltersAndCommentExample()
	{
		String filters = "# some cracks around the cave begin to ooze water.\n"
			+ "ooze water\n";

		assertFalse(ChatMessageMatcher.matches(
			"Some cracks around the cave begin to ooze water.",
			filters));
		assertNull(ChatMessageMatcher.matchedFilter("ooze water", filters));
	}

	@Test
	public void supportsMultipleLines()
	{
		String filters = "wintertodt begins to\n"
			+ "the cave begin\n";

		assertTrue(ChatMessageMatcher.matches(
			"Some cracks around the cave begin to ooze water.",
			filters));
	}
}
