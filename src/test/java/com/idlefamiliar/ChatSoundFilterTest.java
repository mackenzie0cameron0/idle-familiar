package com.idlefamiliar;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.ChatMessageType;
import org.junit.Test;

public class ChatSoundFilterTest
{
	@Test
	public void allowsPlayerAndSocialChatTypes()
	{
		assertTrue(ChatSoundFilter.isAudible(ChatMessageType.PUBLICCHAT, "hello"));
		assertTrue(ChatSoundFilter.isAudible(ChatMessageType.MODCHAT, "hello"));
		assertTrue(ChatSoundFilter.isAudible(ChatMessageType.PRIVATECHAT, "hello"));
		assertTrue(ChatSoundFilter.isAudible(ChatMessageType.FRIENDSCHAT, "hello"));
		assertTrue(ChatSoundFilter.isAudible(ChatMessageType.CLAN_MESSAGE, "hello"));
		assertTrue(ChatSoundFilter.isAudible(ChatMessageType.TRADE, "hello"));
	}

	@Test
	public void suppressesNoisySystemAndPluginMessages()
	{
		assertFalse(ChatSoundFilter.isAudible(ChatMessageType.GAMEMESSAGE, "You swing your axe."));
		assertFalse(ChatSoundFilter.isAudible(ChatMessageType.SPAM, "Filtered spam"));
		assertFalse(ChatSoundFilter.isAudible(ChatMessageType.PUBLICCHAT, "Idle Familiar: animations look good."));
	}

	@Test
	public void filteredTriggersCanMatchGameMessages()
	{
		assertTrue(ChatSoundFilter.isFilteredTrigger(
			ChatMessageType.GAMEMESSAGE,
			"Some cracks around the cave begin to ooze water.",
			"cave begin to"));
	}

	@Test
	public void filteredTriggersRejectPluginAndSpamMessages()
	{
		assertFalse(ChatSoundFilter.isFilteredTrigger(
			ChatMessageType.PUBLICCHAT,
			"Idle Familiar: animations look good.",
			"animations look good"));
		assertFalse(ChatSoundFilter.isFilteredTrigger(
			ChatMessageType.SPAM,
			"Some cracks around the cave begin to ooze water.",
			"cave begin to"));
	}
}
