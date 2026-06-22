package com.idlefamiliar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.Test;

/**
 * Verifies the "finish one-shot animations" behaviour: a momentary state
 * (teleport) plays its full cycle once, latched against the underlying state
 * changing before it completes. Uses a synthetic multi-frame sheet in an external
 * drop-in folder so the test is deterministic and independent of shipped art.
 */
public class AnimationControllerOneShotTest
{
	/** Write a synthetic 4-frame (256x64) teleporting sheet into a temp drop-in dir. */
	private File externalDirWithTeleportSheet() throws IOException
	{
		return externalDirWithSheets("teleporting.png");
	}

	/** Write synthetic 4-frame (256x64) sheets with the given names into a temp drop-in dir. */
	private File externalDirWithSheets(String... fileNames) throws IOException
	{
		File dir = Files.createTempDirectory("if-oneshot").toFile();
		dir.deleteOnExit();
		BufferedImage sheet = new BufferedImage(256, 64, BufferedImage.TYPE_INT_ARGB);
		for (String fileName : fileNames)
		{
			ImageIO.write(sheet, "png", new File(dir, fileName));
		}
		return dir;
	}

	@Test
	public void oneShotPlaysFullCycleEvenAfterStateChanges() throws IOException
	{
		AnimationController controller = new AnimationController(new Random(1));
		controller.setExternalAssetDir(externalDirWithTeleportSheet());
		controller.loadDefaultAnimations();
		controller.setPlayFullCycleOneShots(true);

		long t = 1_000_000L;

		// Entering TELEPORTING arms the one-shot latch.
		assertNotNull(controller.getFrameAt(AvatarState.TELEPORTING, "", t));
		assertEquals("teleporting", controller.activeOneShotKey());

		// The state immediately drops back to idle, but the one-shot keeps playing
		// (a 4-frame sheet runs ~1.5s, so 100ms in it is still mid-cycle).
		controller.getFrameAt(AvatarState.PLAYER_IDLE, "", t + 100);
		assertEquals("teleporting", controller.activeOneShotKey());

		// Well past the cycle length, the latch releases and idle resumes.
		controller.getFrameAt(AvatarState.PLAYER_IDLE, "", t + 100_000);
		assertNull(controller.activeOneShotKey());
	}

	@Test
	public void oneShotDisabledNeverLatches() throws IOException
	{
		AnimationController controller = new AnimationController(new Random(1));
		controller.setExternalAssetDir(externalDirWithTeleportSheet());
		controller.loadDefaultAnimations();
		controller.setPlayFullCycleOneShots(false);

		controller.getFrameAt(AvatarState.TELEPORTING, "", 2_000_000L);
		assertNull(controller.activeOneShotKey());
	}

	@Test
	public void nonOneShotStatesNeverLatch() throws IOException
	{
		AnimationController controller = new AnimationController(new Random(1));
		controller.setExternalAssetDir(externalDirWithTeleportSheet());
		controller.loadDefaultAnimations();
		controller.setPlayFullCycleOneShots(true);

		controller.getFrameAt(AvatarState.PLAYER_IDLE, "", 3_000_000L);
		assertNull(controller.activeOneShotKey());
		controller.getFrameAt(AvatarState.SKILLING, "Fishing", 3_000_100L);
		assertNull(controller.activeOneShotKey());
	}

	@Test
	public void agilityObstacleLatchesAsAOneShot() throws IOException
	{
		AnimationController controller = new AnimationController(new Random(1));
		controller.setExternalAssetDir(externalDirWithSheets("agility_loop.png"));
		controller.loadDefaultAnimations();
		controller.setPlayFullCycleOneShots(true);

		long t = 1_000_000L;

		// Agility is XP-detected and surfaces as SKILLING "Agility" -> asset key
		// agility_loop, a one-shot. It must latch so the obstacle plays its full cycle
		// even though the player is already walking again before it would end.
		assertNotNull(controller.getFrameAt(AvatarState.SKILLING, "Agility", t));
		assertEquals("agility_loop", controller.activeOneShotKey());

		controller.getFrameAt(AvatarState.WALKING, "", t + 100);
		assertEquals("agility_loop", controller.activeOneShotKey());

		controller.getFrameAt(AvatarState.WALKING, "", t + 100_000);
		assertNull(controller.activeOneShotKey());
	}

	@Test
	public void grandExchangeAndLevelUpLatchAsOneShots() throws IOException
	{
		AnimationController controller = new AnimationController(new Random(1));
		controller.setExternalAssetDir(externalDirWithSheets("grand_exchange.png", "level_up.png"));
		controller.loadDefaultAnimations();
		controller.setPlayFullCycleOneShots(true);

		// A filled GE offer latches its one-shot.
		assertNotNull(controller.getFrameAt(AvatarState.GRAND_EXCHANGE, "", 5_000_000L));
		assertEquals("grand_exchange", controller.activeOneShotKey());

		// Let it finish, then a real level-up latches its own one-shot.
		controller.getFrameAt(AvatarState.PLAYER_IDLE, "", 5_100_000L);
		assertNull(controller.activeOneShotKey());
		assertNotNull(controller.getFrameAt(AvatarState.LEVEL_UP, "", 5_200_000L));
		assertEquals("level_up", controller.activeOneShotKey());
	}
}
