package com.idlefamiliar;

import net.runelite.api.ChatMessageType;

public final class ChatSoundFilter
{
	private ChatSoundFilter()
	{
	}

	public static boolean isAudible(ChatMessageType type, String message)
	{
		if (message != null && message.startsWith("Idle Familiar:"))
		{
			return false;
		}
		return type == ChatMessageType.PUBLICCHAT
			|| type == ChatMessageType.MODCHAT
			|| type == ChatMessageType.PRIVATECHAT
			|| type == ChatMessageType.MODPRIVATECHAT
			|| type == ChatMessageType.FRIENDSCHAT
			|| type == ChatMessageType.CLAN_CHAT
			|| type == ChatMessageType.CLAN_MESSAGE
			|| type == ChatMessageType.CLAN_GUEST_CHAT
			|| type == ChatMessageType.CLAN_GUEST_MESSAGE
			|| type == ChatMessageType.CLAN_GIM_CHAT
			|| type == ChatMessageType.CLAN_GIM_MESSAGE
			|| type == ChatMessageType.TRADE
			|| type == ChatMessageType.TRADEREQ;
	}

	public static boolean isFilteredTrigger(ChatMessageType type, String message, String filters)
	{
		if (message != null && message.startsWith("Idle Familiar:"))
		{
			return false;
		}
		return type != ChatMessageType.SPAM
			&& type != ChatMessageType.UNKNOWN
			&& ChatMessageMatcher.matches(message, filters);
	}
}
