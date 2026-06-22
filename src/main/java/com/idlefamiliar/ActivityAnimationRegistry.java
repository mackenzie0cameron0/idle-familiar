package com.idlefamiliar;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.gameval.AnimationID;

/**
 * Maps an in-game animation ID to the skilling activity it represents, replacing
 * the old "any non-idle animation counts as skilling" heuristic that produced
 * false positives (e.g. the fishing loop replaying while walking to a bank).
 *
 * <h2>How the whitelist is built</h2>
 * The registry is built once at startup by reflecting over
 * {@link net.runelite.api.gameval.AnimationID} and matching the named animation
 * constants by name prefix. This intentionally diverges from the original design
 * sketch, which assumed a clean {@code HUMAN_<SKILL>_*} namespace for every
 * skill. In the real gameval data ({@code RuneLite 1.12.x}) animation constants
 * are named by tool/action, not by skill: only a subset of skills expose a clean,
 * unambiguous {@code HUMAN_*} family that can be whitelisted without dragging in
 * unrelated animations.
 *
 * <p>Those clean families are whitelisted here (see {@link #SKILL_PREFIXES}).
 * Reflecting over the family — rather than hard-coding individual IDs — means
 * every tool tier and special variant (bronze → crystal/infernal/3a, wall and
 * no-reach-forward poses, Zalcano/Gauntlet skins, …) is covered automatically,
 * and newly added variants are picked up for free on a RuneLite bump.
 *
 * <p>Combat (Attack/Strength/Defence/Slayer), Ranged, Magic, Hunter, Firemaking,
 * Construction, Agility and other skills have no clean animation namespace, so
 * they are deliberately <em>not</em> driven by this registry. Their skilling
 * label continues to come from the authoritative XP-drop signal
 * ({@code onStatChanged}); see {@link IdleFamiliarPlugin}. An animation absent
 * from every set returns {@link Optional#empty()} — no signal, no linger refresh —
 * so emotes, eating, drinking and teleport wind-ups are silently ignored.
 */
public class ActivityAnimationRegistry
{
	/**
	 * Animation-constant name prefix &rarr; activity label, in match priority
	 * order (first matching prefix wins). Every prefix below is start-anchored and
	 * verified to contain only animations belonging to that one skill.
	 */
	private static final Map<String, String> SKILL_PREFIXES = buildSkillPrefixes();

	/**
	 * Field-name prefix identifying player teleport animation frames. Covers the
	 * standard Lumbridge home-teleport casting sequence
	 * ({@code HOME_TELEPORT_HUMAN_*}). Spellbook/jewellery teleports are added
	 * explicitly below since they are individually named.
	 */
	private static final String HOME_TELEPORT_PREFIX = "HOME_TELEPORT_HUMAN";

	/**
	 * Name suffixes that mark a <em>non-active</em> pose inside an otherwise
	 * whitelisted skill family. The prefix sweep is name-based and necessarily
	 * greedy, so it would otherwise pull in standing-at-station / wind-up /
	 * nothing-to-do poses that share a skill's stem but are not active skilling:
	 * e.g. {@code HUMAN_SMITHING_IDLE} (standing at the anvil), {@code
	 * HUMAN_SMITHING_ENTER} (stepping up to it) and {@code HUMAN_FLETCHING_NO_ITEMS}
	 * (the "nothing left to fletch" pose). Treating those as active skilling makes
	 * the avatar keep skilling while the player is, in fact, idle — a false positive
	 * that also lengthens the apparent activity tail.
	 *
	 * <p>Excluding by suffix token rather than by hard-coded ID keeps the registry
	 * self-maintaining: a future RuneLite bump that adds, say, {@code
	 * HUMAN_COOKING_IDLE} is filtered automatically. Matched with
	 * {@code name.endsWith(token)} after a skill prefix matches.
	 */
	private static final Set<String> EXCLUDED_POSE_SUFFIXES = Set.of(
		"_IDLE",      // standing-idle pose at a station (e.g. HUMAN_SMITHING_IDLE)
		"_ENTER",     // walk-up / enter pose (e.g. HUMAN_SMITHING_ENTER)
		"_NO_ITEMS"   // nothing-to-work-on pose (e.g. HUMAN_FLETCHING_NO_ITEMS)
	);

	private final Map<Integer, String> activityByAnimation;
	private final Set<Integer> teleportAnimations;

	private ActivityAnimationRegistry(Map<Integer, String> activityByAnimation, Set<Integer> teleportAnimations)
	{
		this.activityByAnimation = activityByAnimation;
		this.teleportAnimations = teleportAnimations;
	}

