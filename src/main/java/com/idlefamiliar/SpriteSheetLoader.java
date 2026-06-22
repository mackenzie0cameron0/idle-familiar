package com.idlefamiliar;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class SpriteSheetLoader
{
	/**
	 * Slice an already-open sprite-sheet stream into a looping animation. Lets the
	 * caller decide where the bytes come from (bundled classpath resource or an
	 * external drop-in file), so the same slicing logic serves both sources.
	 *
	 * @param stream     the PNG bytes; closed by this method
	 * @param sourceName a human-readable name used only in error messages
	 */
	public AvatarAnimation load(InputStream stream, String sourceName, long frameDurationMillis) throws IOException
	{
		try (InputStream managed = stream)
		{
			if (managed == null)
			{
				throw new IOException("Sprite resource not found: " + sourceName);
			}

			BufferedImage sheet = ImageIO.read(managed);
			if (sheet == null)
			{
				throw new IOException("Sprite resource could not be read: " + sourceName);
			}

			return slice(sheet, sheet.getHeight(), sheet.getHeight(), frameDurationMillis);
		}
	}

	private AvatarAnimation slice(BufferedImage sheet, int frameWidth, int frameHeight, long frameDurationMillis) throws IOException
	{
		if (frameWidth <= 0 || frameHeight <= 0 || sheet.getWidth() < frameWidth || sheet.getHeight() < frameHeight)
		{
			throw new IOException("Invalid sprite sheet dimensions");
		}

		List<BufferedImage> frames = new ArrayList<>();
		int columns = sheet.getWidth() / frameWidth;
		int rows = sheet.getHeight() / frameHeight;
		for (int y = 0; y < rows; y++)
		{
			for (int x = 0; x < columns; x++)
			{
				frames.add(sheet.getSubimage(x * frameWidth, y * frameHeight, frameWidth, frameHeight));
			}
		}

		if (frames.isEmpty())
		{
			throw new IOException("Sprite sheet contained no frames");
		}

		return new AvatarAnimation(frames, frameDurationMillis, true);
	}
}
