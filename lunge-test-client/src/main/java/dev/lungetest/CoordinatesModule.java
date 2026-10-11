package dev.lungetest;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/** A compact position display below the travel HUD and above the hotbar. */
public final class CoordinatesModule {
    private static boolean enabled;
    private CoordinatesModule() {}
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static String readout(Player player) {
        var pos = player.blockPosition();
        return "XYZ  " + pos.getX() + " / " + pos.getY() + " / " + pos.getZ()
                + "  |  " + player.getDirection().getName();
    }
    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("lunge_test_client", "coordinates"),
                (graphics, delta) -> render(graphics));
    }
    private static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!enabled || client.options.hideGui || client.screen != null
                || client.player == null || client.level == null) return;
        String text = client.font.plainSubstrByWidth(readout(client.player), graphics.guiWidth() - 32);
        int y = graphics.guiHeight() - 28;
        graphics.fillGradient(8, y - 5, 24 + client.font.width(text), y + 13,
                ThemeManager.alpha(ThemeManager.top(), 180), ThemeManager.alpha(ThemeManager.bottom(), 180));
        graphics.fill(8, y - 5, 24 + client.font.width(text), y - 4, ThemeManager.alpha(ThemeManager.accent(), 140));
        graphics.drawString(client.font, text, 16, y, ThemeManager.text(), false);
    }
}