	private static Map<String, String> buildSkillPrefixes()
	{
		// LinkedHashMap: longer / more-specific prefixes must precede any prefix
		// they could be confused with. (None currently overlap, but the ordering
		// guarantee is cheap insurance.)
		LinkedHashMap<String, String> prefixes = new LinkedHashMap<>();
		prefixes.put("HUMAN_WOODCUTTING", "Woodcutting");
		prefixes.put("HUMAN_MINING", "Mining");
		prefixes.put("HUMAN_FISHING", "Fishing");
		// The sustained "fishing at the spot" loop is HUMAN_FISH_ONSPOT* (623, …) —
		// a different stem from HUMAN_FISHING_*. Without it the main fishing loop is
		// invisible to detection and the avatar flickers between casts. Use the full
		// "HUMAN_FISH_ONSPOT" stem so HUMAN_FISHSIZE (a cutscene) is not swept in.
		prefixes.put("HUMAN_FISH_ONSPOT", "Fishing");
		prefixes.put("HUMAN_HARPOON", "Fishing");
		prefixes.put("HUMAN_SMALLNET", "Fishing");
		// Big-net fishing uses the HUMAN_LARGENET stem (620) and a separate sustained
		// loop HUMAN_LARGENET_LOOPING (11042). Like HUMAN_FISH_ONSPOT above, these are a
		// different stem from the other fishing methods, so without an explicit entry
		// net fishing is invisible to detection and the avatar flickers between casts.
		prefixes.put("HUMAN_LARGENET", "Fishing");
		prefixes.put("HUMAN_SMITHING", "Smithing");
		prefixes.put("HUMAN_FLETCHING", "Fletching");
		prefixes.put("HUMAN_HERBING", "Herblore");
		prefixes.put("HUMAN_COOKING", "Cooking");
		// Cooking on a fire is HUMAN_FIRECOOKING (897), a different stem from the range
		// animation HUMAN_COOKING (896) and its HUMAN_COOKING_LOOP. Range cooking is
		// already smooth because both match the HUMAN_COOKING prefix; fire cooking was
		// missing, so the avatar flickered to idle between items — the reported bug, and
		// the same orphan-stem class the HUMAN_FISH_ONSPOT fishing fix resolved.
		prefixes.put("HUMAN_FIRECOOKING", "Cooking");
		prefixes.put("HUMAN_RUNECRAFT", "Runecraft");
		prefixes.put("HUMAN_FARMING", "Farming");
		prefixes.put("HUMAN_PICKPOCKET", "Thieving");
		return Collections.unmodifiableMap(prefixes);
	}

	/**
	 * @return {@code true} if {@code name} is a non-active pose that shares a
	 *         whitelisted skill's stem and must therefore be excluded
	 */
	private static boolean isExcludedPose(String name)
	{
		for (String suffix : EXCLUDED_POSE_SUFFIXES)
		{
			if (name.endsWith(suffix))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the activity label for {@code animationId}, or {@link Optional#empty()}
	 *         if it is not a recognised skilling animation
	 */
	public Optional<String> getActivityForAnimation(int animationId)
	{
		return Optional.ofNullable(activityByAnimation.get(animationId));
	}

	/** @return {@code true} if {@code animationId} is a known teleport animation */
	public boolean isTeleportAnimation(int animationId)
	{
		return teleportAnimations.contains(animationId);
	}

	/**
	 * Build the registry by reflecting over {@link AnimationID}. Immutable after
	 * construction.
	 */
	public static ActivityAnimationRegistry build()
	{
		Map<Integer, String> byAnimation = new HashMap<>();
		Set<Integer> teleports = new HashSet<>();

		for (Field field : AnimationID.class.getFields())
		{
			if (field.getType() != int.class || !Modifier.isStatic(field.getModifiers()))
			{
				continue;
			}

			final int value;
			try
			{
				value = field.getInt(null);
			}
			catch (IllegalAccessException ex)
			{
				continue;
			}

			String name = field.getName();

			if (name.startsWith(HOME_TELEPORT_PREFIX))
			{
				teleports.add(value);
				continue;
			}

			for (Map.Entry<String, String> entry : SKILL_PREFIXES.entrySet())
			{
				if (name.startsWith(entry.getKey()))
				{
					// A name that matched a skill family but is a non-active pose
					// (idle/enter/no-items) is dropped entirely, not offered to a
					// lower-priority prefix — hence break, not continue.
					if (isExcludedPose(name))
					{
						break;
					}
					// putIfAbsent: keep the first (highest-priority) prefix match.
					byAnimation.putIfAbsent(value, entry.getValue());
					break;
				}
			}
		}

		// Spellbook / jewellery teleport casts (individually named constants).
		teleports.add(AnimationID.HUMAN_CASTTELEPORT);
		teleports.add(AnimationID.HUMAN_CAST2_TELEPORT);

		return new ActivityAnimationRegistry(
			Collections.unmodifiableMap(byAnimation),
			Collections.unmodifiableSet(teleports));
	}
}
