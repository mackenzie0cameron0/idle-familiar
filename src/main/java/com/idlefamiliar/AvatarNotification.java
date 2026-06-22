package com.idlefamiliar;

public class AvatarNotification
{
	private final String text;
	private final int priority;
	private final long expiresAtMillis;

	public AvatarNotification(String text, int priority, long expiresAtMillis)
	{
		this.text = text;
		this.priority = priority;
		this.expiresAtMillis = expiresAtMillis;
	}

	public String getText()
	{
		return text;
	}

	public boolean isExpired(long now)
	{
		return expiresAtMillis > 0 && now >= expiresAtMillis;
	}

	public boolean canReplace(AvatarNotification other, long now)
	{
		return other == null || other.isExpired(now) || priority >= other.priority;
	}
}
