package com.idlefamiliar;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Idle Familiar",
	description = "Passive desktop avatar for idle, AFK, and status awareness",
	tags = {"idle", "afk", "avatar", "familiar", "desktop"}
)
public class IdleFamiliarPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(IdleFamiliarPlugin.class);
	private static final int COMBAT_LINGER_TICKS = 4;
	/** Ticks the TELEPORTING state lingers after a teleport animation fires. */
	private static final int TELEPORT_LINGER_TICKS = 3;
	/** Ticks the GRAND_EXCHANGE state lingers after an offer fills (so it is visible). */
	private static final int GE_FILLED_LINGER_TICKS = 5;
	/**
	 * Ticks the SKILLING (Agility) state is held after an Agility XP drop, surviving
	 * movement. Agility is XP-detected (no clean animation namespace) and the player
	 * is already moving away when the XP lands, so the normal movement-cancel would
	 * otherwise drop the state before it is ever shown. This is only a brief trigger
	 * to ENTER the agility state (like the teleport/GE lingers); the actual playback
	 * length is owned by the one-shot latch in {@code AnimationController}
	 * ({@code agility_loop} is a {@code ONE_SHOT_KEY}), which plays the sheet's full
	 * cycle to completion regardless of state - so it adapts automatically to a
	 * longer or shorter agility animation and this value never needs tuning.
	 */
	private static final int AGILITY_LINGER_TICKS = 2;
	/** Ticks the DEATH reaction is shown after the player's hitpoints hit 0. */
	private static final int DEATH_LINGER_TICKS = 8;
	/**
	 * Ticks the LEVEL_UP flourish state is held after a real level rises. Brief, like
	 * the teleport/GE lingers - {@code level_up} is a one-shot key, so the latch plays
	 * the full flourish to completion regardless of this value.
	 */
	private static final int LEVEL_UP_LINGER_TICKS = 3;
	/** Ticks a configured chat-message trigger is shown as a custom event. */
	private static final int CHAT_MESSAGE_LINGER_TICKS = 4;
	/**
	 * OSRS bank interface group id (RuneLite {@code InterfaceID.BANK}). The bank
	 * widget loading/closing under this group is a precise open/close signal for
	 * the BANKING state. Using the literal keeps us off version-specific constant
	 * class names; this id has been stable for years.
	 */
	private static final int BANK_GROUP_ID = 12;
	/** Sub-directory under the RuneLite home for external, user-supplied animation sheets. */
	private static final String EXTERNAL_AVATAR_DIR = "idle-familiar/avatar";

	/**
	 * Hard cap on the effective skilling linger, in game ticks. The linger is the
	 * post-stop tail (and the gap-bridge for click-per-item skills). A stale value
	 * persisted in a profile from earlier development could otherwise survive the
	 * code default and make the avatar over-run an activity by several animation
	 * loops (the reported smithing tail); clamping the value we actually use keeps
	 * the drop crisp for every skill regardless of what is stored. ~2.4s.
	 */
	private static final int MAX_SKILLING_LINGER_TICKS = 4;

	@Inject
	private Client client;

	@Inject
	private IdleFamiliarConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ClientUI clientUI;

	private final IdleStateTracker idleStateTracker = new IdleStateTracker();
	private final PlayerActivityService activityService = new PlayerActivityService();
	private final AnimationController animationController = new AnimationController();
	private final NotificationController notificationController = new NotificationController();
	private final SoundController soundController = new SoundController();
	private final SoundCueGate soundCueGate = new SoundCueGate();
	private final SkillingActivityTracker skillingTracker = new SkillingActivityTracker();
	private final ActivityAnimationRegistry activityRegistry = ActivityAnimationRegistry.build();
	/** Per-session XP/hr source for the widget readout. (The XP Tracker plugin's service is not injectable from an external plugin.) */
	private final XpRateTracker xpRateTracker = new XpRateTracker();
	private DesktopPetWindow desktopPetWindow;

	/** Cached XP/hr text (Xp Tracker service when present, else internal), refreshed on the client thread and published via {@link #widgetSnapshot}. */
	private String cachedXpHr;

	/** Written and read on the client thread; published to the widget via {@link #widgetSnapshot}. */
	private AvatarState currentAvatarState = AvatarState.LOGGED_OUT;
	private WorldPoint lastPlayerLocation;
	private int combatTicksRemaining;
	/** Counts down the TELEPORTING state after a teleport animation; 0 = not teleporting. */
	private int teleportTicksRemaining;
	/** Counts down the GRAND_EXCHANGE state after an offer fills; 0 = nothing to show. */
	private int grandExchangeTicksRemaining;
	/** Counts down the movement-resistant Agility hold after an Agility XP drop; 0 = inactive. */
	private int agilityTicksRemaining;
	/** Counts down the DEATH reaction after hitpoints hit 0; 0 = not shown. */
	private int deathTicksRemaining;
	/** Counts down the LEVEL_UP flourish after a real level rises; 0 = not shown. */
	private int levelUpTicksRemaining;
	/** Counts down a configured chat-message reaction; 0 = inactive. */
	private int chatMessageTicksRemaining;
	/** Whether the player changed tile this tick — gates per-tick skilling detection. */
	private boolean movedThisTick;

	/** Combat sub-style ("melee"/"ranged"/"magic") from the latest combat XP, for combat_&lt;style&gt; sheets. Client thread only; reaches the widget via {@link #widgetSnapshot}. */
	private String combatStyle;
	/** Last observed boosted hitpoints, for death-edge detection (HP &gt; 0 then 0). */
	private int lastHitpoints = -1;
	/** Last real skill levels, to fire a level-up flourish only on a genuine increase. */
	private final java.util.Map<Skill, Integer> lastSkillLevels = new java.util.HashMap<>();
	/** Last skill XP values, used to distinguish real XP gains from passive stat changes. */
	private final java.util.Map<Skill, Integer> lastSkillXp = new java.util.HashMap<>();

	/**
	 * Cached skill-icon sprite for the confirmed skill, refreshed on the client
	 * thread each game tick and copied into {@link #widgetSnapshot}. The
	 * {@link net.runelite.client.game.SpriteManager#getSprite} fetch must stay on the
	 * client thread (it throws off it), which is why the icon is cached here for the
	 * widget instead of being fetched during the widget's EDT paint.
	 */
	private BufferedImage confirmedSkillIcon;
	/** Cached vitals (client thread); published to the widget via {@link #widgetSnapshot}. */
	private int cachedHitpoints;
	private int cachedMaxHitpoints;
	private int cachedPrayer;
	private int cachedMaxPrayer;
	/** Cached occupied inventory slot count (client thread); published via {@link #widgetSnapshot}. */
	private int cachedInventoryCount;
	/**
	 * The latest immutable view the desktop widget paints from, rebuilt on the client
	 * thread each tick. The widget reads this single volatile reference instead of
	 * reaching across the thread boundary for each field, so a paint always sees a
	 * coherent set of values captured at the same instant.
	 */
	private volatile WidgetSnapshot widgetSnapshot = WidgetSnapshot.loggedOut();
	/** Whether the inventory was full on the previous update, for rising-edge detection. */
	private boolean inventoryWasFull;
	private boolean lowHitpointsWasActive;
	private boolean lowPrayerWasActive;
	/** Sprite id backing {@link #confirmedSkillIcon}, to detect skill changes. */
	private Integer cachedSkillSpriteId;
	private String lastSoundAnimationKey;
	private AvatarState lastSoundAvatarState;
	private String lastSoundActivityLabel = "";

	/** The runtime drop-in folder for user-supplied animation sheets. */
	private File externalAvatarDir;

	@Override
	protected void startUp()
	{
		// Allow users to drop in / override animation sheets at runtime (no rebuild)
		// by placing PNGs in <RuneLite home>/idle-familiar/avatar/. Created up-front
		// so the folder is easy to find. Falls back to bundled assets when empty.
		externalAvatarDir = new File(RuneLite.RUNELITE_DIR, EXTERNAL_AVATAR_DIR);
		//noinspection ResultOfMethodCallIgnored
		externalAvatarDir.mkdirs();
		animationController.setExternalAssetDir(externalAvatarDir);

		animationController.loadDefaultAnimations();
		applySoundConfig();
		desktopPetWindow = new DesktopPetWindow(this, config, configManager, this::focusGameClient);
		idleStateTracker.reset(0);
		activityService.reset();
		skillingTracker.reset();
		xpRateTracker.reset();
		teleportTicksRemaining = 0;
		grandExchangeTicksRemaining = 0;
		agilityTicksRemaining = 0;
		deathTicksRemaining = 0;
		levelUpTicksRemaining = 0;
		chatMessageTicksRemaining = 0;
		combatStyle = null;
		lastHitpoints = -1;
		lastSkillLevels.clear();
		lastSkillXp.clear();
		lowHitpointsWasActive = false;
		lowPrayerWasActive = false;
		lastSoundAnimationKey = null;
		lastSoundAvatarState = null;
		lastSoundActivityLabel = "";
		soundCueGate.reset();
		desktopPetWindow.refreshVisibility();

		log.debug("Idle Familiar started");
	}

	@Override
	protected void shutDown()
	{
		if (desktopPetWindow != null)
		{
			desktopPetWindow.hide();
			desktopPetWindow = null;
		}
		notificationController.clear();
		lastPlayerLocation = null;
		currentAvatarState = AvatarState.LOGGED_OUT;
		log.debug("Idle Familiar stopped");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		idleStateTracker.setCurrentTick(idleStateTracker.getCurrentTick() + 1);
		Player localPlayer = client.getLocalPlayer();
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN && localPlayer != null;
		activityService.setLoggedIn(loggedIn);

		if (!loggedIn)
		{
			lastPlayerLocation = null;
			currentAvatarState = AvatarState.LOGGED_OUT;
			skillingTracker.reset();
			xpRateTracker.reset();
			teleportTicksRemaining = 0;
			grandExchangeTicksRemaining = 0;
			agilityTicksRemaining = 0;
			deathTicksRemaining = 0;
			levelUpTicksRemaining = 0;
			chatMessageTicksRemaining = 0;
			combatStyle = null;
			lastHitpoints = -1;
			lastSkillLevels.clear();
			lastSkillXp.clear();
			activityService.setBanking(false);
			activityService.setCustomEvent(false);
			confirmedSkillIcon = null;
			cachedSkillSpriteId = null;
			cachedXpHr = null;
			cachedHitpoints = 0;
			cachedMaxHitpoints = 0;
			cachedPrayer = 0;
			cachedMaxPrayer = 0;
			cachedInventoryCount = 0;
			inventoryWasFull = false;
			lowHitpointsWasActive = false;
			lowPrayerWasActive = false;
			soundCueGate.reset();
			handleAvatarStateTransition(AvatarState.LOGGED_OUT);
			publishWidgetSnapshot();
			return;
		}

		updateMovement(localPlayer);
		primeSkillXpCache();
		updateSkillingFromAnimation(localPlayer);
		updateTransientStates();
		updateVitals();
		detectDeath();
		updateInventoryFull();
		updateLingeringActivity(localPlayer);
		resolveCurrentState();
		updateConfirmedSkillIcon();
		updateXpHr();
		publishWidgetSnapshot();
		logDebugState(localPlayer);
	}

	/**
	 * Per-tick instrumentation for diagnosing the timing/feel issues that are only
	 * visible in a running client (animation flicker, activity tail). Gated behind
	 * the {@code debugState} config so it is silent in normal use. Enable "Debug
	 * state" in the plugin config, perform the activity (fish / fletch / mine), and
	 * read the RuneLite client log: each line shows the raw animation id the game
	 * reports, whether the registry recognises it, the resolved skilling/state, and
	 * whether target-based sustain is holding skilling alive — which makes both the
	 * "missing whitelist id" and the "genuinely still animating" cases self-evident.
	 */
	private void logDebugState(Player localPlayer)
	{
		if (!config.debugState() || localPlayer == null)
		{
			return;
		}
		int anim = localPlayer.getAnimation();
		log.info("[idle-familiar] tick={} anim={} regLabel={} moved={} skilling={} sustained={} state={} label={}",
			idleStateTracker.getCurrentTick(),
			anim,
			activityRegistry.getActivityForAnimation(anim).orElse("-"),
			movedThisTick,
			activityService.isSkilling(),
			skillingTracker.isSustainedByTarget(),
			currentAvatarState,
			activityService.getActivityLabel());
	}

	/**
	 * Advance the short-lived TELEPORTING and GRAND_EXCHANGE linger counters and
	 * mirror them onto the activity service. Both are armed by their event handlers
	 * and decay on their own so the avatar shows them briefly then returns to its
	 * underlying state.
	 */
	private void updateTransientStates()
	{
		if (teleportTicksRemaining > 0)
		{
			teleportTicksRemaining--;
		}
		activityService.setTeleporting(teleportTicksRemaining > 0);

		if (grandExchangeTicksRemaining > 0)
		{
			grandExchangeTicksRemaining--;
		}
		activityService.setGrandExchangeFilled(grandExchangeTicksRemaining > 0);

		if (agilityTicksRemaining > 0)
		{
			agilityTicksRemaining--;
		}

		if (deathTicksRemaining > 0)
		{
			deathTicksRemaining--;
		}
		activityService.setDead(deathTicksRemaining > 0);

		if (levelUpTicksRemaining > 0)
		{
			levelUpTicksRemaining--;
		}
		activityService.setLevelUp(levelUpTicksRemaining > 0);

		if (chatMessageTicksRemaining > 0)
		{
			chatMessageTicksRemaining--;
		}
		activityService.setCustomEvent(chatMessageTicksRemaining > 0);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"idlefamiliar".equals(event.getGroup()))
		{
			return;
		}

		// The "Reload animations" / "Validate animations" toggles act as buttons: a tick
		// fires the action once, then resets itself so it can be tapped again. The reset
		// posts its own (false) ConfigChanged, which falls through harmlessly below.
		if ("reloadAnimations".equals(event.getKey()) && "true".equals(event.getNewValue()))
		{
			configManager.setConfiguration(IdleFamiliarConfig.GROUP, "reloadAnimations", false);
			handleReloadAnimations();
			return;
		}
		if ("validateAnimations".equals(event.getKey()) && "true".equals(event.getNewValue()))
		{
			configManager.setConfiguration(IdleFamiliarConfig.GROUP, "validateAnimations", false);
			handleValidateAnimations();
			return;
		}

		if (desktopPetWindow != null)
		{
			desktopPetWindow.refreshVisibility();
		}
		applySoundConfig();
	}

	/** Reload every sheet/weights from disk (drop-in folder included). Safe off the client thread. */
	private void handleReloadAnimations()
	{
		animationController.reloadAnimations();
		log.debug("Idle Familiar animations reloaded");
		announce("Idle Familiar: animations reloaded.");
	}

	/** Validate the drop-in animation folder and report any issues to chat and the log. */
	private void handleValidateAnimations()
	{
		List<String> issues = AvatarAssetValidator.validate(externalAvatarDir, knownAssetBases());
		if (issues.isEmpty())
		{
			log.debug("Idle Familiar animation validation: no issues");
			announce("Idle Familiar: animation validation found no issues.");
			return;
		}
		log.warn("Idle Familiar animation validation found {} issue(s):", issues.size());
		for (String issue : issues)
		{
			log.warn("  {}", issue);
		}
		announce("Idle Familiar: animation validation found " + issues.size() + " issue(s) (see client log).");
	}

	/**
	 * Post a game-chat line on the client thread (config events may arrive on the Swing
	 * EDT, where the client API is unsafe to touch). Silently does nothing when not
	 * logged in — the log line above still records the result.
	 */
	private void announce(String message)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
			}
		});
	}

	/**
	 * Every primary base name the loader can resolve - one per avatar state, the
	 * combat sub-styles, and one per skill - in both the bare and {@code _loop} forms
	 * {@link AnimationController} accepts. Lets "Validate animations" flag drop-in
	 * sheets that map to nothing (typos / orphans).
	 */
	private Set<String> knownAssetBases()
	{
		Set<String> bases = new HashSet<>();
		for (AvatarState state : AvatarState.values())
		{
			addBase(bases, animationController.resolveAssetName(state, ""));
		}
		for (String style : new String[]{"melee", "ranged", "magic"})
		{
			addBase(bases, "combat_" + style);
		}
		for (Skill skill : Skill.values())
		{
			addBase(bases, skill.name().toLowerCase(Locale.ROOT).replace(' ', '_'));
		}
		return bases;
	}

	/** Add both the bare and {@code _loop} forms of an asset key, as the loader accepts either. */
	private static void addBase(Set<String> bases, String key)
	{
		bases.add(key);
		bases.add(key + "_loop");
	}

	private void applySoundConfig()
	{
		soundController.setEnabled(config.enableSounds());
		soundController.setVolumePercent(config.soundVolumePercent());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			activityService.setLoggedIn(true);
			markActivity(ActivityType.UNKNOWN);
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			activityService.setLoggedIn(false);
			currentAvatarState = AvatarState.LOGGED_OUT;
			// Bank widgets are torn down on hop/disconnect without a WidgetClosed —
			// clear banking here so the avatar can't get stuck in the BANKING state.
			activityService.setBanking(false);
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		int animId = localPlayer.getAnimation();
		if (animId == -1)
		{
			return;
		}

		// Whitelist-only skilling detection: an animation refreshes the skilling
		// linger (and sets the label / toad sprite) only when it is a recognised
		// skilling animation. Emotes, eating, walking and teleport wind-ups are
		// absent from the registry and therefore silently ignored.
		activityRegistry.getActivityForAnimation(animId).ifPresent(this::markSkillingSignal);

		// A teleport is a committed action; surface it briefly via its own state.
		if (activityRegistry.isTeleportAnimation(animId))
		{
			teleportTicksRemaining = TELEPORT_LINGER_TICKS;
			activityService.setTeleporting(true);
			playGameSound(GameSoundEvent.TELEPORT);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!ChatSoundFilter.isFilteredTrigger(event.getType(), event.getMessage(), config.chatMessageFilters()))
		{
			return;
		}
		String matchedFilter = ChatMessageMatcher.matchedFilter(event.getMessage(), config.chatMessageFilters());
		chatMessageTicksRemaining = CHAT_MESSAGE_LINGER_TICKS;
		activityService.setCustomEvent(true);
		notificationController.show(shortChatNotification(matchedFilter), 3, 2400);
		if (SoundActivation.chatMessage(config))
		{
			soundController.playChatMessage();
		}
	}

	/**
	 * Arm combat ONLY on real hit evidence, and accurately enough to exclude
	 * damage that is NOT combat:
	 * <ul>
	 *   <li><b>You dealt a hit</b> ({@code hitsplat.isMine()}) — you only deal
	 *       damage by attacking, so this is unambiguous combat, on any actor.</li>
	 *   <li><b>You took a hit while engaged</b> — a hitsplat on you while you have
	 *       an interaction target and are NOT actively skilling. The skilling guard
	 *       stops a poison/venom tick (or a fishing-spot/banker interaction) from
	 *       flipping the avatar to combat mid-activity; the target guard stops a
	 *       lone poison/environmental tick while walking from arming combat.</li>
	 * </ul>
	 * This replaces the old "any interaction target = combat" heuristic.
	 * {@link #combatTicksRemaining} decays in {@link #updateLingeringActivity} and
	 * is refreshed by combat XP in {@link #onStatChanged}.
	 */
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		boolean dealtByMe = event.getHitsplat() != null && event.getHitsplat().isMine();
		boolean onMe = event.getActor() == localPlayer;
		boolean hasTarget = localPlayer.getInteracting() != null;
		boolean skillingActive = skillingTracker.isSkilling(
			idleStateTracker.getCurrentTick(), skillingLingerTicks());

		if (dealtByMe || (onMe && hasTarget && !skillingActive))
		{
			boolean wasInCombat = combatTicksRemaining > 0;
			combatTicksRemaining = COMBAT_LINGER_TICKS;
			markActivity(ActivityType.COMBAT);
			if (!wasInCombat)
			{
				playGameSound(GameSoundEvent.COMBAT);
			}
		}
	}

	/** The bank interface opened → enter BANKING (sits above combat in the ladder). */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == BANK_GROUP_ID)
		{
			activityService.setBanking(true);
		}
	}

	/** The bank interface closed → leave BANKING. */
	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == BANK_GROUP_ID)
		{
			activityService.setBanking(false);
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		// A bought/sold offer is a completed ("filled") offer worth highlighting.
		GrandExchangeOfferState state = event.getOffer().getState();
		if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD)
		{
			grandExchangeTicksRemaining = GE_FILLED_LINGER_TICKS;
			activityService.setGrandExchangeFilled(true);
			playGameSound(GameSoundEvent.GRAND_EXCHANGE);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		// A genuine level increase fires a brief LEVEL_UP flourish.
		checkLevelUp(event);

		boolean activeXpGain = isActiveXpGain(event);

		// Feed the fallback XP/hr tracker (used when the Xp Tracker service is absent).
		String statName = event.getSkill().getName();
		if (!SkillingActivityTracker.isPassiveSkill(statName) || activeXpGain)
		{
			xpRateTracker.record(statName, event.getXp(), System.currentTimeMillis());
		}

		if (isLikelyCombatStat(event.getSkill()) && combatTicksRemaining > 0)
		{
			// Remember the combat style so the avatar can show combat_melee/ranged/magic.
			String style = styleForCombatSkill(event.getSkill());
			if (style != null)
			{
				combatStyle = style;
			}
			markActivity(ActivityType.COMBAT);
			return;
		}

		// Passive XP sources (hitpoints regeneration, prayer drain) fire stat
		// changes but are not active skilling — ignore them so they neither
		// start skilling nor extend an existing skilling linger.
		String skillName = event.getSkill().getName();
		if (SkillingActivityTracker.isPassiveSkill(skillName) && !activeXpGain)
		{
			return;
		}

		// Agility animates WHILE the player moves across an obstacle, so the normal
		// movement-cancel would otherwise chop its animation to a fraction. Arm a
		// short movement-resistant hold (consumed in updateLingeringActivity) so the
		// agility sheet plays a full loop before walking resumes.
		if (event.getSkill() == Skill.AGILITY)
		{
			agilityTicksRemaining = AGILITY_LINGER_TICKS;
		}

		boolean wasSkilling = skillingTracker.isSkilling(idleStateTracker.getCurrentTick(), skillingLingerTicks());
		markSkillingSignal(skillName, activeXpGain);
		if (!wasSkilling)
		{
			playGameSound(GameSoundEvent.SKILLING);
		}
		if (config.reactToSkilling())
		{
			notificationController.show(skillName, 1, 1600);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INVENTORY.getId())
		{
			if (skillingTracker.isSkilling(idleStateTracker.getCurrentTick(), skillingLingerTicks()))
			{
				markActivity(ActivityType.SKILLING);
			}
			else
			{
				markActivity(ActivityType.INVENTORY);
			}
			updateInventoryFull(event.getItemContainer());
		}
	}

	BufferedImage getCurrentFrame()
	{
		animationController.setPlaybackSpeed(config.animationSpeed().multiplier());
		animationController.setPlayFullCycleOneShots(config.playFullCycleAnimations());

		// Debug preview: force a chosen state/skill so art can be checked without
		// reproducing the in-game condition. OFF (the default) renders normally.
		AvatarPreview preview = config.debugPreviewState();
		if (preview != null && preview != AvatarPreview.OFF)
		{
			return animationController.getFrame(preview.state(), preview.label());
		}

		// Frame state/label come from the per-tick widget snapshot so the rendered
		// frame stays consistent with the rest of the widget's painted values.
		WidgetSnapshot snapshot = widgetSnapshot;
		return animationController.getFrame(snapshot.getState(), snapshot.getAnimationLabel());
	}

	/** @return the latest widget snapshot (never null); see {@link #publishWidgetSnapshot()}. */
	WidgetSnapshot getWidgetSnapshot()
	{
		return widgetSnapshot;
	}

	/**
	 * Assemble and publish the immutable {@link WidgetSnapshot} the desktop widget
	 * paints from. Called on the client thread at the end of each tick, so the widget
	 * (Swing EDT) reads one coherent set of values instead of racing each field. The
	 * animation frame itself is still pulled live in {@link #getCurrentFrame()} because
	 * it advances on the 100ms repaint timer, not the game tick.
	 */
	private void publishWidgetSnapshot()
	{
		String animationLabel = currentAvatarState == AvatarState.COMBAT
			? (combatStyle == null ? "" : combatStyle)
			: activityService.getActivityLabel();
		String message = notificationController.getMessage(
			currentAvatarState, activityService.getCurrentActivity(), activityService.getActivityLabel());
		widgetSnapshot = new WidgetSnapshot(
			currentAvatarState,
			animationLabel,
			cachedHitpoints,
			cachedMaxHitpoints,
			cachedPrayer,
			cachedMaxPrayer,
			cachedInventoryCount,
			cachedXpHr,
			message,
			confirmedSkillIcon);
	}

	private void updateMovement(Player localPlayer)
	{
		WorldPoint currentLocation = localPlayer.getWorldLocation();
		activityService.setWalking(false);
		activityService.setRunning(false);
		movedThisTick = false;

		if (lastPlayerLocation == null || currentLocation == null)
		{
			lastPlayerLocation = currentLocation;
			return;
		}

		if (currentLocation.equals(lastPlayerLocation))
		{
			return;
		}

		movedThisTick = true;

		// The player moved this tick. Movement and skilling are mutually exclusive,
		// so cancel the skilling linger immediately — this is the core fix for the
		// fish-then-walk-to-bank false positive, where a replayed loop animation
		// used to keep the skilling window alive for ~27 seconds.
		skillingTracker.cancelLinger();
		activityService.setActivityLabel("");
		// You cannot move with the bank open, so movement also closes banking — a
		// safety net in case a WidgetClosed(bank) event was missed.
		activityService.setBanking(false);
		markActivity(ActivityType.WALKING);

		// Distinguish walking (1 tile/tick) from running (2 tiles/tick) by tile
		// delta. Larger deltas (teleports, region loads) are not locomotion and are
		// left to the teleport handler. distanceTo() returns a large value across
		// planes, so those are ignored too.
		int distance = currentLocation.distanceTo(lastPlayerLocation);
		if (distance == 1)
		{
			activityService.setWalking(true);
		}
		else if (distance == 2)
		{
			activityService.setRunning(true);
		}

		lastPlayerLocation = currentLocation;
	}

	/**
	 * Poll the player's live animation every tick and treat a whitelisted skilling
	 * animation as ground truth for "skilling right now". For sustained skills
	 * (mining, woodcutting, fishing) the action animation is present continuously
	 * while working and gone the instant you stop, so this — combined with a tiny
	 * linger — lets the avatar drop out of skilling almost immediately rather than
	 * riding out a long window. The short linger only bridges the sub-second
	 * animation gaps of click-per-item skills (fletching, some cooking).
	 *
	 * <p>{@link #onAnimationChanged} still records signals too, so a brief one-shot
	 * animation that starts and ends between tick boundaries is not missed.
	 */
	private void updateSkillingFromAnimation(Player localPlayer)
	{
		// Movement already cancelled the linger this tick and wins over skilling.
		if (movedThisTick)
		{
			return;
		}

		int animId = localPlayer.getAnimation();
		if (animId == -1)
		{
			return;
		}

		activityRegistry.getActivityForAnimation(animId).ifPresent(this::markSkillingSignal);
	}

	private void updateVitals()
	{
		int hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int prayer = client.getBoostedSkillLevel(Skill.PRAYER);
		boolean lowHitpoints = config.enableLowHpWarning() && hitpoints > 0 && hitpoints <= config.lowHpThreshold();
		boolean lowPrayer = config.enableLowPrayerWarning() && prayer <= config.lowPrayerThreshold();
		activityService.setLowHitpoints(lowHitpoints);
		activityService.setLowPrayer(lowPrayer);
		if (lowHitpoints && !lowHitpointsWasActive)
		{
			playGameSound(GameSoundEvent.LOW_HEALTH);
		}
		if (lowPrayer && !lowPrayerWasActive)
		{
			playGameSound(GameSoundEvent.LOW_PRAYER);
		}
		lowHitpointsWasActive = lowHitpoints;
		lowPrayerWasActive = lowPrayer;

		// Cache current/max HP and prayer on the client thread so the desktop widget
		// (which paints on the Swing EDT) can draw its orbs without touching the
		// client off-thread. getRealSkillLevel is the unboosted "max".
		cachedHitpoints = hitpoints;
		cachedMaxHitpoints = client.getRealSkillLevel(Skill.HITPOINTS);
		cachedPrayer = prayer;
		cachedMaxPrayer = client.getRealSkillLevel(Skill.PRAYER);
	}

	private void updateInventoryFull()
	{
		updateInventoryFull(client.getItemContainer(InventoryID.INVENTORY));
	}

	private void updateInventoryFull(ItemContainer inventory)
	{
		boolean full = false;
		int count = 0;
		if (inventory != null)
		{
			Item[] items = inventory.getItems();
			int slots = Math.min(28, items.length);
			for (int index = 0; index < slots; index++)
			{
				if (items[index] != null && items[index].getId() != -1)
				{
					count++;
				}
			}
			full = count >= 28;
		}
		cachedInventoryCount = count;

		boolean reactFull = config.enableInventoryFullWarning() && full;

		// Rising edge: the inventory just filled. A gathering action (fishing,
		// mining, woodcutting) has necessarily stopped, but target-based sustain
		// keeps the player "interacting" with the fishing spot/rock, so skilling —
		// which outranks INVENTORY_FULL in the state ladder — would otherwise keep
		// the skilling animation looping for several seconds before the inventory-
		// full reaction surfaces. Cancel the skilling linger now so the reaction
		// shows on the same tick the inventory fills. A continuous skill that legit-
		// imately runs with a full inventory (e.g. cooking) re-confirms via its
		// per-tick animation on the next tick, so this only suppresses the stale tail.
		if (reactFull && !inventoryWasFull)
		{
			skillingTracker.cancelLinger();
			activityService.setSkilling(false);
			playGameSound(GameSoundEvent.INVENTORY_FULL);
		}
		inventoryWasFull = reactFull;

		activityService.setInventoryFull(reactFull);
	}

	private void updateLingeringActivity(Player localPlayer)
	{
		Actor target = localPlayer.getInteracting();
		boolean hasTarget = target != null;

		int currentTick = idleStateTracker.getCurrentTick();
		int lingerTicks = skillingLingerTicks();

		// Target-based sustain for NPC-interaction skills (fishing, thieving): while
		// the player keeps interacting with the SAME actor that a skilling animation
		// was confirmed against, skilling stays alive across animation gaps and drops
		// the instant the target changes/clears — decoupling the inter-action bridge
		// from the post-stop tail (the core tension a single global linger can't
		// resolve). An opaque identity token keeps the tracker free of RuneLite types
		// and needs no fishing-spot ID list. Movement already cancelled the linger and
		// cleared lastSignalTick this tick, so a moving player cannot (re)confirm a
		// target here.
		int targetToken = hasTarget ? System.identityHashCode(target) : SkillingActivityTracker.NO_TARGET;
		skillingTracker.recordInteractionTarget(targetToken, currentTick);

		// Combat is NO LONGER inferred from "has an interaction target". That old
		// heuristic read bankers, shopkeepers, quest NPCs, tool leprechauns and even
		// fishing spots (before their animation confirmed) as COMBAT — the reported
		// false positive. Combat is now armed only by real hit evidence in
		// onHitsplatApplied (a hitsplat on the player, or one the player dealt to its
		// target); here we just decay that linger.
		if (combatTicksRemaining > 0)
		{
			combatTicksRemaining--;
		}

		boolean combatLingering = combatTicksRemaining > 0;
		if (!combatLingering)
		{
			// Combat ended: forget the style so a later fight starts generic until its
			// first combat XP confirms melee/ranged/magic again.
			combatStyle = null;
		}

		// NB: the skilling linger is now refreshed ONLY by whitelisted skilling
		// animations (onAnimationChanged) and by XP-drop signals (onStatChanged).
		// The old "any animation while not in combat refreshes skilling" heuristic
		// was removed — it was what let the walk animation resurrect a stale
		// skilling state. Movement explicitly cancels the linger in updateMovement.

		// The Agility hold keeps skilling alive across the movement that an obstacle
		// requires (movement cancelled the tracker linger above), so the agility
		// animation plays a full loop instead of being cut to a fraction by walking.
		boolean agilityHold = agilityTicksRemaining > 0;

		activityService.setInCombat(config.reactToCombat() && combatLingering);
		activityService.setSkilling(config.reactToSkilling()
			&& (skillingTracker.isSkilling(currentTick, lingerTicks) || agilityHold));
		if (activityService.isSkilling())
		{
			idleStateTracker.markActivity(ActivityType.SKILLING);
			activityService.setCurrentActivity(ActivityType.SKILLING);
			String confirmedSkill = skillingTracker.getConfirmedSkill(currentTick, lingerTicks);
			if (confirmedSkill != null && !confirmedSkill.isEmpty())
			{
				activityService.setActivityLabel(confirmedSkill);
			}
			else if (agilityHold)
			{
				// Movement cleared the confirmed label; restore it for the hold window.
				activityService.setActivityLabel("Agility");
			}
			else if (activityService.getActivityLabel().isEmpty())
			{
				activityService.setActivityLabel("Skilling");
			}
		}
	}

	private void resolveCurrentState()
	{
		AvatarState nextState = activityService.resolveState(
			idleStateTracker.isIdle(config.idleThresholdTicks()),
			idleStateTracker.isAfkWarning(config.afkWarningThresholdTicks()));
		handleAvatarStateTransition(nextState);
		currentAvatarState = nextState;
	}

	private void handleAvatarStateTransition(AvatarState nextState)
	{
		String nextLabel = animationLabelFor(nextState);
		String nextKey = animationController.resolveAssetName(nextState, nextLabel);
		if (nextKey.equals(lastSoundAnimationKey))
		{
			lastSoundAvatarState = nextState;
			lastSoundActivityLabel = nextLabel;
			return;
		}

		if (lastSoundAvatarState != null && SoundActivation.animationEnd(config, lastSoundAvatarState))
		{
			soundController.playAnimationEnd(lastSoundAvatarState, lastSoundActivityLabel);
		}
		if (SoundActivation.animationStart(config, nextState))
		{
			boolean suppressed = soundCueGate.consumeSuppressedAnimationStart(
				nextKey, idleStateTracker.getCurrentTick());
			if (!suppressed)
			{
				soundController.playAnimationStart(nextState, nextLabel);
			}
		}

		lastSoundAnimationKey = nextKey;
		lastSoundAvatarState = nextState;
		lastSoundActivityLabel = nextLabel;
	}

	private void markActivity(ActivityType activityType)
	{
		idleStateTracker.markActivity(activityType);
		activityService.setCurrentActivity(activityType);
	}

	/**
	 * Record a confirmed skilling signal for {@code activityLabel} (a real skill
	 * name from the animation whitelist or an XP drop), refresh the linger, and
	 * mark the skilling activity. Passive XP sources are filtered upstream by
	 * {@link SkillingActivityTracker#recordSkillSignal}.
	 */
	private void markSkillingSignal(String activityLabel)
	{
		markSkillingSignal(activityLabel, false);
	}

	private void markSkillingSignal(String activityLabel, boolean confirmedXpGain)
	{
		if (confirmedXpGain)
		{
			skillingTracker.recordXpSkillSignal(activityLabel, idleStateTracker.getCurrentTick());
		}
		else
		{
			skillingTracker.recordSkillSignal(activityLabel, idleStateTracker.getCurrentTick());
		}
		markActivity(ActivityType.SKILLING);
		if (activityLabel != null && !activityLabel.isEmpty())
		{
			activityService.setActivityLabel(activityLabel);
		}
		else if (activityService.getActivityLabel().isEmpty())
		{
			activityService.setActivityLabel("Skilling");
		}
	}

	private void playGameSound(GameSoundEvent event)
	{
		if (SoundActivation.gameEvent(config, event))
		{
			soundController.playGameEvent(event);
			soundCueGate.suppressAnimationStart(
				animationKeyForGameEvent(event), idleStateTracker.getCurrentTick(), 2);
		}
	}

	private String animationKeyForGameEvent(GameSoundEvent event)
	{
		switch (event)
		{
			case INVENTORY_FULL:
				return animationController.resolveAssetName(AvatarState.INVENTORY_FULL, "");
			case LOW_HEALTH:
				return animationController.resolveAssetName(AvatarState.LOW_HEALTH, "");
			case LOW_PRAYER:
				return animationController.resolveAssetName(AvatarState.LOW_PRAYER, "");
			case GRAND_EXCHANGE:
				return animationController.resolveAssetName(AvatarState.GRAND_EXCHANGE, "");
			case TELEPORT:
				return animationController.resolveAssetName(AvatarState.TELEPORTING, "");
			case LEVEL_UP:
				return animationController.resolveAssetName(AvatarState.LEVEL_UP, "");
			case DEATH:
				return animationController.resolveAssetName(AvatarState.DEATH, "");
			case COMBAT:
				return animationController.resolveAssetName(AvatarState.COMBAT, animationLabelFor(AvatarState.COMBAT));
			case SKILLING:
				return animationController.resolveAssetName(AvatarState.SKILLING, activityService.getActivityLabel());
			default:
				return null;
		}
	}

	private String animationLabelFor(AvatarState state)
	{
		return state == AvatarState.COMBAT
			? (combatStyle == null ? "" : combatStyle)
			: activityService.getActivityLabel();
	}

	private String shortChatNotification(String matchedFilter)
	{
		if (matchedFilter == null || matchedFilter.trim().isEmpty())
		{
			return "Chat trigger";
		}
		String trimmed = matchedFilter.trim();
		if (trimmed.length() <= 42)
		{
			return trimmed;
		}
		return trimmed.substring(0, 39) + "...";
	}

	/**
	 * @return the confirmed skill name (e.g. {@code "Fishing"}) while the avatar
	 *         is in the skilling state, or {@code null} otherwise. Used by the
	 *         desktop widget to draw the matching skill icon badge.
	 */
	String getConfirmedSkill()
	{
		if (currentAvatarState != AvatarState.SKILLING)
		{
			return null;
		}
		return skillingTracker.getConfirmedSkill(idleStateTracker.getCurrentTick(), skillingLingerTicks());
	}

	/**
	 * The skilling linger actually used by detection, clamped to
	 * {@link #MAX_SKILLING_LINGER_TICKS}. The linger is both the gap-bridge for
	 * click-per-item skills and the post-stop tail; clamping it here means a large
	 * value left over in a saved profile (the most likely cause of an avatar that
	 * over-runs an activity by several animation loops, e.g. the smithing tail)
	 * cannot produce a long tail, while sustained skills still drop promptly off
	 * the live animation. NPC skills (fishing) ignore this entirely via
	 * target-based sustain.
	 */
	private int skillingLingerTicks()
	{
		return Math.max(1, Math.min(MAX_SKILLING_LINGER_TICKS, config.skillingLingerTicks()));
	}

	/**
	 * Refresh {@link #cachedXpHr} on the client thread (the widget paints on the EDT)
	 * with the per-session XP/hr for the confirmed skill, or {@code null} when not
	 * skilling. (RuneLite's XP Tracker plugin binds its service only in its own
	 * injector, so an external plugin can't read it; this is the equivalent internal
	 * calculation.)
	 */
	private void updateXpHr()
	{
		String skill = getConfirmedSkill();
		cachedXpHr = skill == null
			? null
			: XpRateTracker.formatRate(xpRateTracker.ratePerHour(skill, System.currentTimeMillis()));
	}

	/**
	 * Refresh {@link #confirmedSkillIcon} on the client thread (called from
	 * {@link #onGameTick}). {@link net.runelite.client.game.SpriteManager} loads
	 * sprites asynchronously, so the first ticks after a skill is confirmed may
	 * still yield {@code null} until the sprite is ready.
	 */
	private void updateConfirmedSkillIcon()
	{
		Integer spriteId = SkillIcons.spriteId(getConfirmedSkill());
		if (spriteId == null)
		{
			cachedSkillSpriteId = null;
			confirmedSkillIcon = null;
			return;
		}
		if (!spriteId.equals(cachedSkillSpriteId))
		{
			cachedSkillSpriteId = spriteId;
			confirmedSkillIcon = null;
		}
		if (confirmedSkillIcon == null && spriteManager != null)
		{
			confirmedSkillIcon = spriteManager.getSprite(spriteId, 0);
		}
	}

	private boolean isLikelyCombatStat(Skill skill)
	{
		return skill == Skill.ATTACK
			|| skill == Skill.STRENGTH
			|| skill == Skill.DEFENCE
			|| skill == Skill.RANGED
			|| skill == Skill.MAGIC
			|| skill == Skill.HITPOINTS;
	}

	/**
	 * @return the combat sub-style ("melee" / "ranged" / "magic") a combat XP drop
	 *         implies, or {@code null} for Hitpoints (every style grants HP XP, so it
	 *         is ambiguous and must not change the remembered style).
	 */
	private String styleForCombatSkill(Skill skill)
	{
		if (skill == Skill.ATTACK || skill == Skill.STRENGTH || skill == Skill.DEFENCE)
		{
			return "melee";
		}
		if (skill == Skill.RANGED)
		{
			return "ranged";
		}
		if (skill == Skill.MAGIC)
		{
			return "magic";
		}
		return null;
	}

	private void primeSkillXpCache()
	{
		if (!lastSkillXp.isEmpty())
		{
			return;
		}

		for (Skill skill : Skill.values())
		{
			try
			{
				lastSkillXp.put(skill, client.getSkillExperience(skill));
			}
			catch (RuntimeException ignored)
			{
				// Some synthetic enum values, if present, may not have a client XP value.
			}
		}
	}

	private boolean isActiveXpGain(StatChanged event)
	{
		Skill skill = event.getSkill();
		int xp = event.getXp();
		Integer previous = lastSkillXp.put(skill, xp);
		return previous != null && xp > previous;
	}

	/**
	 * Fire a brief LEVEL_UP flourish when a skill's real level actually increases.
	 * The first reading per skill (login stat sync) is recorded silently so the
	 * initial sync is not mistaken for a level-up.
	 */
	private void checkLevelUp(StatChanged event)
	{
		Skill skill = event.getSkill();
		int level = event.getLevel();
		Integer previous = lastSkillLevels.put(skill, level);
		if (previous != null && level > previous)
		{
			levelUpTicksRemaining = LEVEL_UP_LINGER_TICKS;
			activityService.setLevelUp(true);
			playGameSound(GameSoundEvent.LEVEL_UP);
		}
	}

	/**
	 * Fire a brief DEATH reaction when boosted hitpoints fall to 0 while logged in.
	 * Edge-detected against the previous reading so it triggers once per death.
	 */
	private void detectDeath()
	{
		int hitpoints = cachedHitpoints;
		if (lastHitpoints > 0 && hitpoints == 0)
		{
			deathTicksRemaining = DEATH_LINGER_TICKS;
			activityService.setDead(true);
			playGameSound(GameSoundEvent.DEATH);
		}
		lastHitpoints = hitpoints;
	}

	@Provides
	IdleFamiliarConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(IdleFamiliarConfig.class);
	}

	private boolean focusGameClient()
	{
		if (clientUI == null)
		{
			return false;
		}
		try
		{
			clientUI.forceFocus();
			clientUI.requestFocus();
			return true;
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to focus RuneLite client from desktop widget", ex);
			return false;
		}
	}
}
