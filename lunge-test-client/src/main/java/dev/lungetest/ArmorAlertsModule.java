package dev.lungetest;

import java.util.ArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Low-durability notices for currently equipped armor; never changes equipment or inventory. */
public final class ArmorAlertsModule {
    private static boolean enabled;
    private static int threshold = 20;
    private ArmorAlertsModule() {}
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static int threshold() { return threshold; }
    public static void initialize(int value) { threshold = value == 10 || value == 30 ? value : 20; }
    public static void cycleThreshold() { threshold = threshold == 10 ? 20 : threshold == 20 ? 30 : 10; LungeTestClient.saveArmorPreferences(); }
    public static int remainingPercent(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) return -1;
        return Math.clamp((int) Math.ceil(100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage()), 0, 100);
    }
    public static String warning(Player player) {
        var parts = new ArrayList<String>();
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        String[] labels = {"Helmet", "Chest", "Legs", "Boots"};
        for (int i = 0; i < slots.length; i++) {
            int percent = remainingPercent(player.getItemBySlot(slots[i]));
            if (percent >= 0 && percent <= threshold) parts.add(labels[i] + " " + percent + "%");
        }
        return String.join(" · ", parts);
    }
    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("lunge_test_client", "armor_alerts"), (g, delta) -> {
                    var client = Minecraft.getInstance();
                    if (!enabled || client.options.hideGui || client.screen != null || client.player == null || client.level == null) return;
                    String warning = warning(client.player);
                    if (!warning.isEmpty()) renderWarning(g, client, warning);
                });
    }
    public static void renderWarning(GuiGraphics g, Minecraft client, String warning) {
        var bounds = panelBounds(g.guiWidth(), g.guiHeight());
        if (bounds.width() < 180) {
            UiGlass.gradient(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 7, 0xED382330, 0xEB17131D);
            UiTypography.draw(g, client.font, "Low armor", bounds.x() + 7, bounds.y() + 6, 0xFFFFB4C7, false);
            UiTypography.draw(g, client.font, UiTypography.fit(client.font, warning, bounds.width() - 14),
                    bounds.x() + 7, bounds.y() + 20, 0xFFFFB4C7, false);
            return;
        }
        String text = UiTypography.fit(client.font, "Low armor: " + warning, bounds.width() - 18);
        int w = UiTypography.width(client.font, text) + 18, x = bounds.x() + (bounds.width() - w) / 2, y = bounds.y();
        UiGlass.gradient(g, x, y, w, 22, 7, 0xED382330, 0xEB17131D);
        UiTypography.center(g, client.font, text, x + w / 2, y + 7, 0xFFFFB4C7);
    }
    public static SpearometerHud.Bounds panelBounds(int width, int height) {
        int w = width - 18, x = 9;
        if (LungeTestClient.hudEnabled() && (LungeTestClient.hudCorner() == LungeTestClient.HudCorner.BOTTOM_LEFT
                || LungeTestClient.hudCorner() == LungeTestClient.HudCorner.BOTTOM_RIGHT)) {
            var meter = SpearometerHud.panelBounds(width, height, LungeTestClient.hudMode(), LungeTestClient.hudCorner());
            w = Math.max(1, width - meter.width() - 24);
            x = meter.x() == 8 ? width - w - 8 : 8;
        }
        return new SpearometerHud.Bounds(x, height - (w < 180 ? 80 : 68), w, w < 180 ? 34 : 22);
    }
}
