package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that weighted variants re-roll on each loop boundary (not only on
 * state entry) and that a variant stays fixed for the duration of a single loop.
 */
public class AnimationControllerRerollTest
{
	private File dir;

	@Before
	public void setUp() throws Exception
	{
		dir = Files.createTempDirectory("if-reroll").toFile();
		// Two multi-frame "active" variants (128x64 -> 2 frames each), distinct colours.
		writeSheet("active_loop.png", Color.RED);
		writeSheet("active_loop_2.png", Color.BLUE);
		// Equal odds so both surface quickly.
		Files.write(new File(dir, "weights.json").toPath(),
			"{\"active_loop\":1,\"active_loop_2\":1}".getBytes());
	}

	private void writeSheet(String name, Color color) throws Exception
	{
		BufferedImage sheet = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(color);
		g.fillRect(0, 0, 128, 64);
		g.dispose();
		ImageIO.write(sheet, "png", new File(dir, name));
	}

	private AnimationController controller(long seed)
	{
		AnimationController controller = new AnimationController(new Random(seed));
		controller.setExternalAssetDir(dir);
		controller.loadDefaultAnimations();
		return controller;
	}

	@Test
	public void variantStaysFixedWithinASingleLoop()
	{
		AnimationController controller = controller(1);

		// Two 64-base frames: 300ms + 600ms (last frame doubled) = 900ms loop.
		controller.getFrameAt(AvatarState.PLAYER_ACTIVE, "", 1000L);
		String first = controller.currentVariantName();
		controller.getFrameAt(AvatarState.PLAYER_ACTIVE, "", 1300L); // still < 900ms in
		controller.getFrameAt(AvatarState.PLAYER_ACTIVE, "", 1800L);
		assertEquals("variant must not re-roll mid-loop", first, controller.currentVariantName());
	}

	@Test
	public void variantRerollsAcrossLoopBoundaries()
	{
		AnimationController controller = controller(1);

		Set<String> seen = new HashSet<>();
		long now = 1000L;
		for (int i = 0; i < 100; i++)
		{
			controller.getFrameAt(AvatarState.PLAYER_ACTIVE, "", now);
			seen.add(controller.currentVariantName());
			now += 2000L; // > one 900ms loop, so a re-roll is eligible every step
		}

		assertTrue("expected both variants to appear across loops, saw " + seen, seen.size() >= 2);
	}

	@Test
	public void switchingStateReAnchorsAndRendersOneOfItsVariants()
	{
		AnimationController controller = controller(7);

		// active -> logged_out -> active exercises state-entry re-anchoring.
		controller.getFrameAt(AvatarState.PLAYER_ACTIVE, "", 1000L);
		controller.getFrameAt(AvatarState.LOGGED_OUT, "", 1100L);
		BufferedImage frame = controller.getFrameAt(AvatarState.PLAYER_ACTIVE, "", 1200L);

		int rgb = frame.getRGB(0, 0);
		assertTrue("active frame must be one of its two variants",
			rgb == Color.RED.getRGB() || rgb == Color.BLUE.getRGB());
		String variant = controller.currentVariantName();
		assertTrue("variant must be one of the active sheets",
			"active_loop".equals(variant) || "active_loop_2".equals(variant));
	}
}
