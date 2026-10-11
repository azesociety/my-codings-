package dev.lungetest;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;

/** Shared glass surfaces. Half-unit rasterization softens edges at every GUI scale. */
final class UiGlass {
    private UiGlass() {}

    interface Hinted { Hint hint(); }
    static final class Hint {
        private Tooltip tooltip;
        private long hoveredAt;
        void set(Tooltip tooltip) { this.tooltip = tooltip; hoveredAt = 0; }
        void draw(GuiGraphics g, int mouseX, int mouseY, boolean hovered) {
            if (!hovered || tooltip == null) { hoveredAt = 0; return; }
            long now = System.nanoTime();
            if (hoveredAt == 0) hoveredAt = now;
            float reveal = Math.clamp((now - hoveredAt - 450_000_000L) / 140_000_000F, 0, 1);
            if (reveal <= 0) return;
            var client = Minecraft.getInstance();
            var lines = tooltip.toCharSequence(client);
            if (lines.isEmpty()) return;
            int w = lines.stream().mapToInt(client.font::width).max().orElse(0) + 16;
            int h = lines.size() * 11 + 12;
            int x = Math.clamp(mouseX + 12, 4, Math.max(4, g.guiWidth() - w - 4));
            int y = mouseY + 15 + h > g.guiHeight() - 4 ? mouseY - h - 8 : mouseY + 15;
            y = Math.clamp(y, 4, Math.max(4, g.guiHeight() - h - 4));
            rounded(g, x - 2, y + 2, w + 4, h + 2, 9, fade(0x40000000, reveal));
            gradient(g, x, y, w, h, 7, fade(0x756D7A91, reveal), fade(0x3C425068, reveal));
            gradient(g, x + 1, y + 1, w - 2, h - 2, 6,
                    fade(ThemeManager.alpha(surfaceTop(), 248), reveal), fade(0xFA0C121F, reveal));
            int rowY = y + 6;
            for (var line : lines) {
                g.drawString(client.font, line, x + 8, rowY, fade(ThemeManager.text(), reveal), false);
                rowY += 11;
            }
        }
        void narrate(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            if (tooltip != null) tooltip.updateNarration(output);
        }
    }

    static int surfaceTop() { return ThemeManager.mix(0xFF171622, ThemeManager.first(), .10F); }
    static int surfaceBottom() { return ThemeManager.mix(0xFF0E1019, ThemeManager.second(), .06F); }
    static int opacity() { return Math.clamp(120 + Math.round(LungeTestClient.uiOpacity() * 1.25F), 0, 255); }
    static int fade(int color, float amount) {
        return ThemeManager.alpha(color, Math.round((color >>> 24) * Math.clamp(amount, 0, 1)));
    }
    static float approach(float value, float target, float seconds) {
        return value + (target - value) * (1 - (float) Math.exp(-Math.clamp(seconds, 0, .1F) * 16));
    }
    static void panel(GuiGraphics g, int x, int y, int w, int h, float reveal) {
        rounded(g, x - 10, y + 10, w + 20, h + 5, 23, fade(0x16000000, reveal));
        rounded(g, x - 5, y + 5, w + 10, h + 4, 20, fade(0x36000000, reveal));
        gradient(g, x, y, w, h, 17, fade(ThemeManager.alpha(ThemeManager.first(), 105), reveal),
                fade(ThemeManager.alpha(ThemeManager.second(), 45), reveal));
        gradient(g, x + 1, y + 1, w - 2, h - 2, 16,
                fade(ThemeManager.alpha(surfaceTop(), opacity()), reveal),
                fade(ThemeManager.alpha(surfaceBottom(), opacity()), reveal));
        gradient(g, x + 2, y + 2, w - 4, 36, 15, fade(0x12FFFFFF, reveal), 0x00FFFFFF);
    }
    static void button(GuiGraphics g, int x, int y, int w, int h, float hover,
                       boolean selected, boolean active, float reveal) {
        int tint = ThemeManager.mix(surfaceTop(), ThemeManager.first(), selected ? .36F : .12F + hover * .12F);
        int rim = ThemeManager.alpha(selected ? ThemeManager.first() : 0xFFCFC7DA,
                selected ? 110 : Math.round(17 + hover * 28));
        gradient(g, x, y, w, h, 9, fade(rim, reveal), fade(ThemeManager.alpha(rim, 12), reveal));
        gradient(g, x + 1, y + 1, w - 2, h - 2, 8,
                fade(ThemeManager.alpha(tint, active ? Math.round(155 + hover * 38) : 95), reveal),
                fade(ThemeManager.alpha(surfaceBottom(), active ? 135 : 78), reveal));
        if (hover > .05F && active) gradient(g, x + 3, y + 2, w - 6, Math.max(1, h / 3), 5,
                fade(0x0CFFFFFF, hover * reveal), 0x00FFFFFF);
    }
    static void rounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        gradient(g, x, y, w, h, r, color, color);
    }
    static void gradient(GuiGraphics g, int x, int y, int w, int h, int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        if (radius <= 0) { g.fillGradient(x, y, x + w, y + h, top, bottom); return; }
        int rows = h * 2, columns = w * 2, r = Math.min(radius * 2, Math.min(columns, rows) / 2);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(.5F, .5F);
        int start = 0, previousInset = -1, previousColor = 0;
        for (int row = 0; row < rows; row++) {
            int edge = Math.min(row, rows - row - 1);
            double inset = edge >= r ? 0 : r - Math.sqrt(r * r - Math.pow(r - edge - .5, 2));
            int fullInset = (int) Math.ceil(inset);
            float fraction = (float) (fullInset - inset);
            int color = interpolate(top, bottom, rows == 1 ? 0 : row / (float) (rows - 1));
            if (row > 0 && (fullInset != previousInset || color != previousColor)) {
                g.fill(previousInset, start, columns - previousInset, row, previousColor);
                start = row;
            }
            if (fullInset > 0 && fraction > .01F) {
                int antialias = fade(color, fraction);
                g.fill(fullInset - 1, row, fullInset, row + 1, antialias);
                g.fill(columns - fullInset, row, columns - fullInset + 1, row + 1, antialias);
            }
            previousInset = fullInset; previousColor = color;
        }
        g.fill(previousInset, start, columns - previousInset, rows, previousColor);
        g.pose().popMatrix();
    }
    private static int interpolate(int a, int b, float amount) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            result |= Math.round((a >>> shift & 255) + ((b >>> shift & 255) - (a >>> shift & 255)) * amount) << shift;
        return result;
    }
}
