package dev.lungetest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

/** Azeium client modules. Spear input stays manual; Auto Clutch is an independent item-use helper. */
public final class LungeTestClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("Azeium");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("azeium.json");
    private static final SpearMetrics METRICS = new SpearMetrics();
    private static Settings settings = new Settings();
    private static ClientPacketListener armedConnection;
    private static ClientPacketListener metricsConnection;
    private static ClientLevel metricsLevel;
    private static boolean enabled;
    private static Path lastExport;

    public enum HudMode {
        CLICKS("Clicks"), TRAVEL("Travel"), BOTH("Both");
        private final String label;
        HudMode(String label) { this.label = label; }
        public String label() { return label; }
    }
    public enum HudCorner {
        TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right");
        private final String label;
        HudCorner(String label) { this.label = label; }
        public String label() { return label; }
    }

    @Override public void onInitializeClient() {
        try {
            if (Files.isRegularFile(settingsPath())) {
                Settings loaded = GSON.fromJson(Files.readString(settingsPath()), Settings.class);
                if (loaded != null) settings = loaded;
            }
        } catch (Exception e) { LOG.warn("Could not load Azeium preferences; using defaults", e); }
        if (settings.mode == null) settings.mode = HudMode.TRAVEL;
        if (settings.corner == null) settings.corner = HudCorner.TOP_LEFT;
        PlayerHighlightModule.setColor(settings.highlightColor);
        PlayerHighlightModule.setRange(settings.highlightRange);
        PlayerTracerModule.setColor(settings.tracerColor);
        PlayerTracerModule.setRange(settings.tracerRange);
        boolean migrateClutch = settings.clutchRevision < 2;
        if (migrateClutch || settings.clutchMode == null) settings.clutchMode = AutoClutchModule.Mode.BOTH;
        settings.clutchRevision = 2;
        AutoClutchModule.setMode(settings.clutchMode);
        if (settings.tellySpeed == null) settings.tellySpeed = AutoClutchModule.TellySpeed.FAST;
        AutoClutchModule.setTellySpeed(settings.tellySpeed);
        ZoomModule.setFactor(settings.zoomFactor);
        if (settings.uiOpacity != 55 && settings.uiOpacity != 70 && settings.uiOpacity != 85) settings.uiOpacity = 70;
        AzeiumKeybinds.initialize(settings.moduleKeys, settings.menuKey);
        CombatAssistModule.initialize(settings.combat);
        AutoRejoinModule.initialize(settings.rejoinKey);
        SpearQuickLungeModule.initialize(settings.quickLungeKey, settings.spearSlot);
        ArmorAlertsModule.initialize(settings.armorThreshold);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING
                .register(client -> CombatTestLog.flush());
        ThemeManager.initialize(settings.theme);
        ShieldDrainerModule.initialize(settings.shieldTest);
        METRICS.reset(System.nanoTime());
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (enabled && (client.getConnection() != armedConnection || client.player == null
                    || !client.player.isAlive())) disable();
            syncMetricsContext(client);
            while (AzeiumKeybinds.consumeMenuClick()) {
                if (client.screen instanceof LungeTestScreen) client.setScreen(null);
                else if (client.screen == null || client.screen instanceof TitleScreen)
                    client.setScreen(new LungeTestScreen());
            }
            AutoClutchModule.tick(client);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AutoRejoinModule.tick(client);
            SpearQuickLungeModule.tick(client);
            CombatAssistModule.tick(client);
            ShieldDrainerModule.tick(client);
            CombatTestLog.tick();
            SessionHudModule.tick(client);
            if (!syncMetricsContext(client) || !client.player.isAlive() || client.isPaused()
                    || !client.isWindowActive()) {
                METRICS.clearMovementAnchor();
                return;
            }
            var position = client.player.position();
            METRICS.sampleMovement(System.nanoTime(), position.x, position.z);
        });
        PlayerTracerModule.register();
        SpearometerHud.register();
        CoordinatesModule.register();
        ArmorAlertsModule.register();
        SessionHudModule.register();
        PerformanceHudModule.register();
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> CombatTestLog.observation(message.getString()));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.CHAT.register(
                (message, signed, sender, parameters, received) -> CombatTestLog.observation(message.getString()));
        LOG.info("Azeium 2.4.0 loaded; the menu key opens modules. Auto Telly can jump, turn and bridge automatically; gameplay helpers start OFF.");
    }

    public static boolean isSpear(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("minecraft") && id.getPath().endsWith("_spear");
    }
    public static boolean active(Minecraft client) {
        return enabled && client.player != null && client.getConnection() != null
                && client.getConnection() == armedConnection;
    }
    public static boolean skipLocalGate() { return active(Minecraft.getInstance()); }
    public static boolean enabled() { return enabled; }
    public static boolean menuMatches(KeyEvent key) { return AzeiumKeybinds.menuMatches(key); }
    public static SpearMetrics.Snapshot stats() { return METRICS.snapshot(System.nanoTime()); }
    public static long attempts() { return stats().attempts(); }
    public static long dispatched() { return stats().started(); }

    /** Observes vanilla returns, including OFF baselines; never generates clicks or packets. */
    public static void recordManualAttack(Minecraft client, boolean started) {
        if (!syncMetricsContext(client) || !isSpear(client.player.getMainHandItem())) return;
        METRICS.recordAttack(System.nanoTime(), started);
        if (attempts() % 100 == 0) LOG.info("Manual spear progress: attempts={}, locallyStarted={}, cooldownRemoval={}",
                attempts(), dispatched(), enabled);
    }
    /** Observes ordinary server position updates without changing or cancelling them. */
    public static void recordPositionSync(ClientPacketListener connection) {
        var client = Minecraft.getInstance();
        if (client.getConnection() == connection && syncMetricsContext(client)) METRICS.recordPositionSync();
    }
    private static boolean syncMetricsContext(Minecraft client) {
        if (client.player == null || client.level == null || client.getConnection() == null) {
            metricsConnection = null;
            metricsLevel = null;
            METRICS.clearMovementAnchor();
            return false;
        }
        if (metricsConnection != client.getConnection() || metricsLevel != client.level) {
            METRICS.reset(System.nanoTime());
            metricsConnection = client.getConnection();
            metricsLevel = client.level;
        }
        return true;
    }

    public static boolean hudEnabled() { return settings.hud; }
    public static HudMode hudMode() { return settings.mode; }
    public static HudCorner hudCorner() { return settings.corner; }
    public static void toggleHud() { settings.hud = !settings.hud; saveSettings(); }
    public static void cycleHudMode() {
        settings.mode = HudMode.values()[(settings.mode.ordinal() + 1) % HudMode.values().length];
        saveSettings();
    }
    public static void cycleHudCorner() {
        settings.corner = HudCorner.values()[(settings.corner.ordinal() + 1) % HudCorner.values().length];
        saveSettings();
    }
    public static void resetStats() { METRICS.reset(System.nanoTime()); }
    public static Path lastExport() { return lastExport; }
    public static String exportStats(Minecraft client) {
        lastExport = null;
        try {
            var folder = FabricLoader.getInstance().getGameDir().resolve("logs/spearometer");
            Files.createDirectories(folder);
            var report = new JsonObject();
            report.addProperty("modVersion", "2.4.0");
            report.addProperty("minecraftVersion", "1.21.11");
            report.addProperty("exportedAt", Instant.now().toString());
            report.addProperty("measurementSource", "client");
            report.addProperty("cooldownRemovalEnabled", active(client));
            report.addProperty("hudMode", hudMode().name());
            report.addProperty("horizontalTravel", true);
            report.addProperty("serverAcceptanceMeasured", false);
            report.add("snapshot", GSON.toJsonTree(stats()));
            var target = folder.resolve("spearometer-" + UUID.randomUUID() + ".json");
            Files.writeString(target, GSON.toJson(report));
            lastExport = target;
            LOG.info("Spearometer snapshot saved to {}", target);
            return "Saved to logs/spearometer";
        } catch (Exception e) {
            LOG.warn("Could not save spearometer snapshot", e);
            return "Export failed; see client log";
        }
    }
    private static Path settingsPath() {
        if (Files.isRegularFile(CONFIG)) return CONFIG;
        return CONFIG.resolveSibling("spearometer.json");
    }
    public static void setHighlightColor(int argb) {
        PlayerHighlightModule.setColor(argb);
        settings.highlightColor = PlayerHighlightModule.color();
        saveSettings();
    }
    public static int uiOpacity() { return settings.uiOpacity; }
    public static void cycleUiOpacity() {
        settings.uiOpacity = settings.uiOpacity == 55 ? 70 : settings.uiOpacity == 70 ? 85 : 55;
        saveSettings();
    }
    public static void cycleHighlightRange() {
        settings.highlightRange = nextRange(PlayerHighlightModule.range());
        PlayerHighlightModule.setRange(settings.highlightRange);
        saveSettings();
    }
    public static void cycleTracerRange() {
        settings.tracerRange = nextRange(PlayerTracerModule.range());
        PlayerTracerModule.setRange(settings.tracerRange);
        saveSettings();
    }
    private static int nextRange(int current) {
        return current < 128 ? 128 : current < 256 ? 256 : current < 512 ? 512 : current < 1024 ? 1024 : 128;
    }
    public static void setTracerColor(int argb) {
        PlayerTracerModule.setColor(argb);
        settings.tracerColor = PlayerTracerModule.color();
        saveSettings();
    }
    public static void cycleClutchMode() {
        var modes = AutoClutchModule.Mode.values();
        settings.clutchMode = modes[(AutoClutchModule.mode().ordinal() + 1) % modes.length];
        AutoClutchModule.setMode(settings.clutchMode);
        saveSettings();
    }
    public static void cycleTellySpeed() {
        AutoClutchModule.cycleTellySpeed();
        settings.tellySpeed = AutoClutchModule.tellySpeed();
        saveSettings();
    }
    public static void saveKeybinds() {
        settings.moduleKeys = AzeiumKeybinds.snapshot();
        settings.menuKey = AzeiumKeybinds.menuKey();
        saveSettings();
    }
    public static void saveRejoinKey() {
        settings.rejoinKey = AutoRejoinModule.key();
        saveSettings();
    }
    public static void saveSpearPreferences() {
        settings.quickLungeKey = SpearQuickLungeModule.key();
        settings.spearSlot = SpearQuickLungeModule.preferredSlot();
        saveSettings();
    }
    public static void saveArmorPreferences() {
        settings.armorThreshold = ArmorAlertsModule.threshold();
        saveSettings();
    }
    public static void saveCombatPreferences() {
        settings.combat = CombatAssistModule.snapshot();
        saveSettings();
    }
    public static void saveThemePreferences() {
        settings.theme = ThemeManager.snapshot();
        saveSettings();
    }
    public static void saveTestPreferences() {
        settings.shieldTest = ShieldDrainerModule.preferences();
        saveSettings();
    }
    private static void saveSettings() {
        try {
            Files.createDirectories(CONFIG.getParent());
            Files.writeString(CONFIG, GSON.toJson(settings));
        } catch (Exception e) { LOG.warn("Could not save Azeium preferences", e); }
    }
    public static void saveZoomFactor() {
        settings.zoomFactor = ZoomModule.factor();
        saveSettings();
    }
    public static String activity(Minecraft client) {
        if (!enabled) return "OFF: normal vanilla cooldown";
        if (!active(client)) return "No active session";
        if (!isSpear(client.player.getMainHandItem())) return "ON: hold a spear to use it";
        return "ON: click manually at your own pace";
    }
    public static void toggle(Minecraft client) {
        if (enabled) { disable(); return; }
        if (client.player == null || client.getConnection() == null || !client.player.isAlive()) return;
        armedConnection = client.getConnection();
        enabled = true;
        LOG.info("Manual spear cooldown removal ON; attacks remain controlled by your mouse clicks");
    }
    public static void disable() {
        SpearQuickLungeModule.cancel();
        if (enabled) LOG.info("Manual spear cooldown removal OFF: {} attempts, {} locally started", attempts(), dispatched());
        enabled = false;
        armedConnection = null;
    }
    private static final class Settings {
        int highlightColor = 0xFF69E4EF;
        int highlightRange = 256;
        int tracerColor = 0xFF69E4EF;
        int tracerRange = 256;
        int uiOpacity = 70;
        int menuKey = 344;
        int rejoinKey = -1;
        int quickLungeKey = -1;
        int spearSlot = -1;
        int armorThreshold = 20;
        Map<String, Integer> moduleKeys = new LinkedHashMap<>();
        Map<String, CombatAssistModule.Preferences> combat = new LinkedHashMap<>();
        ThemeManager.Preferences theme = new ThemeManager.Preferences();
        ShieldDrainerModule.Preferences shieldTest = new ShieldDrainerModule.Preferences();
        AutoClutchModule.Mode clutchMode = AutoClutchModule.Mode.BOTH;
        AutoClutchModule.TellySpeed tellySpeed = AutoClutchModule.TellySpeed.FAST;
        int clutchRevision;
        int zoomFactor = 4;
        boolean hud = true;
        HudMode mode = HudMode.TRAVEL;
        HudCorner corner = HudCorner.TOP_LEFT;
    }
}
