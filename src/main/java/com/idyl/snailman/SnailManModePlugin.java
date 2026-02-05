package com.idyl.snailman;

import com.google.inject.Provides;
import com.idyl.snailman.pathfinder.CollisionMap;
import com.idyl.snailman.pathfinder.Pathfinder;
import com.idyl.snailman.pathfinder.PathfinderConfig;
import com.idyl.snailman.pathfinder.SplitFlagMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

import javax.inject.Inject;
import javax.inject.Named;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.idyl.snailman.SnailManModeConfig.*;

@Slf4j
@PluginDescriptor(
    name = "SnailMan Mode"
)
public class SnailManModePlugin extends Plugin {
    private static final int RECALCULATION_THRESHOLD = 20;
    private static final int SNAIL_HORROR_SOUND = 1899;
    private static final int SNAIL_HORROR_DISTANCE = 15;
    private static final String ADD_START = "Add start";
    private static final String ADD_END = "Add end";
    private static final String MOVE_SNAIL = "Move";
    private static final String SNAIL = ColorUtil.wrapWithColorTag("Snail", JagexColors.MENU_TARGET);
    private static final String WALK_HERE = "Walk here";
    private static final String TRANSPORT = ColorUtil.wrapWithColorTag("Transport", JagexColors.MENU_TARGET);
    private static final WorldPoint DEFAULT_SNAIL_START = new WorldPoint(1181, 3624, 0);
    private static final int SNARE_SPOT_ANIM_ID = "snailmanmode.snare".hashCode();
    private static boolean horrorCloseFlag = false;
    public Pathfinder pathfinder;
    public PathfinderConfig pathfinderConfig;

    @Inject
    @Named("developerMode")
    boolean developerMode;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private SnailManModeOverlay snailManModeOverlay;

    @Inject
    private SnailManModeMapOverlay snailManModeMapOverlay;

    @Inject
    private Client client;

    @Inject
    private SnailManModeConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private WorldService worldService;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private InfoBoxManager infoBoxManager;

    @Inject
    private ItemManager itemManager;

    @Getter
    private WorldPoint snailWorldPoint;

    private NavigationButton navButton;
    private int currentPathIndex;
    private int snailmanIconOffset = -1;
    private boolean onSeasonalWorld;
    private boolean isLoggedIn;
    private boolean isAlive;
    private boolean isDying;
    private WorldPoint deathPoint;
    private WorldPoint transportStart;
    private MenuEntry lastClick;
    private long lastSaveTime;
    private int tickCount = 0;
    private WorldPoint lastPlayerPoint;
    private RuneLiteObject snailObject;
    private Model snailModel;
    private Animation snailAnimation;
    private boolean wasInScene;

    private static String getImgTag(int iconIndex) {
        return "<img=" + iconIndex + ">";
    }

