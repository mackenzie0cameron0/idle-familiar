package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;
import org.junit.Test;

public class AnimationControllerTest
{
	@Test
	public void mapsNewLocomotionAndEventStatesToAssetKeys()
	{
		AnimationController controller = new AnimationController();

		assertEquals("walking", controller.resolveAssetName(AvatarState.WALKING, ""));
		assertEquals("running", controller.resolveAssetName(AvatarState.RUNNING, ""));
		assertEquals("teleporting", controller.resolveAssetName(AvatarState.TELEPORTING, ""));
		assertEquals("grand_exchange", controller.resolveAssetName(AvatarState.GRAND_EXCHANGE, ""));
	}

	@Test
	public void newStatesWithoutSheetsFallBackToIdle()
	{
		AnimationController controller = new AnimationController();
		controller.loadDefaultAnimations();

		// No walking sheet is bundled, so the frame must still render (idle fallback).
		assertNotNull(controller.getFrame(AvatarState.WALKING));
	}

	@Test
	public void externalFolderSuppliesAnimationsWithoutRebuild() throws Exception
	{
		File dir = Files.createTempDirectory("if-avatar").toFile();
		// A 64x64 all-red sheet slices into a single 64x64 frame.
		BufferedImage sheet = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = sheet.createGraphics();
		graphics.setColor(Color.RED);
		graphics.fillRect(0, 0, 64, 64);
		graphics.dispose();
		ImageIO.write(sheet, "png", new File(dir, "walking_loop.png"));

		AnimationController controller = new AnimationController();
		controller.setExternalAssetDir(dir);
		controller.loadDefaultAnimations();

		// The external walking sheet must win over the idle fallback.
		BufferedImage frame = controller.getFrame(AvatarState.WALKING, "");
		assertEquals(Color.RED.getRGB(), frame.getRGB(0, 0));
	}

	@Test
	public void externalFolderCanOverrideABundledState() throws Exception
	{
		File dir = Files.createTempDirectory("if-avatar-override").toFile();
		BufferedImage sheet = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = sheet.createGraphics();
		graphics.setColor(Color.GREEN);
		graphics.fillRect(0, 0, 64, 64);
		graphics.dispose();
		// "combat" is bundled (combat_loop.png); the external file must take priority.
		ImageIO.write(sheet, "png", new File(dir, "combat_loop.png"));

		AnimationController controller = new AnimationController();
		controller.setExternalAssetDir(dir);
		controller.loadDefaultAnimations();

		assertEquals(Color.GREEN.getRGB(), controller.getFrame(AvatarState.COMBAT, "").getRGB(0, 0));
	}

	@Test
	public void fallbackFrameIsBlankNotADevPlaceholder()
	{
		AnimationController controller = new AnimationController();

		// The absolute last-resort frame must render nothing visible — never the old
		// smiley "dev image" placeholder. A missing or unreadable sheet falls back to
		// the real idle animation; only if even that is gone do we reach this frame,
		// and it must be fully transparent so programmer-art is never shown.
		BufferedImage frame = controller.createFallbackFrame();
		assertNotNull(frame);
		for (int y = 0; y < frame.getHeight(); y++)
		{
			for (int x = 0; x < frame.getWidth(); x++)
			{
				int alpha = (frame.getRGB(x, y) >>> 24) & 0xff;
				assertEquals("pixel (" + x + "," + y + ") must be transparent", 0, alpha);
			}
		}
	}

	@Test
	public void loadsDefaultAnimationFrame()
	{
		AnimationController controller = new AnimationController();
		controller.loadDefaultAnimations();

		assertNotNull(controller.getFrame(AvatarState.PLAYER_ACTIVE));
	}

	@Test
	public void loadsModernLoopAnimationFrame()
	{
		AnimationController controller = new AnimationController();
		controller.loadDefaultAnimations();

		assertNotNull(controller.getFrame(AvatarState.PLAYER_IDLE));
	}

	@Test
	public void loadsCookingLoopAnimationFrame()
	{
		AnimationController controller = new AnimationController();
		controller.loadDefaultAnimations();

		assertNotNull(controller.getFrame(AvatarState.SKILLING, "Cooking"));
	}

	@Test
	public void loadsWoodcuttingStartFallbackAnimationFrame()
	{
		AnimationController controller = new AnimationController();
		controller.loadDefaultAnimations();

		assertNotNull(controller.getFrame(AvatarState.SKILLING, "Woodcutting"));
	}
}
