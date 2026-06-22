package com.idlefamiliar;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Validates a folder of avatar sprite sheets (and its {@code weights.json}),
 * reporting the three mistakes that are easy to make while authoring animations:
 *
 * <ul>
 *   <li><b>Malformed sheet</b> - width is not a whole multiple of height, so the
 *       loader cannot slice it into square frames.</li>
 *   <li><b>Orphaned variant</b> - a variant such as {@code idle_loop_rare.png} or
 *       {@code idle_loop_2.png} with no {@code idle_loop.png} primary, so the
 *       variant is never discovered.</li>
 *   <li><b>Dangling weight</b> - a {@code weights.json} key pointing at a sheet
 *       that is not in the folder.</li>
 * </ul>
 *
 * <p>Pure and dependency-light (java.awt + ImageIO only) so it can be unit tested
 * and run against the external drop-in folder without RuneLite. Targets the
 * external folder specifically - that is where authoring happens and where these
 * mistakes occur.
 */
public final class AvatarAssetValidator
{
	/** A variant file base ends in one of these once its primary base is stripped. */
	private static final Pattern VARIANT_SUFFIX = Pattern.compile("^(.*)_(uncommon|rare|[2-9])$");

	private AvatarAssetValidator()
	{
	}

	/**
	 * @param dir the avatar folder to validate (typically the external drop-in dir)
	 * @return a list of human-readable issues; empty means the folder is clean (or
	 *         absent - there is nothing to validate)
	 */
	public static List<String> validate(File dir)
	{
		List<String> issues = new ArrayList<>();
		if (dir == null || !dir.isDirectory())
		{
			return issues;
		}

		File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
		if (files == null)
		{
			return issues;
		}

		Set<String> bases = new LinkedHashSet<>();
		for (File file : files)
		{
			bases.add(stripPng(file.getName()));
		}

		for (File file : files)
		{
			checkDimensions(file, issues);
		}

		for (String base : bases)
		{
			Matcher matcher = VARIANT_SUFFIX.matcher(base);
			if (matcher.matches() && !bases.contains(matcher.group(1)))
			{
				issues.add("Orphaned variant: " + base + ".png has no primary " + matcher.group(1) + ".png");
			}
		}

		checkWeights(new File(dir, "weights.json"), bases, issues);
		return issues;
	}

	private static void checkDimensions(File file, List<String> issues)
	{
		try
		{
			BufferedImage image = ImageIO.read(file);
			if (image == null)
			{
				issues.add("Unreadable sheet: " + file.getName() + " is not a valid image");
				return;
			}
			int width = image.getWidth();
			int height = image.getHeight();
			if (height <= 0 || width % height != 0)
			{
				issues.add("Malformed sheet: " + file.getName() + " is " + width + "x" + height
					+ " (width must be a whole multiple of height; frames = width / height)");
			}
		}
		catch (IOException ex)
		{
			issues.add("Unreadable sheet: " + file.getName() + " (" + ex.getMessage() + ")");
		}
	}

	private static void checkWeights(File weightsFile, Set<String> bases, List<String> issues)
	{
		if (!weightsFile.isFile())
		{
			return;
		}
		String raw;
		try
		{
			raw = new String(Files.readAllBytes(weightsFile.toPath()), StandardCharsets.UTF_8);
		}
		catch (IOException ex)
		{
			issues.add("weights.json: unreadable (" + ex.getMessage() + ")");
			return;
		}
		String stripped = raw.replaceAll("[{}\"\\s]", "");
		for (String pair : stripped.split(","))
		{
			if (pair.isEmpty())
			{
				continue;
			}
			String key = pair.split(":")[0];
			if (!key.isEmpty() && !bases.contains(key))
			{
				issues.add("Dangling weight: weights.json key '" + key + "' has no " + key + ".png");
			}
		}
	}

	private static String stripPng(String fileName)
	{
		int dot = fileName.toLowerCase(Locale.ROOT).lastIndexOf(".png");
		return dot >= 0 ? fileName.substring(0, dot) : fileName;
	}
}