    @Provides
    SnailManModeConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(SnailManModeConfig.class);
    }

    @Override
    protected void startUp() {
        this.loadResources();
        this.isLoggedIn = false;
        this.onSeasonalWorld = false;
        this.isAlive = true;
        this.isDying = false;
        this.lastSaveTime = Instant.EPOCH.getEpochSecond();
        this.overlayManager.add(this.snailManModeOverlay);
        this.overlayManager.add(this.snailManModeMapOverlay);

        this.clientThread.invokeLater(() -> {
            this.snailModel = this.client.loadModel(4108);
            this.snailAnimation = this.client.loadAnimation(AnimationID.SNAIL_WALK);
        });

        final BufferedImage icon = ImageUtil.loadImageResource(SnailManModePlugin.class, "/snail.png");

        SnailManModePanel panel = this.injector.getInstance(SnailManModePanel.class);
        this.navButton = NavigationButton.builder()
            .panel(panel)
            .tooltip("SnailMan Mode")
            .icon(icon)
            .priority(90)
            .build();

        this.clientToolbar.addNavigation(this.navButton);
    }

    @Override
    protected void shutDown() {
        this.overlayManager.remove(this.snailManModeOverlay);
        this.overlayManager.remove(this.snailManModeMapOverlay);
        this.clientToolbar.removeNavigation(this.navButton);
        this.infoBoxManager.removeIf(t -> t instanceof TeleportTimer);
        this.destroySnailObject();
        this.saveData();
    }

    public void testDeathAnim() {
        this.clientThread.invokeLater(() -> {
            this.isDying = true;
            this.deathPoint = this.client.getLocalPlayer().getWorldLocation();
            this.client.playSoundEffect(SoundEffectID.ATTACK_HIT);
            this.client.getLocalPlayer().setAnimation(AnimationID.HUMAN_DEATH);
            this.client.getLocalPlayer().setAnimationFrame(0);
            this.client.getLocalPlayer().createSpotAnim(SNARE_SPOT_ANIM_ID, SpotanimID.SNARE_IMPACT, 30, 0);

//            LocalPoint localLocation = client.getLocalPlayer().getLocalLocation();
//            localLocation = localLocation.plus(256, 256);
//            snailObject.setLocation(localLocation, 0);
        });
    }

    private void addSnailmanIcon(ChatMessage chatMessage) {
        if (!this.isAlive) return;

        String name = chatMessage.getName();

        boolean isLocalPlayer = Text.standardize(name).equalsIgnoreCase(Text.standardize(this.client.getLocalPlayer().getName()));

        if (!isLocalPlayer) return;

        chatMessage.getMessageNode().setName(getImgTag(this.snailmanIconOffset) + Text.removeTags(name));
    }

    private WorldPoint getSavedSnailWorldPoint() {
        if (this.configManager.getRSProfileKey() == null) return null;

        final WorldPoint point = this.configManager.getRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_SNAIL_LOC, WorldPoint.class);

        if (point == null) {
            return DEFAULT_SNAIL_START;
        }

        return point;
    }

    private void saveSnailWorldPoint() {
        if (this.configManager.getRSProfileKey() == null) return;

        this.configManager.setRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_SNAIL_LOC, this.snailWorldPoint);
    }

    private void saveData() {
        System.out.println("saving data");
        System.out.println(this.configManager.getRSProfileKey());
        if (this.configManager.getRSProfileKey() == null) return;

        this.saveSnailWorldPoint();
        this.configManager.setRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_IS_ALIVE, this.isAlive);

        this.lastSaveTime = Instant.EPOCH.getEpochSecond();
    }

    public void reset() {
        this.clientThread.invoke(() -> {
            this.snailObject.setActive(false);
            this.setSnailWorldPoint(DEFAULT_SNAIL_START);
        });
        this.pathfinder = null;
        this.isAlive = true;
        this.isDying = false;
        this.deathPoint = null;
        this.wasInScene = false;
        horrorCloseFlag = false;
        this.client.getLocalPlayer().setAnimation(-1);
        this.client.getLocalPlayer().removeSpotAnim(SNARE_SPOT_ANIM_ID);
        this.saveData();
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (this.client.isKeyPressed(KeyCode.KC_SHIFT) && event.getOption().equals(WALK_HERE) && this.developerMode) {
            WorldView wv = this.client.getWorldView(event.getMenuEntry().getWorldViewId());
            if (wv == null) {
                return;
            }
            final Tile selectedSceneTile = wv.getSelectedSceneTile();
            if (selectedSceneTile == null) {
                return;
            }
            final WorldPoint worldPoint = WorldPoint.fromLocalInstance(this.client, selectedSceneTile.getLocalLocation());
            this.addMenuEntry(event, ADD_START, TRANSPORT, worldPoint);
            this.addMenuEntry(event, ADD_END, TRANSPORT, worldPoint);
            this.addMenuEntry(event, MOVE_SNAIL, SNAIL, worldPoint);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN && !this.isLoggedIn) {
            String savedAlive = this.configManager.getRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_IS_ALIVE);
            this.currentPathIndex = 1;
            this.isAlive = savedAlive == null || Boolean.parseBoolean(savedAlive);
            this.isLoggedIn = true;
            this.onSeasonalWorld = this.isSeasonalWorld(this.client.getWorld());

            this.clientThread.invoke(() -> {
                final WorldPoint point = this.getSavedSnailWorldPoint();
                this.setSnailWorldPoint(point);

                if (this.config.visualMode() == SnailManModeConfig.SnailVisualMode.MODEL) {
                    this.createSnailObject();
                }
            });
        } else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN && this.isLoggedIn) {
            this.isLoggedIn = false;
            this.saveData();
            this.pathfinder = null;
            if (this.config.visualMode() == SnailManModeConfig.SnailVisualMode.MODEL) {
                this.destroySnailObject();
            }
        } else if (gameStateChanged.getGameState() == GameState.LOADING) {
            this.clientThread.invokeLater(this::createSnailObject);
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
//        log.debug("game tick");
        if (!this.isLoggedIn || this.isSeasonalWorld(this.client.getWorld())) return;

        WorldPoint playerPoint = this.client.getLocalPlayer().getWorldLocation();

        if (this.isDying) {
            if (!this.deathPoint.equals(playerPoint)) {
                this.isDying = false;
                // need to set to an actual animation to "finish" the death animation
                // idle doesn't set some flag so when running death again it starts at the end
                this.client.getLocalPlayer().setAnimation(AnimationID.HUMAN_DANCING);
//				client.getLocalPlayer().setAnimation(-1);
            }
        }

        this.handleSnail(playerPoint);

        this.lastPlayerPoint = playerPoint;
        this.wasInScene = WorldPoint.isInScene(this.client.getLocalPlayer().getWorldView(), this.snailWorldPoint.getX(), this.snailWorldPoint.getY());
//        log.debug("wasInScene: {}", this.wasInScene);

        if (Instant.EPOCH.getEpochSecond() - this.lastSaveTime >= 60 && this.isLoggedIn) {
            this.saveData();
        }
    }

    private void handleSnail(WorldPoint playerPoint) {
        if (!this.wasInScene && WorldPoint.isInScene(this.client.getLocalPlayer().getWorldView(), this.snailWorldPoint.getX(), this.snailWorldPoint.getY())) {
            log.debug("entered scene");
            this.createSnailObject();
        }

        if (this.config.pauseSnail()) {
            return;
        }

        this.tickCount++;

        long distanceToSnail = playerPoint.distanceTo2D(this.snailWorldPoint);
        boolean snailShouldMove = this.tickCount % this.config.moveSpeed() == 0 || (distanceToSnail <= RECALCULATION_THRESHOLD && this.config.speedBoost());

        if (this.lastPlayerPoint == null) this.lastPlayerPoint = playerPoint;

        if (!snailShouldMove) return;

        if (this.pathfinder == null) {
            this.calculatePath(this.snailWorldPoint, playerPoint, false, false);
            return;
        }

        if (this.currentPathIndex < this.pathfinder.getPath().size() && this.pathfinder.isComplete) {
            int index = Math.max(this.pathfinder.getPath().size() - 1 - this.currentPathIndex, 0);
            WorldPoint target = this.pathfinder.getPath().get(index);
            this.setSnailWorldPoint(target);
            this.currentPathIndex++;
        }

        if (this.config.horrorMode()) {
            this.performHorrorModeChecks(distanceToSnail);
        }

        if (this.checkTouching()) {
            log.debug("touching!");
            final ChatMessageBuilder message = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append("You have been touched by the snail. You are dead.");

            log.debug("isAlive: {}", this.isAlive);
            if (this.isAlive) {
                this.isDying = true;
                this.deathPoint = playerPoint;
                this.chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.GAMEMESSAGE)
                    .runeLiteFormattedMessage(message.build())
                    .build());

                this.client.playSoundEffect(SoundEffectID.ATTACK_HIT);

                this.client.getLocalPlayer().setAnimation(AnimationID.HUMAN_DEATH);
                this.client.getLocalPlayer().setAnimationFrame(0);
                this.client.getLocalPlayer().createSpotAnim(SNARE_SPOT_ANIM_ID, SpotanimID.SNARE_IMPACT, 0, 0);

                this.isAlive = false;
                this.clientThread.invoke(() -> this.client.runScript(ScriptID.CHAT_PROMPT_INIT));
                this.saveData();
            }
        }
        this.recalculatePath();
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (this.client.getGameState() != GameState.LOADING && this.client.getGameState() != GameState.LOGGED_IN) {
            return;
        }

        if (this.client.getLocalPlayer().getName() == null) return;

        String name = Text.removeTags(chatMessage.getName());
        boolean isSelf = this.client.getLocalPlayer().getName().equalsIgnoreCase(name);

        switch (chatMessage.getType()) {
            case PRIVATECHAT:
            case MODPRIVATECHAT:
            case FRIENDSCHAT:
            case CLAN_CHAT:
                if (!this.onSeasonalWorld && isSelf) {
                    this.addSnailmanIcon(chatMessage);
                }
                break;
            case PUBLICCHAT:
            case MODCHAT:
                if (isSelf) {
                    this.addSnailmanIcon(chatMessage);
                }
                break;
        }
    }

    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent event) {
        if (!event.getEventName().equals("setChatboxInput")) {
            return;
        }

        this.updateChatbox();
    }

    @Subscribe
    public void onBeforeRender(BeforeRender event) {
        this.updateChatbox();
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (event.getActor() instanceof Player) {
            final Player player = (Player) event.getActor();

            if (this.isDying) {
                this.isDying = false;
                this.client.getLocalPlayer().setAnimation(-1);
                this.client.getLocalPlayer().removeSpotAnim(SNARE_SPOT_ANIM_ID);
            }
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        if (configChanged.getGroup().equals(CONFIG_GROUP)) {
            if (configChanged.getKey().equals(CONFIG_KEY_VISUAL_MODE)) {
                if (this.config.visualMode() == SnailManModeConfig.SnailVisualMode.MODEL) {
                    this.createSnailObject();
                } else {
                    if (this.snailObject != null) {
                        this.destroySnailObject();
                    }
                }
            }
        }
    }

    private void createSnailObject() {
        this.clientThread.invoke(() -> {
            if (this.snailObject != null) {
                this.snailObject.setActive(false);
            }
            this.snailObject = this.client.createRuneLiteObject();
            this.snailObject.setModel(this.snailModel);
            this.snailObject.setAnimation(this.snailAnimation);
            this.snailObject.setActive(true);
            this.setSnailWorldPoint(this.snailWorldPoint);
        });
    }

    private void destroySnailObject() {
        this.clientThread.invoke(() -> {
            if (this.snailObject != null) {
                this.snailObject.setActive(false);
            }
            this.snailObject = null;
        });
    }

    private void setSnailWorldPoint(WorldPoint worldPoint) {
        if (worldPoint == null) return;
        WorldPoint previousLocation = this.snailWorldPoint;
        this.snailWorldPoint = worldPoint;

        if (this.snailObject == null) return;
        LocalPoint localPoint = LocalPoint.fromWorld(this.client, worldPoint);
//        log.debug("setting snail world point to {} {}", worldPoint, localPoint);
        if (localPoint == null) return;
//        log.debug("{}", this.snailObject);
        this.snailObject.setLocation(localPoint, worldPoint.getPlane());
        this.snailObject.setActive(true);

        if (previousLocation == null) return;
        double angle = Math.atan2(previousLocation.getX() - worldPoint.getX(), previousLocation.getY() - worldPoint.getY());
        int jau = (int) Math.round(angle / Perspective.UNIT) & 2047;
        this.snailObject.setOrientation(jau);
    }

    private void addMenuEntry(MenuEntryAdded event, String option, String target, WorldPoint worldPoint) {
        this.client.getMenu().createMenuEntry(1)
            .setOption(option)
            .setTarget(target)
            .setParam0(event.getActionParam0())
            .setParam1(event.getActionParam1())
            .setIdentifier(event.getIdentifier())
            .setType(MenuAction.RUNELITE)
            .onClick(e -> this.onMenuOptionClicked(e, worldPoint));
    }

    private void onMenuOptionClicked(MenuEntry entry, WorldPoint worldPoint) {
        Player localPlayer = this.client.getLocalPlayer();

        WorldPoint currentLocation = localPlayer.getWorldLocation();
        if (entry.getOption().equals(ADD_START) && entry.getTarget().equals(TRANSPORT)) {
            this.transportStart = currentLocation;
        }

        if (entry.getOption().equals(ADD_END) && entry.getTarget().equals(TRANSPORT)) {
            WorldPoint transportEnd = this.client.getLocalPlayer().getWorldLocation();
            String transportText = this.transportStart.getX() + " " + this.transportStart.getY() + " " + this.transportStart.getPlane() + "\t" +
                currentLocation.getX() + " " + currentLocation.getY() + " " + currentLocation.getPlane() + "\t" +
                this.lastClick.getOption() + " " + Text.removeTags(this.lastClick.getTarget()) + " " + this.lastClick.getIdentifier();
            System.out.println(transportText);
            Pathfinder.writeTransportToFile(transportText);
            Transport transport = new Transport(this.transportStart, transportEnd);
            this.pathfinderConfig.getTransports().computeIfAbsent(this.transportStart, k -> new ArrayList<>()).add(transport);
        }

        if (entry.getOption().equals(MOVE_SNAIL) && entry.getTarget().equals(SNAIL)) {
            log.debug("entry: {}", worldPoint);
            this.snailWorldPoint = worldPoint;
            log.debug("force recalculating path");
            this.recalculatePath(true);
            this.createSnailObject();
//			calculatePath(snailWorldPoint, lastPlayerPoint, true, false);
        }

        if (entry.getType() != MenuAction.WALK) {
            this.lastClick = entry;
        }
    }

    private void performHorrorModeChecks(long distanceToSnail) {
        if (this.config.pauseSnail() || !this.isAlive) return;

        if (distanceToSnail < SNAIL_HORROR_DISTANCE && !horrorCloseFlag) {
            horrorCloseFlag = true;
            final ChatMessageBuilder message = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append("You see something moving in the fog...");

            this.chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(message.build())
                .build());

            TeleportTimer timer = new TeleportTimer(
                Duration.of(10, ChronoUnit.SECONDS),
                this.itemManager.getImage(ItemID.SNELM_ROUND_SWAMP),
                this
            );
            this.infoBoxManager.addInfoBox(timer);

            this.client.playSoundEffect(SNAIL_HORROR_SOUND);
        } else if (distanceToSnail > 30 && horrorCloseFlag) {
            horrorCloseFlag = false;
        }
    }

    private void updateChatbox() {
        if (!this.isAlive) {
            return;
        }

        Widget chatboxTypedText = this.client.getWidget(InterfaceID.Chatbox.INPUT);

        if (this.snailmanIconOffset == -1) {
            return;
        }

        if (chatboxTypedText == null || chatboxTypedText.isHidden()) {
            return;
        }

        String[] chatbox = chatboxTypedText.getText().split(":", 2);
        String rsn = Objects.requireNonNull(this.client.getLocalPlayer()).getName();

        chatboxTypedText.setText(getImgTag(this.snailmanIconOffset) + Text.removeTags(rsn) + ":" + chatbox[1]);
    }

    private boolean isSeasonalWorld(int worldNumber) {
        WorldResult worlds = this.worldService.getWorlds();
        if (worlds == null) {
            return false;
        }

        World world = worlds.findWorld(worldNumber);
        return world != null && world.getTypes().contains(WorldType.SEASONAL);
    }

    private void recalculatePath() {
        this.recalculatePath(false);
    }

    private void recalculatePath(boolean force) {
        if (!this.isLoggedIn) return;

        if (this.client.getLocalPlayer().getWorldView().isInstance()) {
            this.pathfinder = null;
            return;
        }

        WorldPoint playerPoint = this.client.getLocalPlayer().getWorldLocation();
        final int distanceFromPlayer = this.snailWorldPoint.distanceTo2D(playerPoint);
//		log.debug("distanceFromPlayer: {}", distanceFromPlayer);

        boolean forceRecalc = force || this.lastPlayerPoint.getPlane() != playerPoint.getPlane();
//		log.debug("forceRecalc: {}", forceRecalc);

//		log.debug("distanceFromPlayer < RECALCULATION_THRESHOLD: {}", distanceFromPlayer < RECALCULATION_THRESHOLD);
        if (distanceFromPlayer < RECALCULATION_THRESHOLD) {
//			log.debug("start: {}", pathfinder.getStart());
//			log.debug("distance from start: {}", pathfinder.getStart().distanceTo2D(playerPoint));
            if (force || this.pathfinder.getStart().distanceTo2D(playerPoint) > 0) {
//				log.debug("force calculating path");
                this.calculatePath(this.snailWorldPoint, playerPoint, true, false);
                this.currentPathIndex = 1;
            }
        } else {
//			log.debug("not below recalculation threshold");
            // Limit number of recalculations done during player movement
//			log.debug("{}", pathfinder.getStart().distanceTo2D(playerPoint) >= RECALCULATION_THRESHOLD || forceRecalc);
            if (this.pathfinder.getStart().distanceTo2D(playerPoint) >= RECALCULATION_THRESHOLD || forceRecalc) {
                boolean useExistingPath = this.lastPlayerPoint.distanceTo(playerPoint) < 100;
//				log.debug("useExistingPath: {}", useExistingPath);
                this.calculatePath(this.snailWorldPoint, playerPoint, forceRecalc, useExistingPath);
                this.currentPathIndex = 1;
            }
        }
    }

    private boolean checkTouching() {
        WorldPoint playerPoint = this.client.getLocalPlayer().getWorldLocation();
        final int distanceFromPlayer = this.snailWorldPoint.distanceTo2D(playerPoint);

        return distanceFromPlayer <= 0;
    }

    private void calculatePath(WorldPoint start, WorldPoint end, boolean force, boolean useExisting) {
        if (this.pathfinder != null && !this.pathfinder.isDone() && !force) return;

        if (this.client.getLocalPlayer().getWorldView().isInstance()) return;

        List<WorldPoint> existingPath = useExisting ? this.pathfinder.getPath() : null;

        this.pathfinder = new Pathfinder(this.pathfinderConfig, end, start, existingPath);

    }

    public Point mapWorldPointToGraphicsPoint(WorldPoint worldPoint) {
        WorldMap worldMap = this.client.getWorldMap();

        float pixelsPerTile = worldMap.getWorldMapZoom();

        Widget map = this.client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
        if (map != null) {
            Rectangle worldMapRect = map.getBounds();

            int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);
            int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);

            Point worldMapPosition = worldMap.getWorldMapPosition();

            int yTileMax = worldMapPosition.getY() - heightInTiles / 2;
            int yTileOffset = (yTileMax - worldPoint.getY() - 1) * -1;
            int xTileOffset = worldPoint.getX() + widthInTiles / 2 - worldMapPosition.getX();

            int xGraphDiff = ((int) (xTileOffset * pixelsPerTile));
            int yGraphDiff = (int) (yTileOffset * pixelsPerTile);

            yGraphDiff -= (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));
            xGraphDiff += (int) (pixelsPerTile - Math.ceil(pixelsPerTile / 2));

            yGraphDiff = worldMapRect.height - yGraphDiff;
            yGraphDiff += (int) worldMapRect.getY();
            xGraphDiff += (int) worldMapRect.getX();

            return new Point(xGraphDiff, yGraphDiff);
        }
        return null;
    }

    private void loadResources() {
        Map<SplitFlagMap.Position, byte[]> compressedRegions = new HashMap<>();
        HashMap<WorldPoint, List<Transport>> transports = new HashMap<>();

        try (ZipInputStream in = new ZipInputStream(SnailManModePlugin.class.getResourceAsStream("/collision-map.zip"))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String[] n = entry.getName().split("_");

                compressedRegions.put(
                    new SplitFlagMap.Position(Integer.parseInt(n[0]), Integer.parseInt(n[1])),
                    Util.readAllBytes(in)
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            String s = new String(Util.readAllBytes(SnailManModePlugin.class.getResourceAsStream("/transports.txt")), StandardCharsets.UTF_8);
            Scanner scanner = new Scanner(s);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }

                Transport transport = new Transport(line);
                WorldPoint origin = transport.getOrigin();
                transports.computeIfAbsent(origin, k -> new ArrayList<>()).add(transport);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.pathfinderConfig = new PathfinderConfig(new CollisionMap(64, compressedRegions), transports, this.client);

        final IndexedSprite[] modIcons = this.client.getModIcons();

        if (this.snailmanIconOffset != -1 || modIcons == null) {
            return;
        }

        BufferedImage image = ImageUtil.loadImageResource(this.getClass(), "/helm.png");
        IndexedSprite indexedSprite = ImageUtil.getImageIndexedSprite(image, this.client);
        this.snailmanIconOffset = modIcons.length;

        final IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + 1);
        newModIcons[newModIcons.length - 1] = indexedSprite;

        this.client.setModIcons(newModIcons);
    }
}
