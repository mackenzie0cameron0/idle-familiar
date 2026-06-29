package com.idlefamiliar;

import com.google.inject.Injector;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InventoryID;
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
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.xptracker.XpTrackerPlugin;
import net.runelite.client.plugins.xptracker.XpTrackerService;
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
	/** Ticks the GRAND_EXCHANGE state lingers after an offer fills. */
	private static final int GE_FILLED_LINGER_TICKS = 5;
	/** Brief trigger to enter SKILLING (Agility) after an Agility XP drop; survives movement. The one-shot latch owns playback length. */
	private static final int AGILITY_LINGER_TICKS = 2;
	/** Ticks the DEATH reaction is shown after hitpoints hit 0. */
	private static final int DEATH_LINGER_TICKS = 8;
	/** Brief trigger to enter the LEVEL_UP flourish; level_up is a one-shot key that owns its playback. */
	private static final int LEVEL_UP_LINGER_TICKS = 3;
	/** Ticks a configured chat-message trigger is shown as a custom event. */
	private static final int CHAT_MESSAGE_LINGER_TICKS = 4;
	/** OSRS bank interface group id (InterfaceID.BANK); precise open/close signal for BANKING. Stable for years. */
	private static final int BANK_GROUP_ID = 12;
	/** Sub-directory under the RuneLite home for external, user-supplied animation sheets. */
	private static final String EXTERNAL_AVATAR_DIR = "idle-familiar/avatar";

	/** Hard cap on the effective skilling linger (ticks), so a stale saved value can't make the avatar over-run an activity. */
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

	@Inject
	private PluginManager pluginManager;

	/** Primary XP/hr source (matches the XP Tracker panel). Bound only in the XP Tracker plugin's child injector, so resolved lazily via {@link #resolveXpTracker()}; null → {@link #xpRateTracker} fallback. */
	private XpTrackerService xpTrackerService;
	/** True once the "sourcing XP/hr from XP Tracker" line has been logged, to log it only once. */
	private boolean xpTrackerLogged;

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
	private final Map<Skill, Integer> lastSkillLevels = new HashMap<>();
	/** Last skill XP values, used to distinguish real XP gains from passive stat changes. */
	private final Map<Skill, Integer> lastSkillXp = new HashMap<>();

	/** Cached confirmed-skill icon, refreshed on the client thread (SpriteManager#getSprite must run there) and published to the widget. */
	private BufferedImage confirmedSkillIcon;
	/** Cached vitals (client thread); published to the widget via {@link #widgetSnapshot}. */
	private int cachedHitpoints;
	private int cachedMaxHitpoints;
	private int cachedPrayer;
	private int cachedMaxPrayer;
	/** Cached occupied inventory slot count (client thread); published via {@link #widgetSnapshot}. */
	private int cachedInventoryCount;
	/** Immutable per-tick view the widget paints from; one volatile ref so a paint sees a coherent value set. */
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
		// Runtime drop-in folder for user PNGs (no rebuild); falls back to bundled assets.
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

	/** Per-tick diagnostics (raw anim id, registry match, resolved state, target-sustain), gated behind the {@code debugState} config. */
	private void logDebugState(Player localPlayer)
	{
		if (!config.debugState() || localPlayer == null)
		{
			return;
		}
		int anim = localPlayer.getAnimation();
		// Interaction target: decisive for whether target-sustain can engage.
		net.runelite.api.Actor interacting = localPlayer.getInteracting();
		String interactingDesc = "none";
		if (interacting != null)
		{
			interactingDesc = interacting.getName()
				+ (interacting instanceof net.runelite.api.NPC
					? " npcId=" + ((net.runelite.api.NPC) interacting).getId()
					: "")
				+ " token=" + System.identityHashCode(interacting);
		}
		log.info("[idle-familiar] tick={} anim={} regLabel={} interacting=[{}] moved={} skilling={} sustained={} state={} label={}",
			idleStateTracker.getCurrentTick(),
			anim,
			activityRegistry.getActivityForAnimation(anim).orElse("-"),
			interactingDesc,
			movedThisTick,
			activityService.isSkilling(),
			skillingTracker.isSustainedByTarget(),
			currentAvatarState,
			activityService.getActivityLabel());
	}

	/** Advance the transient linger counters (teleport, GE, agility, death, level-up, chat) and mirror them onto the activity service. */
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

		// Whitelist-only: only a recognised skilling animation refreshes the linger.
		// Emotes, eating, walking and teleport wind-ups aren't in the registry.
		activityRegistry.getActivityForAnimation(animId).ifPresent(this::markSkillingSignal);

		// Surface a teleport briefly via its own state (silent — teleports are frequent).
		if (activityRegistry.isTeleportAnimation(animId))
		{
			teleportTicksRemaining = TELEPORT_LINGER_TICKS;
			activityService.setTeleporting(true);
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
	 * Arm combat only on real hit evidence: a hit you dealt ({@code isMine()}), or a hit
	 * on you while you have a target and are not skilling (guards exclude poison/venom and
	 * fishing-spot/banker interactions). {@link #combatTicksRemaining} decays in
	 * {@link #updateLingeringActivity}, refreshed by combat XP in {@link #onStatChanged}.
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

		// XP-based skilling requires a REAL XP increase; the login stat-sync and
		// level/boost-only or passive changes report activeXpGain=false (those briefly
		// showed a bogus last-synced skill). Animation-driven skilling is separate.
		String skillName = event.getSkill().getName();
		if (!activeXpGain)
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
		if (event.getContainerId() == InventoryID.INV)
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

		// Debug preview: force a chosen state/skill so art can be checked directly.
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

	/** Build and publish the immutable {@link WidgetSnapshot} the widget paints from (client thread, end of tick). The live frame is pulled separately in {@link #getCurrentFrame()}. */
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

		// Movement and skilling are mutually exclusive, so cancel the skilling linger
		// immediately (fixes the fish-then-walk false positive). Movement also closes
		// banking, in case a WidgetClosed(bank) was missed.
		skillingTracker.cancelLinger();
		activityService.setActivityLabel("");
		activityService.setBanking(false);
		markActivity(ActivityType.WALKING);

		// Walking = 1 tile/tick, running = 2; larger deltas (teleport/region load,
		// cross-plane) are not locomotion and left to the teleport handler.
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
	 * Treat a whitelisted live animation as ground truth for "skilling now". Sustained
	 * skills drop almost immediately on stop; the short linger only bridges click-per-item
	 * gaps. {@link #onAnimationChanged} also records signals, catching sub-tick one-shots.
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
		updateInventoryFull(client.getItemContainer(InventoryID.INV));
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

		// Rising edge: inventory just filled. Gathering has stopped, but target-sustain
		// keeps skilling (which outranks INVENTORY_FULL) alive, masking the reaction.
		// Cancel the linger so it shows this tick; a skill that legitimately runs full
		// (cooking) re-confirms next tick via its animation.
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

		// Target-based sustain for NPC skills (fishing, thieving): while the player keeps
		// interacting with the SAME actor a skilling animation was confirmed against,
		// skilling survives animation gaps and drops the instant the target clears. An
		// opaque identity token avoids a fishing-spot ID list. Movement already cancelled
		// the linger this tick, so a moving player can't re-confirm here.
		int targetToken = hasTarget ? System.identityHashCode(target) : SkillingActivityTracker.NO_TARGET;
		skillingTracker.recordInteractionTarget(targetToken, currentTick);

		// Combat is armed only by real hit evidence (onHitsplatApplied), not "has a target"
		// — that old heuristic mis-read bankers/fishing spots as combat. Here we just decay.
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

		// The skilling linger is refreshed ONLY by whitelisted animations and XP drops;
		// movement explicitly cancels it. The Agility hold keeps skilling alive across the
		// movement an obstacle requires, so the agility animation plays a full loop.
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

	/** Effective skilling linger, clamped to {@link #MAX_SKILLING_LINGER_TICKS} so a stale saved value can't make the avatar over-run an activity. */
	private int skillingLingerTicks()
	{
		return Math.max(1, Math.min(MAX_SKILLING_LINGER_TICKS, config.skillingLingerTicks()));
	}

	/**
	 * Refresh {@link #cachedXpHr} on the client thread (the widget paints on the EDT)
	 * with the XP/hr for the confirmed skill, or {@code null} when not skilling.
	 * Prefers RuneLite's XP Tracker service so the readout matches the XP Tracker
	 * panel exactly, falling back to the internal {@link #xpRateTracker} when that
	 * service is not reachable from this plugin's injector.
	 */
	private void updateXpHr()
	{
		String skill = getConfirmedSkill();
		cachedXpHr = skill == null ? null : XpRateTracker.formatRate(xpHrFor(skill));
	}

	/**
	 * XP/hour for {@code skill}: RuneLite's XP Tracker value when its service is
	 * available and reporting, otherwise the internal session tracker.
	 */
	private long xpHrFor(String skill)
	{
		Skill resolved = resolveSkill(skill);
		XpTrackerService service = resolveXpTracker();
		if (service != null && resolved != null)
		{
			int rate = service.getXpHr(resolved);
			if (rate > 0)
			{
				return rate;
			}
		}
		return xpRateTracker.ratePerHour(skill, System.currentTimeMillis());
	}

	/** Resolve (and cache) the {@link XpTrackerService} from the XP Tracker plugin's child injector; null if that plugin isn't started. */
	private XpTrackerService resolveXpTracker()
	{
		if (xpTrackerService != null)
		{
			return xpTrackerService;
		}
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof XpTrackerPlugin)
			{
				Injector injector = plugin.getInjector();
				if (injector != null)
				{
					try
					{
						xpTrackerService = injector.getInstance(XpTrackerService.class);
					}
					catch (RuntimeException ex)
					{
						log.debug("XpTrackerService not available from the XP Tracker plugin injector", ex);
					}
				}
				break;
			}
		}
		if (xpTrackerService != null && !xpTrackerLogged)
		{
			xpTrackerLogged = true;
			log.info("[idle-familiar] XP/hr now sourced from the XP Tracker plugin");
		}
		return xpTrackerService;
	}

	/** Map a skill label (e.g. "Woodcutting") to its {@link Skill}, or {@code null} if unknown. */
	private static Skill resolveSkill(String name)
	{
		if (name == null)
		{
			return null;
		}
		try
		{
			return Skill.valueOf(name.toUpperCase(Locale.ROOT).replace(' ', '_'));
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}

	/** Refresh {@link #confirmedSkillIcon} on the client thread; SpriteManager loads async, so it may be null for the first few ticks. */
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
