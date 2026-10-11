package dev.lungetest;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Read-only client FPS and server-reported player latency; no probes or extra packets. */
public final class PerformanceHudModule {
    private static boolean enabled;
    private PerformanceHudModule() {}
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static String fpsLabel(Minecraft client) { return Math.max(0, client.getFps()) + " FPS"; }
    public static String pingLabel(Minecraft client) {
        if (client.hasSingleplayerServer()) return "Local world";
        var connection = client.getConnection();
        var info = connection == null || client.player == null ? null : connection.getPlayerInfo(client.player.getUUID());
        return info == null ? "Ping unavailable" : Math.max(0, info.getLatency()) + " ms ping";
    }
    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("lunge_test_client", "performance"), (g, delta) -> {
                    var client = Minecraft.getInstance();
                    if (enabled && client.player != null && client.level != null && client.screen == null && !client.options.hideGui)
                        renderSnapshot(g, client, fpsLabel(client), pingLabel(client));
                });
    }
    public static void renderSnapshot(GuiGraphics g, Minecraft client, String fps, String ping) {
        var b = panelBounds(g.guiWidth(), g.guiHeight());
        UiGlass.gradient(g, b.x(), b.y(), b.width(), b.height(), 8,
                ThemeManager.alpha(UiGlass.surfaceTop(), 225), ThemeManager.alpha(UiGlass.surfaceBottom(), 225));
        UiTypography.draw(g, client.font, UiTypography.fit(client.font, fps, b.width()-14), b.x()+7, b.y()+6, ThemeManager.text(), false);
        UiTypography.draw(g, client.font, UiTypography.fit(client.font, ping, b.width()-14), b.x()+7, b.y()+19, ThemeManager.secondary(), false);
    }
    public static SpearometerHud.Bounds panelBounds(int width, int height) {
        var session = SessionHudModule.panelBounds(width, height);
        return new SpearometerHud.Bounds(session.x(), session.y()+session.height()+8, session.width(), 32);
    }
}
