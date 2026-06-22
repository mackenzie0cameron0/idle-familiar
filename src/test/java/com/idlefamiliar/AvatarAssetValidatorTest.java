package com.idlefamiliar;

import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.Before;
import org.junit.Test;

public class AvatarAssetValidatorTest
{
	private File dir;

	@Before
	public void setUp() throws Exception
	{
		dir = Files.createTempDirectory("if-validate").toFile();
	}

	private void sheet(String name, int width, int height) throws Exception
	{
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		ImageIO.write(image, "png", new File(dir, name));
	}

	private void weights(String json) throws Exception
	{
		Files.write(new File(dir, "weights.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
	}

	private static boolean hasIssue(List<String> issues, String fragment)
	{
		return issues.stream().anyMatch(s -> s.contains(fragment));
	}

	@Test
	public void cleanFolderHasNoIssues() throws Exception
	{
		sheet("idle_loop.png", 256, 64);
		weights("{\"idle_loop\": 1}");
		assertTrue(AvatarAssetValidator.validate(dir).isEmpty());
	}

	@Test
	public void flagsMalformedSheet() throws Exception
	{
		sheet("idle_loop.png", 256, 64);
		sheet("broken_loop.png", 100, 64);
		assertTrue(hasIssue(AvatarAssetValidator.validate(dir), "Malformed sheet: broken_loop.png"));
	}

	@Test
	public void flagsOrphanedVariant() throws Exception
	{
		sheet("orphan_loop_2.png", 256, 64);
		assertTrue(hasIssue(AvatarAssetValidator.validate(dir), "Orphaned variant: orphan_loop_2.png"));
	}

	@Test
	public void primaryWithValidVariantIsClean() throws Exception
	{
		sheet("idle_loop.png", 256, 64);
		sheet("idle_loop_rare.png", 256, 64);
		assertTrue(AvatarAssetValidator.validate(dir).isEmpty());
	}

	@Test
	public void flagsDanglingWeightKey() throws Exception
	{
		sheet("idle_loop.png", 256, 64);
		weights("{\"idle_loop\": 70, \"missing_loop\": 5}");
		assertTrue(hasIssue(AvatarAssetValidator.validate(dir), "Dangling weight: weights.json key 'missing_loop'"));
	}

	@Test
	public void nullAndMissingDirsAreEmpty()
	{
		assertTrue(AvatarAssetValidator.validate(null).isEmpty());
		assertTrue(AvatarAssetValidator.validate(new File(dir, "does-not-exist")).isEmpty());
	}
}
