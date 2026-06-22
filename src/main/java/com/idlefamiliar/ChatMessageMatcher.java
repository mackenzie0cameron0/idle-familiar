package com.idlefamiliar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChatMessageMatcher
{
	private static final int MIN_WORDS = 3;

	private ChatMessageMatcher()
	{
	}

	public static boolean matches(String message, String filters)
	{
		return matchedFilter(message, filters) != null;
	}

	public static String matchedFilter(String message, String filters)
	{
		String normalizedMessage = normalize(message);
		if (normalizedMessage.isEmpty())
		{
			return null;
		}

		for (String filter : parseFilters(filters))
		{
			String normalizedFilter = normalize(filter);
			if (!normalizedFilter.isEmpty() && normalizedMessage.contains(normalizedFilter))
			{
				return filter;
			}
		}
		return null;
	}

	static List<String> parseFilters(String filters)
	{
		List<String> result = new ArrayList<>();
		if (filters == null || filters.trim().isEmpty())
		{
			return result;
		}

		String[] lines = filters.split("\\R");
		for (String line : lines)
		{
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#"))
			{
				continue;
			}
			if (wordCount(trimmed) >= MIN_WORDS)
			{
				result.add(trimmed);
			}
		}
		return result;
	}

	private static String normalize(String value)
	{
		if (value == null)
		{
			return "";
		}
		String lower = value.toLowerCase(Locale.ROOT);
		String wordsOnly = lower.replaceAll("[^a-z0-9]+", " ").trim();
		return wordsOnly.replaceAll("\\s+", " ");
	}

	private static int wordCount(String value)
	{
		String normalized = normalize(value);
		if (normalized.isEmpty())
		{
			return 0;
		}
		return normalized.split(" ").length;
	}
}
