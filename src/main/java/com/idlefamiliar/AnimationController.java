package com.idlefamiliar;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class AnimationController
{
	private static final int FRAME_SIZE = 64;
	/** Base time each frame is shown; the final frame of a loop is held 2x this. */
	private static final long FRAME_DURATION_MILLIS = 300;

	private static final String RESOURCE_DIR = "/com/idlefamiliar/avatar/default/";
	private static final String WEIGHTS_FILE = "weights.json";

	private final Map<AvatarState, AvatarAnimation> animations = new EnumMap<>(AvatarState.class);
	private final Map<String, AvatarAnimation> skillingAnimations = new HashMap<>();
	private final Set<String> missingSkillingAnimations = new HashSet<>();
	private final SpriteSheetLoader spriteSheetLoader = new SpriteSheetLoader();

	// --- Weighted variant system -------------------------------------------
	private final Random random;
	/** Lazily-built picker per asset name (e.g. "idle", "fishing_loop"). */
	private final Map<String, WeightedVariantPicker> variantPickers = new HashMap<>();
	/** Asset names for which we already attempted (and possibly failed) to build a picker. */
	private final Set<String> attemptedPickerKeys = new HashSet<>();
	/** Loaded variant animations keyed by variant file base (null = known-missing). */
	private final Map<String, AvatarAnimation> variantAnimations = new HashMap<>();
	/** Parsed weights.json, loaded once. */
	private Map<String, Integer> cachedWeights;
	/** The asset key currently playing — used to re-roll a variant only on state entry. */
	private String currentAnimationKey;
	/** The variant chosen for {@link #currentAnimationKey}; null if none/unavailable. */
	private String currentVariant;
	/** Wall-clock millis the current variant was chosen; anchors loop playback and re-rolling. */
	private long currentVariantAnchorMillis;

	/** Live playback speed multiplier (1.0 = normal). Driven by plugin config. */
	private volatile double playbackSpeed = 1.0;

	/**
	 * Asset keys for momentary, event-driven states that read best as a single
	 * complete play-through (a teleport flourish, a GE "ka-ching", crossing an
	 * agility obstacle) rather than a loop. When {@link #playFullCycleOneShots} is
	 * on, entering one of these latches the animation so it finishes its full cycle
	 * even if the underlying state has already moved on (e.g. agility, where the
	 * player is already walking again before the obstacle animation would end). The
	 * latch plays for the sheet's real duration, so it adapts automatically to a
	 * longer or shorter replacement animation.
	 */
	private static final Set<String> ONE_SHOT_KEYS = Collections.unmodifiableSet(
		new HashSet<>(java.util.Arrays.asList("teleporting", "grand_exchange", "agility_loop", "level_up")));

	/** When true, {@link #ONE_SHOT_KEYS} animations play one full cycle on entry. */
	private volatile boolean playFullCycleOneShots = true;
	/** The one-shot asset key currently latched (playing to completion), or null. */
	private String latchedOneShotKey;
	/** Variant chosen for the latched one-shot. */
	private String latchedOneShotVariant;
	/** Wall-clock millis the latched one-shot started. */
	private long latchedOneShotAnchorMillis;
	/** A one-shot key that just completed; blocks immediate re-latch while still in that state. */
	private String oneShotJustFinishedKey;

	/**
	 * Guards the animation/variant maps against a runtime reload racing a paint.
	 * {@link #getFrameAt} is called from both the overlay (client thread) and the
	 * desktop widget (Swing EDT), and {@link #reloadAnimations()} clears/rebuilds
	 * those same maps from the client thread when the user picks "Reload
	 * animations". Holding this lock across both keeps a reload from interleaving
	 * with a paint (the lookups are cheap, so the contention is negligible).
	 */
	private final Object frameLock = new Object();

	/**
	 * Optional external drop-in folder. When set, a PNG placed here that matches an
	 * asset file name (e.g. {@code walking_loop.png}) is loaded in preference to the
	 * bundled resource of the same name — so animations can be added or swapped at
	 * runtime without rebuilding the jar. {@code null} means "bundled assets only".
	 */
	private File externalAssetDir;

	public AnimationController()
	{
		this(new Random());
	}

	/** @param random injectable random source for deterministic variant tests. */
	public AnimationController(Random random)
	{
		this.random = random;
	}

	/**
	 * Adjust how fast every animation plays back. 1.0 is the default 300ms-per-frame
	 * cadence; 2.0 plays twice as fast, 0.5 half as fast. Values &le; 0 are ignored.
	 */
	public void setPlaybackSpeed(double playbackSpeed)
	{
		if (playbackSpeed > 0)
		{
			this.playbackSpeed = playbackSpeed;
		}
	}

	/**
	 * Enable/disable the "finish one-shot animations" behaviour for
	 * {@link #ONE_SHOT_KEYS}. When off, those states loop/cut like any other.
	 */
	public void setPlayFullCycleOneShots(boolean value)
	{
		this.playFullCycleOneShots = value;
	}

	/**
	 * Point the controller at an external drop-in folder for animation sheets.
	 * Call before {@link #loadDefaultAnimations()} (or call that again afterwards)
	 * so cached lookups are rebuilt against the new folder. Pass {@code null} to
	 * use bundled assets only.
	 */
	public void setExternalAssetDir(File externalAssetDir)
	{
		this.externalAssetDir = externalAssetDir;
	}

	public void loadDefaultAnimations()
	{
		animations.clear();
		skillingAnimations.clear();
		missingSkillingAnimations.clear();
		variantPickers.clear();
		attemptedPickerKeys.clear();
		variantAnimations.clear();
		cachedWeights = null;
		currentAnimationKey = null;
		currentVariant = null;
		currentVariantAnchorMillis = 0;
		latchedOneShotKey = null;
		latchedOneShotVariant = null;
		latchedOneShotAnchorMillis = 0;
		oneShotJustFinishedKey = null;
		load(AvatarState.DEFAULT, "idle");
		load(AvatarState.PLAYER_ACTIVE, "active");
		load(AvatarState.PLAYER_IDLE, "idle");
		load(AvatarState.AFK_WARNING, "afk_warning");
		load(AvatarState.COMBAT, "combat");
		load(AvatarState.SKILLING, "skilling");
		load(AvatarState.INVENTORY_FULL, "inventory_full");
		load(AvatarState.LOW_HEALTH, "low_health");
		load(AvatarState.LOW_PRAYER, "low_prayer");
		load(AvatarState.LOGGED_OUT, "logged_out");
		load(AvatarState.CUSTOM_EVENT, "active");
	}

	public BufferedImage getFrame(AvatarState state)
	{
		return getFrame(state, "");
	}

	public BufferedImage getFrame(AvatarState state, String activityLabel)
	{
		return getFrameAt(state, activityLabel, System.currentTimeMillis());
	}

	/** @return the variant name currently selected, or {@code null}. Visible for tests. */
	String currentVariantName()
	{
		return currentVariant;
	}

	/** @return the one-shot asset key currently latched (playing to completion), or {@code null}. Visible for tests. */
	String activeOneShotKey()
	{
		return latchedOneShotKey;
	}

	/**
	 * Render a frame using an explicit clock instead of {@link System#currentTimeMillis()}.
	 * Package-private so per-loop re-roll and playback anchoring can be tested deterministically.
	 */
	BufferedImage getFrameAt(AvatarState state, String activityLabel, long now)
	{
		synchronized (frameLock)
		{
			String assetName = resolveAssetName(state, activityLabel);

			// One-shot handling (teleport / GE flourish): play the full cycle once,
			// latched so it survives the underlying state changing before it ends.
			BufferedImage oneShotFrame = renderOneShot(assetName, now);
			if (oneShotFrame != null)
			{
				return oneShotFrame;
			}

			// Prefer a weighted variant; it re-rolls on state entry and on each loop
			// boundary. Fall back to the pre-loaded primary animation if none exists.
			AvatarAnimation animation = selectVariantAnimation(assetName, now);
			long playbackTime;
			if (animation != null)
			{
				// Anchor variant playback to its selection time, so each loop starts on
				// frame 0 and completes cleanly — which is what the per-loop re-roll keys off.
				playbackTime = now - currentVariantAnchorMillis;
			}
			else
			{
				animation = animations.getOrDefault(state, animations.get(AvatarState.DEFAULT));
				if (state == AvatarState.SKILLING)
				{
					AvatarAnimation skillingAnimation = getSkillingAnimation(activityLabel);
					if (skillingAnimation != null)
					{
						animation = skillingAnimation;
					}
				}
				playbackTime = now;
			}

			if (animation == null)
			{
				return createFallbackFrame();
			}

			BufferedImage frame = animation.getFrame(playbackTime, playbackSpeed);
			return frame == null ? createFallbackFrame() : frame;
		}
	}

	/**
	 * Re-read every animation sheet and {@code weights.json} from disk, picking up
	 * hand-edited variant weights and any newly dropped-in PNGs without toggling the
	 * plugin off and on. Safe to call at runtime: it holds {@link #frameLock} so it
	 * cannot clear the variant maps mid-paint. The next {@link #getFrame} re-rolls a
	 * fresh variant against the reloaded weights (the current key is reset).
	 */
	public void reloadAnimations()
	{
		synchronized (frameLock)
		{
			loadDefaultAnimations();
		}
	}

	/**
	 * Build a {@link WeightedVariantPicker} covering every sprite-sheet variant
	 * discovered for {@code assetName}. Variants follow the resolved primary file
	 * name with a suffix: e.g. for {@code idle} (primary {@code idle_loop.png})
	 * the candidates are {@code idle_loop}, {@code idle_loop_uncommon},
	 * {@code idle_loop_rare}, and {@code idle_loop_2}..{@code idle_loop_9}.
	 *
	 * <p>Weights come from {@code weights.json} when present, defaulting to 1
	 * (equal probability) for any variant not listed.
	 *
	 * @return a picker, or {@code null} if no primary resource exists for the key
	 */
	public WeightedVariantPicker buildVariantPicker(String assetName)
	{
		String primaryBase = resolvePrimaryBase(assetName);
		if (primaryBase == null)
		{
			return null;
		}

		List<String> variants = discoverVariants(primaryBase);
		Map<String, Integer> weights = loadWeightsJson();

		Map<String, Integer> resolvedWeights = new LinkedHashMap<>();
		for (String variant : variants)
		{
			resolvedWeights.put(variant, weights.getOrDefault(variant, 1));
		}
		return new WeightedVariantPicker(resolvedWeights, random);
	}

	/**
	 * Drive the one-shot latch. Returns a frame when a one-shot animation should be
	 * shown — either continuing a latched one to the end of its single cycle, or
	 * starting a freshly-entered one — or {@code null} to fall through to normal
	 * looping playback. Must be called inside {@link #frameLock}.
	 */
	private BufferedImage renderOneShot(String assetName, long now)
	{
		// Forget the "just finished" guard once we have left that one-shot state.
		if (oneShotJustFinishedKey != null && !oneShotJustFinishedKey.equals(assetName))
		{
			oneShotJustFinishedKey = null;
		}

		double speed = playbackSpeed > 0 ? playbackSpeed : 1.0;

		// (A) Continue a latched one-shot to the end of its single cycle, overriding
		// whatever the current state now is.
		if (latchedOneShotKey != null)
		{
			AvatarAnimation oneShot = loadVariantCached(latchedOneShotVariant);
			long elapsed = (long) ((now - latchedOneShotAnchorMillis) * speed);
			if (oneShot != null && oneShot.frameCount() > 1 && elapsed < oneShot.getTotalDurationMillis())
			{
				BufferedImage frame = oneShot.getFrame(now - latchedOneShotAnchorMillis, playbackSpeed);
				return frame == null ? createFallbackFrame() : frame;
			}
			// Finished (or sheet unavailable): release and let normal selection resume.
			oneShotJustFinishedKey = latchedOneShotKey;
			latchedOneShotKey = null;
			latchedOneShotVariant = null;
			currentAnimationKey = null;
		}

		// (B) Entering a one-shot state arms a fresh latch — unless we just finished
		// it and are still sitting in that same state (avoids an immediate replay).
		if (playFullCycleOneShots
			&& ONE_SHOT_KEYS.contains(assetName)
			&& !assetName.equals(oneShotJustFinishedKey)
			&& !assetName.equals(currentAnimationKey))
		{
			AvatarAnimation picked = selectVariantAnimation(assetName, now);
			if (picked != null && picked.frameCount() > 1)
			{
				latchedOneShotKey = assetName;
				latchedOneShotVariant = currentVariant;
				latchedOneShotAnchorMillis = currentVariantAnchorMillis;
				BufferedImage frame = picked.getFrame(now - latchedOneShotAnchorMillis, playbackSpeed);
				return frame == null ? createFallbackFrame() : frame;
			}
		}

		return null;
	}

	private AvatarAnimation selectVariantAnimation(String assetName, long now)
	{
		if (assetName == null)
		{
			return null;
		}

		if (!assetName.equals(currentAnimationKey))
		{
			// State entry: (re)build the picker and roll a fresh variant, anchoring
			// playback to now.
			WeightedVariantPicker picker = variantPickers.get(assetName);
			if (picker == null && !attemptedPickerKeys.contains(assetName))
			{
				attemptedPickerKeys.add(assetName);
				picker = buildVariantPicker(assetName);
				if (picker != null)
				{
					variantPickers.put(assetName, picker);
				}
			}
			currentAnimationKey = assetName;
			currentVariant = picker != null ? picker.pick() : null;
			currentVariantAnchorMillis = now;
		}
		else if (currentVariant != null)
		{
			// Same state still showing: re-roll once the current variant's loop has
			// fully played, so multi-sheet states vary every cycle, not only on entry.
			rerollVariantIfLoopElapsed(assetName, now);
		}

		return currentVariant == null ? null : loadVariantCached(currentVariant);
	}

	/**
	 * Re-roll the active variant (via the same weighted picker) when the current
	 * variant's loop has finished, and re-anchor playback so the next variant starts
	 * on frame 0. Static single-frame poses have no loop boundary and are left alone.
	 */
	private void rerollVariantIfLoopElapsed(String assetName, long now)
	{
		WeightedVariantPicker picker = variantPickers.get(assetName);
		if (picker == null)
		{
			return;
		}

		AvatarAnimation current = loadVariantCached(currentVariant);
		if (current == null || current.frameCount() <= 1)
		{
			return;
		}

		double speed = playbackSpeed > 0 ? playbackSpeed : 1.0;
		long elapsed = (long) ((now - currentVariantAnchorMillis) * speed);
		if (elapsed >= current.getTotalDurationMillis())
		{
			currentVariant = picker.pick();
			currentVariantAnchorMillis = now;
		}
	}

	private AvatarAnimation loadVariantCached(String variantBase)
	{
		if (variantAnimations.containsKey(variantBase))
		{
			return variantAnimations.get(variantBase);
		}
		try
		{
			AvatarAnimation animation = loadSheet(variantBase + ".png");
			variantAnimations.put(variantBase, animation);
			return animation;
		}
		catch (IOException ex)
		{
			// Cache a null ONLY when the sheet is genuinely absent, so a missing variant
			// is not re-attempted every frame. A sheet that EXISTS but failed to decode
			// (a transient read hiccup on the asset folder, or a momentarily-locked file)
			// is left uncached, so the next frame retries and recovers the real animation
			// rather than being stuck on the idle fallback for the rest of the session.
			if (!assetExists(variantBase + ".png"))
			{
				variantAnimations.put(variantBase, null);
			}
			return null;
		}
	}

	String resolveAssetName(AvatarState state, String activityLabel)
	{
		if (state == AvatarState.SKILLING)
		{
			if (activityLabel != null && !activityLabel.isEmpty() && !"Skilling".equals(activityLabel))
			{
				return activityLabel.toLowerCase().replace(' ', '_') + "_loop";
			}
			return "skilling";
		}

		// Combat splits into style sub-sheets (combat_melee / combat_ranged /
		// combat_magic) when the plugin supplies a style label; the renderer falls
		// back to the generic "combat" sheet when the sub-sheet is absent.
		if (state == AvatarState.COMBAT)
		{
			if (activityLabel != null && !activityLabel.isEmpty())
			{
				return "combat_" + activityLabel.toLowerCase().replace(' ', '_');
			}
			return "combat";
		}

		switch (state)
		{
			case PLAYER_ACTIVE:
			case CUSTOM_EVENT:
				return "active";
			case AFK_WARNING:
				return "afk_warning";
			case DEATH:
				return "death";
			case LEVEL_UP:
				return "level_up";
			case BANKING:
				return "banking";
			case INVENTORY_FULL:
				return "inventory_full";
			case GRAND_EXCHANGE:
				return "grand_exchange";
			case TELEPORTING:
				return "teleporting";
			case WALKING:
				return "walking";
			case RUNNING:
				return "running";
			case LOW_HEALTH:
				return "low_health";
			case LOW_PRAYER:
				return "low_prayer";
			case LOGGED_OUT:
				return "logged_out";
			case PLAYER_IDLE:
			case DEFAULT:
			default:
				return "idle";
		}
	}

	/** Resolve the primary on-disk file base for an asset name (without ".png"). */
	private String resolvePrimaryBase(String assetName)
	{
		for (String suffix : new String[]{"_loop", ""})
		{
			String candidate = assetName + suffix;
			if (assetExists(candidate + ".png"))
			{
				return candidate;
			}
		}
		return null;
	}

	private List<String> discoverVariants(String primaryBase)
	{
		List<String> found = new ArrayList<>();
		found.add(primaryBase); // the primary sheet is always a variant
		boolean primaryIsExternal = externalAssetExists(primaryBase + ".png");
		for (String suffix : new String[]{"uncommon", "rare"})
		{
			String candidate = primaryBase + "_" + suffix;
			if (variantExists(candidate + ".png", primaryIsExternal))
			{
				found.add(candidate);
			}
		}
		for (int i = 2; i <= 9; i++)
		{
			String candidate = primaryBase + "_" + i;
			if (variantExists(candidate + ".png", primaryIsExternal))
			{
				found.add(candidate);
			}
		}
		return found;
	}

	private boolean variantExists(String fileName, boolean primaryIsExternal)
	{
		return primaryIsExternal ? externalAssetExists(fileName) : assetExists(fileName);
	}

	/**
	 * @return {@code true} if {@code fileName} can be loaded from the external
	 *         drop-in folder (preferred) or the bundled resources
	 */
	private boolean assetExists(String fileName)
	{
		if (externalAssetExists(fileName))
		{
			return true;
		}
		return AnimationController.class.getResource(RESOURCE_DIR + fileName) != null;
	}

	private boolean externalAssetExists(String fileName)
	{
		return externalAssetDir != null && new File(externalAssetDir, fileName).isFile();
	}

	/**
	 * Open {@code fileName} from the external drop-in folder when present, else
	 * from the bundled resources. Returns {@code null} if neither has it.
	 */
	private InputStream openAsset(String fileName) throws IOException
	{
		if (externalAssetDir != null)
		{
			File external = new File(externalAssetDir, fileName);
			if (external.isFile())
			{
				return new FileInputStream(external);
			}
		}
		return AnimationController.class.getResourceAsStream(RESOURCE_DIR + fileName);
	}

	/** Load and slice a single sheet file (external-folder aware). */
	private AvatarAnimation loadSheet(String fileName) throws IOException
	{
		return spriteSheetLoader.load(openAsset(fileName), fileName, FRAME_DURATION_MILLIS);
	}

	private Map<String, Integer> loadWeightsJson()
	{
		if (cachedWeights != null)
		{
			return cachedWeights;
		}

		// Start from the bundled defaults, then overlay the external drop-in file so a
		// user-supplied weights.json overrides bundled entries PER KEY rather than
		// replacing the whole file. Dropping in a weights.json that only tunes "idle"
		// no longer silently wipes every other bundled weight.
		Map<String, Integer> merged = new LinkedHashMap<>();
		try (InputStream bundled = AnimationController.class.getResourceAsStream(RESOURCE_DIR + WEIGHTS_FILE))
		{
			merged.putAll(parseWeights(bundled));
		}
		catch (IOException ignored)
		{
			// Bundled file missing/unreadable — fall through to whatever the external has.
		}

		if (externalAssetDir != null)
		{
			File external = new File(externalAssetDir, WEIGHTS_FILE);
			if (external.isFile())
			{
				try (InputStream stream = new FileInputStream(external))
				{
					merged.putAll(parseWeights(stream));
				}
				catch (IOException ignored)
				{
					// External file unreadable — keep the bundled defaults.
				}
			}
		}

		cachedWeights = merged;
		return cachedWeights;
	}

	/**
	 * Minimal hand-rolled parse of a {@code {"key": weight, ...}} object into a map.
	 * No JSON library is guaranteed in the plugin runtime, so parse by hand. A
	 * {@code null} stream or malformed entries yield an empty map / skipped keys.
	 */
	private static Map<String, Integer> parseWeights(InputStream stream) throws IOException
	{
		if (stream == null)
		{
			return Collections.emptyMap();
		}
		String raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replaceAll("[{}\"\\s]", "");
		Map<String, Integer> result = new LinkedHashMap<>();
		for (String pair : raw.split(","))
		{
			if (pair.isEmpty())
			{
				continue;
			}
			String[] kv = pair.split(":");
			if (kv.length == 2)
			{
				try
				{
					result.put(kv[0], Integer.parseInt(kv[1].trim()));
				}
				catch (NumberFormatException ignored)
				{
					// Skip malformed entries.
				}
			}
		}
		return result;
	}

	private void load(AvatarState state, String assetName)
	{
		try
		{
			animations.put(state, loadAsset(assetName));
		}
		catch (IOException ex)
		{
			// No usable sheet for this state — it is genuinely absent (e.g. no bundled
			// combat/low_health sheet) or a transient I/O failure on the asset folder.
			// Leave the state UNMAPPED so getFrameAt falls back to the real idle
			// animation instead of storing a generated dev-placeholder here. The map is
			// cleared and rebuilt on every loadDefaultAnimations()/reloadAnimations(),
			// so a sheet that reappears is picked up on the next reload.
			animations.remove(state);
		}
	}

	private AvatarAnimation getSkillingAnimation(String activityLabel)
	{
		if (activityLabel == null || activityLabel.isEmpty() || "Skilling".equals(activityLabel))
		{
			return null;
		}

		String skillName = activityLabel.toLowerCase().replace(' ', '_');
		String assetName = skillName + "_loop";
		if (skillingAnimations.containsKey(assetName))
		{
			return skillingAnimations.get(assetName);
		}

		if (missingSkillingAnimations.contains(assetName))
		{
			return null;
		}

		try
		{
			AvatarAnimation animation = loadAsset(assetName);
			skillingAnimations.put(assetName, animation);
			return animation;
		}
		catch (IOException ex)
		{
			String fallbackAssetName = "skilling_" + skillName;
			try
			{
				AvatarAnimation animation = loadAsset(fallbackAssetName);
				skillingAnimations.put(assetName, animation);
				return animation;
			}
			catch (IOException fallbackEx)
			{
				missingSkillingAnimations.add(assetName);
				return null;
			}
		}
	}

	private AvatarAnimation loadAsset(String assetName) throws IOException
	{
		try
		{
			return loadSheet(assetName + "_loop.png");
		}
		catch (IOException ex)
		{
			return loadSheet(assetName + ".png");
		}
	}

	/**
	 * The absolute last-resort frame, returned only when no real sheet — not even the
	 * idle fallback — is available. It is intentionally a fully transparent frame: a
	 * missing or (transiently) unreadable sheet must NEVER surface a generated
	 * placeholder "dev image" to the user. The normal path for a failed sheet is to
	 * fall back to the real idle animation (see {@link #load} and {@link #getFrameAt});
	 * this blank frame only covers the case where even that is gone, where showing
	 * nothing is preferable to programmer-art.
	 */
	BufferedImage createFallbackFrame()
	{
		// A fresh TYPE_INT_ARGB image is all zeroes — every pixel fully transparent.
		return new BufferedImage(FRAME_SIZE, FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);
	}
}
