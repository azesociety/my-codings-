package dev.lungetest;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/** A read-only display of local manual clicks and horizontal travel. */
public final class SpearometerHud {
    private static final int WIDTH = 190;
    private static int TEXT = ThemeManager.text();
    private static int MUTED = ThemeManager.muted();
    private static int ACCENT = ThemeManager.accent();
    private static int TRAVEL = ThemeManager.secondary();

    private SpearometerHud() { }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("lunge_test_client", "spearometer"),
                SpearometerHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker ignored) {
        Minecraft client = Minecraft.getInstance();
        if (!LungeTestClient.hudEnabled() || client.options.hideGui || client.screen != null
                || client.player == null || client.level == null || client.getConnection() == null) return;
        renderSnapshot(graphics, client, LungeTestClient.stats(),
                LungeTestClient.hudMode(), LungeTestClient.hudCorner());
    }

    /** Exposed for real-renderer verification without creating a world or sending packets. */
    public static void renderSnapshot(GuiGraphics graphics, Minecraft client, SpearMetrics.Snapshot stats,
                                      LungeTestClient.HudMode mode, LungeTestClient.HudCorner corner) {
        TEXT = ThemeManager.text(); MUTED = ThemeManager.muted();
        ACCENT = ThemeManager.accent(); TRAVEL = ThemeManager.secondary();
        Bounds bounds = panelBounds(graphics.guiWidth(), graphics.guiHeight(), mode, corner);
        int x = bounds.x();
        int y = bounds.y();
        int right = x + bounds.width();
        Font font = client.font;

        graphics.fill(x + 2, y + 3, right + 2, y + bounds.height() + 3, 0x55000000);
        graphics.fillGradient(x, y, right, y + bounds.height(), ThemeManager.alpha(ThemeManager.top(), 239), ThemeManager.alpha(ThemeManager.bottom(), 239));
        graphics.fill(x, y, right, y + 2, ACCENT);
        graphics.fill(x, y + 2, x + 1, y + bounds.height(), ThemeManager.top());
        graphics.fill(right - 1, y + 2, right, y + bounds.height(), ThemeManager.top());
        text(graphics, font, "Spearometer", x + 10, y + 10, TEXT, bounds.width() - 74);

        String state = LungeTestClient.enabled() ? "NO CD" : "VANILLA";
        int stateWidth = font.width(state) + 8;
        graphics.fill(right - stateWidth - 8, y + 7, right - 8, y + 20,
                LungeTestClient.enabled() ? 0xFF16404C : 0xFF283649);
        graphics.drawString(font, state, right - stateWidth - 4, y + 10,
                LungeTestClient.enabled() ? ACCENT : MUTED, false);

        if (mode == LungeTestClient.HudMode.CLICKS || mode == LungeTestClient.HudMode.BOTH) {
            clickMetrics(graphics, font, stats, x, y, bounds.width());
        }
        if (mode == LungeTestClient.HudMode.TRAVEL) {
            text(graphics, font, "Speed: " + decimal(stats.speed()) + " b/s", x + 10, y + 28,
                    ACCENT, bounds.width() - 20);
            text(graphics, font, "Peak: " + decimal(stats.peakSpeed()) + " b/s", x + 10, y + 42,
                    TEXT, bounds.width() - 20);
            text(graphics, font, "Distance: " + decimal(stats.distance()) + " blocks", x + 10, y + 56,
                    TEXT, bounds.width() - 20);
            travelGraph(graphics, stats.speedHistory(), x + 10, y + 72, bounds.width() - 20, 20);
        } else if (mode == LungeTestClient.HudMode.BOTH) {
            graphics.fill(x + 10, y + 76, right - 10, y + 77, ThemeManager.top());
            text(graphics, font, decimal(stats.speed()) + " b/s", x + 10, y + 83,
                    TRAVEL, bounds.width() / 2 - 10);
            String peak = "Peak " + decimal(stats.peakSpeed()) + " b/s";
            textRight(graphics, font, peak, right - 10, y + 83, MUTED, bounds.width() / 2 - 10);
            text(graphics, font, "Distance: " + decimal(stats.distance()) + " blocks", x + 10, y + 97,
                    TEXT, bounds.width() - 20);
            travelGraph(graphics, stats.speedHistory(), x + 10, y + 113, bounds.width() - 20, 19);
        }
        text(graphics, font, "Local data / recent samples", x + 10, y + bounds.height() - 14,
                MUTED, bounds.width() - 20);
    }

    private static void clickMetrics(GuiGraphics graphics, Font font, SpearMetrics.Snapshot stats,
                                     int x, int y, int width) {
        int columnWidth = (width - 20) / 3;
        String[] labels = {"Click/s", "Start/s", "Peak"};
        int[] values = {stats.clicksPerSecond(), stats.startsPerSecond(), stats.peakClicksPerSecond()};
        for (int i = 0; i < labels.length; i++) {
            int columnX = x + 10 + i * columnWidth;
            text(graphics, font, labels[i], columnX, y + 28, MUTED, columnWidth - 4);
            text(graphics, font, Integer.toString(values[i]), columnX, y + 40, ACCENT, columnWidth - 4);
        }
        clickGraph(graphics, stats.clickHistory(), x + 10, y + 55, width - 20, 16);
    }

    private static void clickGraph(GuiGraphics graphics, int[] samples, int x, int y, int width, int height) {
        double[] values = new double[samples.length];
        for (int i = 0; i < samples.length; i++) values[i] = samples[i];
        graph(graphics, values, x, y, width, height, 10, ACCENT);
    }

    private static void travelGraph(GuiGraphics graphics, double[] values, int x, int y, int width, int height) {
        graph(graphics, values, x, y, width, height, 10, TRAVEL);
    }

    private static void graph(GuiGraphics graphics, double[] values, int x, int y,
                              int width, int height, double minimumScale, int color) {
        graphics.fill(x, y, x + width, y + height, ThemeManager.bottom());
        graphics.fill(x, y + height - 1, x + width, y + height, ThemeManager.top());
        if (values.length == 0) return;
        double scale = minimumScale;
        for (double value : values) if (Double.isFinite(value)) scale = Math.max(scale, value);
        for (int i = 0; i < values.length; i++) {
            double value = Double.isFinite(values[i]) ? Math.max(0, values[i]) : 0;
            int barHeight = (int) Math.round(Math.min(1, value / scale) * (height - 2));
            if (barHeight == 0) continue;
            int left = x + i * width / values.length;
            int right = x + (i + 1) * width / values.length - 1;
            graphics.fill(left, y + height - 1 - barHeight, Math.max(left + 1, right), y + height - 1,
                    i == values.length - 1 ? TEXT : color);
        }
    }

    private static void text(GuiGraphics graphics, Font font, String value, int x, int y, int color, int width) {
        graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(0, width)), x, y, color, false);
    }

    private static void textRight(GuiGraphics graphics, Font font, String value, int right, int y,
                                  int color, int width) {
        String clipped = font.plainSubstrByWidth(value, Math.max(0, width));
        graphics.drawString(font, clipped, right - font.width(clipped), y, color, false);
    }

    private static String decimal(double value) {
        double safe = Double.isFinite(value) ? Math.max(0, value) : 0;
        if (safe >= 1_000_000) return String.format(Locale.ROOT, "%.1fm", safe / 1_000_000);
        if (safe >= 10_000) return String.format(Locale.ROOT, "%.1fk", safe / 1_000);
        return String.format(Locale.ROOT, "%.1f", safe);
    }

    /** Four corner layouts reserve space above the bottom vanilla status bars. */
    public static Bounds panelBounds(int guiWidth, int guiHeight, LungeTestClient.HudMode mode,
                                     LungeTestClient.HudCorner corner) {
        int width = Math.max(1, Math.min(WIDTH, guiWidth - 16));
        int height = switch (mode) {
            case CLICKS -> 96;
            case TRAVEL -> 112;
            case BOTH -> 152;
        };
        boolean right = corner == LungeTestClient.HudCorner.TOP_RIGHT
                || corner == LungeTestClient.HudCorner.BOTTOM_RIGHT;
        boolean bottom = corner == LungeTestClient.HudCorner.BOTTOM_LEFT
                || corner == LungeTestClient.HudCorner.BOTTOM_RIGHT;
        int x = right ? Math.max(8, guiWidth - width - 8) : 8;
        int y = bottom ? Math.max(8, guiHeight - height - 44) : 8;
        return new Bounds(x, y, width, height);
    }

    public record Bounds(int x, int y, int width, int height) { }
}
