package dev.lungetest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;

public final class SessionHudModule {
    private static final SessionTracker tracker = new SessionTracker();
    private static boolean enabled;
    private static ClientPacketListener connection;
    private static ClientLevel level;
    private SessionHudModule() {}
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static void reset() { tracker.reset(); }
    public static String timeLabel() {
        long seconds = tracker.seconds();
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    public static String distanceLabel() { return String.format(Locale.ROOT, "%.1f blocks", tracker.distance()); }
    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null || client.getConnection() == null) {
            tracker.clearAnchor(); connection = null; level = null; return;
        }
        if (connection != client.getConnection()) { tracker.reset(); connection = client.getConnection(); }
        if (level != client.level) { tracker.clearAnchor(); level = client.level; }
        if (!client.player.isAlive() || client.isPaused() || !client.isWindowActive()) { tracker.clearAnchor(); return; }
        var position = client.player.position();
        tracker.sample(System.nanoTime(), position.x, position.z);
    }
    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("lunge_test_client", "session"), (g, delta) -> {
                    var client = Minecraft.getInstance();
                    if (enabled && !client.options.hideGui && client.screen == null && client.player != null && client.level != null)
                        renderSnapshot(g, client, timeLabel(), distanceLabel());
                });
    }
    public static void renderSnapshot(GuiGraphics g, Minecraft client, String time, String distance) {
        var bounds = panelBounds(g.guiWidth(), g.guiHeight());
        int w = bounds.width(), x = bounds.x(), y = bounds.y();
        UiGlass.gradient(g, x, y, w, 38, 7, ThemeManager.alpha(UiGlass.surfaceTop(), 225), ThemeManager.alpha(UiGlass.surfaceBottom(), 225));
        UiTypography.draw(g, client.font, UiTypography.fit(client.font, "Time " + time, w - 14), x + 7, y + 7, ThemeManager.text(), false);
        UiTypography.draw(g, client.font, UiTypography.fit(client.font, distance, w - 14), x + 7, y + 22, ThemeManager.secondary(), false);
    }
    public static SpearometerHud.Bounds panelBounds(int width, int height) {
        int w = Math.min(124, width - 16), x = width - w - 8;
        if (LungeTestClient.hudEnabled()) {
            var meter = SpearometerHud.panelBounds(width, height, LungeTestClient.hudMode(), LungeTestClient.hudCorner());
            w = Math.max(1, Math.min(w, width - meter.width() - 24));
            x = meter.x() == 8 ? width - w - 8 : 8;
        }
        return new SpearometerHud.Bounds(x, 8, w, 38);
    }
}
