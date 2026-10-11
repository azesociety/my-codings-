package dev.lungetest.verify;

import dev.lungetest.*;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

public final class SurvivalModuleVerification {
    public static void verify(Minecraft client) {
        var oldPlayer = client.player; var oldLevel = client.level; var oldScreen = client.screen;
        boolean armor = ArmorAlertsModule.enabled(), bright = FullbrightModule.enabled(), session = SessionHudModule.enabled();
        int threshold = ArmorAlertsModule.threshold(), checks = 0;
        boolean hud = LungeTestClient.hudEnabled();
        var hudMode = LungeTestClient.hudMode(); var hudCorner = LungeTestClient.hudCorner();
        double gamma = client.options.gamma().get();
        try {
            var f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); var unsafe = (Unsafe) f.get(null);
            var player = (SpearQuickLungeVerification.QuickPlayer) unsafe.allocateInstance(SpearQuickLungeVerification.QuickPlayer.class);
            var equipment = new EntityEquipment();
            var eqField = LivingEntity.class.getDeclaredField("equipment"); eqField.setAccessible(true); eqField.set(player, equipment);
            player.inventory = new Inventory(player, equipment); player.abilities = new Abilities(); player.alive = true;
            player.fixtureLevel = (CombatAssistVerification.CombatLevel) unsafe.allocateInstance(CombatAssistVerification.CombatLevel.class);
            client.player = player; client.level = player.fixtureLevel;
            ArmorAlertsModule.initialize(20);
            require(ArmorAlertsModule.warning(player).isEmpty(), "Empty armor slots have no false low-durability warning"); checks++;
            var helmet = new ItemStack(Items.DIAMOND_HELMET); equipment.set(EquipmentSlot.HEAD, helmet);
            require(ArmorAlertsModule.remainingPercent(helmet) == 100 && ArmorAlertsModule.warning(player).isEmpty(), "New armor is healthy"); checks++;
            helmet.setDamageValue((int) Math.ceil(helmet.getMaxDamage() * .9));
            require(ArmorAlertsModule.warning(player).contains("Helmet") && ArmorAlertsModule.remainingPercent(helmet) <= 10, "Actual damaged armor produces the correct slot warning"); checks++;
            var boots = new ItemStack(Items.IRON_BOOTS); boots.setDamageValue((int) Math.ceil(boots.getMaxDamage() * .75)); equipment.set(EquipmentSlot.FEET, boots);
            require(!ArmorAlertsModule.warning(player).contains("Boots"), "Armor above threshold is excluded"); checks++;
            ArmorAlertsModule.initialize(30);
            require(ArmorAlertsModule.warning(player).contains("Boots") && ArmorAlertsModule.warning(player).contains("Helmet"), "Threshold includes both actual damaged pieces"); checks++;
            equipment.clear(); require(ArmorAlertsModule.warning(player).isEmpty(), "Removing damaged armor clears its warning"); checks++;
            require(ArmorAlertsModule.remainingPercent(ItemStack.EMPTY) == -1 && ArmorAlertsModule.remainingPercent(new ItemStack(Items.STONE)) == -1, "Non-damageable items do not raise alerts"); checks++;
            FullbrightModule.setEnabled(false);
            require(LightTexture.getBrightness(0, 0) == 0 && LightTexture.getBrightness(0, 15) == 1, "OFF uses actual vanilla light calculation"); checks++;
            FullbrightModule.setEnabled(true);
            for (int light : new int[]{0, 1, 7, 15}) { require(LightTexture.getBrightness(0, light) == 1, "Transformed vanilla brightness is full at level " + light); checks++; }
            require(FullbrightModule.ambient(.1F) == 1 && client.options.gamma().get() == gamma, "Fullbright leaves saved gamma unchanged"); checks++;
            FullbrightModule.setEnabled(false); require(LightTexture.getBrightness(0, 0) == 0, "Disabling immediately restores vanilla brightness"); checks++;
            FullbrightModule.setEnabled(true); client.level = null; require(LightTexture.getBrightness(0, 0) == 0, "No-world menus keep vanilla light calculation"); checks++;
            client.level = player.fixtureLevel;
            var menu = new LungeTestScreen(); client.setScreen(menu); menu.init(320, 240);
            require(menu.modulePage() == 0 && button(menu, "Previous").active == false && button(menu, "Next").active, "First sidebar page exposes navigation"); checks++;
            button(menu, "Next").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            require(menu.modulePage() == 1 && !button(menu, "Next").active && button(menu, "Previous").active, "Second sidebar page exposes new modules without changing enabled states"); checks++;
            for (var module : new AzeiumModules.Module[]{AzeiumModules.Module.ARMOR_ALERTS,AzeiumModules.Module.FULLBRIGHT,AzeiumModules.Module.SESSION_HUD}) {
                menu.selectModule(module); require(menu.modulePage() == 1 && button(menu, module.label()) != null, "New module reachable at minimum size: " + module); checks++;
                boolean before = module.enabled(); button(menu, before ? "Disable module" : "Enable module").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                require(module.enabled() != before, "Actual toggle operates " + module); checks++;
            }
            menu.selectModule(AzeiumModules.Module.ARMOR_ALERTS);
            int original = ArmorAlertsModule.threshold();
            button(menu, "Warn at: " + original + "%").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            require(ArmorAlertsModule.threshold() != original, "Threshold widget changes armor configuration"); checks++;
            menu.selectModule(AzeiumModules.Module.SESSION_HUD);
            button(menu, "Reset session stats").onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            require(SessionHudModule.timeLabel().equals("00:00:00") && SessionHudModule.distanceLabel().equals("0.0 blocks"), "Session reset control clears both readouts"); checks++;
            if (!LungeTestClient.hudEnabled()) LungeTestClient.toggleHud();
            for (int width : new int[]{320, 341, 480, 854}) for (var mode : LungeTestClient.HudMode.values()) for (var corner : LungeTestClient.HudCorner.values()) {
                while (LungeTestClient.hudMode() != mode) LungeTestClient.cycleHudMode();
                while (LungeTestClient.hudCorner() != corner) LungeTestClient.cycleHudCorner();
                var spear = SpearometerHud.panelBounds(width, 240, mode, corner);
                var sessionBounds = SessionHudModule.panelBounds(width, 240);
                var armorBounds = ArmorAlertsModule.panelBounds(width, 240);
                require(!overlap(spear, sessionBounds) && !overlap(spear, armorBounds) && !overlap(sessionBounds, armorBounds),
                        "New HUDs avoid each other and spearometer at width " + width + ", " + mode + "/" + corner); checks++;
                require(sessionBounds.width() >= 100 && armorBounds.width() >= 100, "Compact HUDs retain useful readable width"); checks++;
            }
            LungeTestClient.LOG.info("SURVIVAL_MODULES_VERIFIED: {} checks; actual armor stacks/equipment, thresholds, transformed LightTexture brightness and unchanged gamma, paged navigation and module controls", checks);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        finally {
            ArmorAlertsModule.initialize(threshold); ArmorAlertsModule.setEnabled(armor); FullbrightModule.setEnabled(bright); SessionHudModule.setEnabled(session);
            if (LungeTestClient.hudEnabled() != hud) LungeTestClient.toggleHud();
            while (LungeTestClient.hudMode() != hudMode) LungeTestClient.cycleHudMode();
            while (LungeTestClient.hudCorner() != hudCorner) LungeTestClient.cycleHudCorner();
            client.player = oldPlayer; client.level = oldLevel; client.screen = oldScreen;
        }
    }
    private static Button button(LungeTestScreen screen, String label) { return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow(); }
    private static void require(boolean test, String message) { if (!test) throw new AssertionError(message); }
    private static boolean overlap(SpearometerHud.Bounds a, SpearometerHud.Bounds b) {
        return a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
                && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
    }
}
