package dev.lungetest.verify;

import dev.lungetest.AzeiumKeybinds;
import dev.lungetest.AzeiumModules;
import dev.lungetest.LungeTestClient;
import dev.lungetest.LungeTestScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;
import java.util.EnumMap;

/** Exercises real keyboard callbacks with deliberate keys and no network transport. */
public final class KeybindVerification {
    private static int checks;
    private static final KeyEvent ACTIVATE = new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0);

    public static void verify(Minecraft client, LungeTestScreen initialScreen) {
        checks = 0;
        var originalBindings = new EnumMap<AzeiumModules.Module, Integer>(AzeiumModules.Module.class);
        var originalStates = new EnumMap<AzeiumModules.Module, Boolean>(AzeiumModules.Module.class);
        for (var module : AzeiumModules.Module.values()) {
            originalBindings.put(module, AzeiumKeybinds.key(module));
            originalStates.put(module, module.enabled());
        }
        int originalMenu = AzeiumKeybinds.menuKey();
        var previousPlayer = client.player;
        var previousLevel = client.level;
        var previousScreen = client.screen;
        try {
            for (var module : AzeiumModules.Module.values()) AzeiumKeybinds.unbind(module);
            require(AzeiumKeybinds.bind(AzeiumModules.Module.SPEAROMETER, GLFW.GLFW_KEY_F6),
                    "A module can receive an unused key");
            require(!AzeiumKeybinds.bind(AzeiumModules.Module.PLAYER_HIGHLIGHTS, GLFW.GLFW_KEY_F6),
                    "A second module cannot take an occupied module key");
            require(AzeiumKeybinds.key(AzeiumModules.Module.PLAYER_HIGHLIGHTS) == GLFW.GLFW_KEY_UNKNOWN,
                    "Rejected assignment preserves the previous binding");

            initialScreen.selectModule(AzeiumModules.Module.PLAYER_HIGHLIGHTS);
            beginModuleCapture(initialScreen);
            require(initialScreen.capturingKeybind(), "Keybind control enters capture mode");
            key(client, GLFW.GLFW_KEY_F6, GLFW.GLFW_PRESS);
            require(initialScreen.capturingKeybind()
                            && AzeiumKeybinds.key(AzeiumModules.Module.PLAYER_HIGHLIGHTS) == GLFW.GLFW_KEY_UNKNOWN,
                    "Real keyboard capture rejects a conflicting key and remains ready for another key");
            key(client, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_PRESS);
            require(!initialScreen.capturingKeybind() && client.screen == initialScreen,
                    "Escape cancels capture without closing the menu");

            boolean beforeHighlight = AzeiumModules.Module.PLAYER_HIGHLIGHTS.enabled();
            beginModuleCapture(initialScreen);
            key(client, GLFW.GLFW_KEY_F8, GLFW.GLFW_PRESS);
            require(!initialScreen.capturingKeybind()
                            && AzeiumKeybinds.key(AzeiumModules.Module.PLAYER_HIGHLIGHTS) == GLFW.GLFW_KEY_F8,
                    "Real keyboard capture stores the selected module key");
            require(AzeiumModules.Module.PLAYER_HIGHLIGHTS.enabled() == beforeHighlight,
                    "Binding a key does not toggle the selected module");
            beginModuleCapture(initialScreen);
            key(client, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_PRESS);
            require(AzeiumKeybinds.key(AzeiumModules.Module.PLAYER_HIGHLIGHTS) == GLFW.GLFW_KEY_F8,
                    "Cancelled rebind preserves the existing key");
            beginModuleCapture(initialScreen);
            key(client, GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_PRESS);
            require(!initialScreen.capturingKeybind()
                            && AzeiumKeybinds.key(AzeiumModules.Module.PLAYER_HIGHLIGHTS) == GLFW.GLFW_KEY_UNKNOWN,
                    "Backspace clears a module binding");
            beginModuleCapture(initialScreen);
            key(client, GLFW.GLFW_KEY_F8, GLFW.GLFW_PRESS);
            beginModuleCapture(initialScreen);
            key(client, GLFW.GLFW_KEY_DELETE, GLFW.GLFW_PRESS);
            require(!initialScreen.capturingKeybind()
                            && AzeiumKeybinds.key(AzeiumModules.Module.PLAYER_HIGHLIGHTS) == GLFW.GLFW_KEY_UNKNOWN,
                    "Delete also clears a module binding");
            require(AzeiumKeybinds.bind(AzeiumModules.Module.PLAYER_HIGHLIGHTS, GLFW.GLFW_KEY_F8),
                    "Cleared module can be bound again");

            prefix(initialScreen, "Menu key: ").onPress(ACTIVATE);
            require(initialScreen.capturingKeybind(), "Menu key has its own capture control");
            key(client, GLFW.GLFW_KEY_F8, GLFW.GLFW_PRESS);
            require(initialScreen.capturingKeybind() && AzeiumKeybinds.menuKey() == originalMenu,
                    "Menu cannot take an occupied module key");
            key(client, GLFW.GLFW_KEY_F9, GLFW.GLFW_PRESS);
            require(!initialScreen.capturingKeybind() && AzeiumKeybinds.menuKey() == GLFW.GLFW_KEY_F9,
                    "Menu capture updates the registered menu key");
            key(client, GLFW.GLFW_KEY_F9, GLFW.GLFW_PRESS);
            require(client.screen instanceof TitleScreen, "Rebound menu key closes Azeium");
            key(client, GLFW.GLFW_KEY_F9, GLFW.GLFW_PRESS);
            require(client.screen instanceof LungeTestScreen, "Rebound menu key reopens Azeium");
            var reboundScreen = (LungeTestScreen) client.screen;
            reboundScreen.selectModule(AzeiumModules.Module.PLAYER_HIGHLIGHTS);
            prefix(reboundScreen, "Menu key: ").onPress(ACTIVATE);
            key(client, originalMenu, GLFW.GLFW_PRESS);
            require(AzeiumKeybinds.menuKey() == originalMenu && !reboundScreen.capturingKeybind(),
                    "Capture restores the original menu key");

            boolean beforeHud = AzeiumModules.Module.SPEAROMETER.enabled();
            key(client, GLFW.GLFW_KEY_F6, GLFW.GLFW_PRESS);
            key(client, GLFW.GLFW_KEY_F8, GLFW.GLFW_PRESS);
            require(AzeiumModules.Module.SPEAROMETER.enabled() == beforeHud
                            && AzeiumModules.Module.PLAYER_HIGHLIGHTS.enabled() == beforeHighlight,
                    "Assigned keys do not toggle modules while the menu is open");

            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (Unsafe) unsafeField.get(null);
            var local = (ManualAttackVerification.FixturePlayer)
                    unsafe.allocateInstance(ManualAttackVerification.FixturePlayer.class);
            local.alive = true;
            local.held = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SPEAR);
            var connection = (ClientPacketListener) unsafe.allocateInstance(ClientPacketListener.class);
            var connectionField = LocalPlayer.class.getField("connection");
            connectionField.setAccessible(true);
            connectionField.set(local, connection);
            client.player = local;
            client.level = (ManualAttackVerification.FixtureLevel)
                    unsafe.allocateInstance(ManualAttackVerification.FixtureLevel.class);
            client.screen = null;
            int[] keys = {GLFW.GLFW_KEY_F5, GLFW.GLFW_KEY_F6, GLFW.GLFW_KEY_F8,
                    GLFW.GLFW_KEY_F7, GLFW.GLFW_KEY_F10, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P,
                    GLFW.GLFW_KEY_F11, GLFW.GLFW_KEY_F12, GLFW.GLFW_KEY_F13, GLFW.GLFW_KEY_F14, GLFW.GLFW_KEY_F15, GLFW.GLFW_KEY_F16, GLFW.GLFW_KEY_F17, GLFW.GLFW_KEY_F19, GLFW.GLFW_KEY_F20};
            for (var module : AzeiumModules.Module.values()) {
                require(AzeiumKeybinds.bind(module, keys[module.ordinal()]),
                        "Each module receives an independent gameplay key: " + module);
                boolean before = module.enabled();
                key(client, keys[module.ordinal()], GLFW.GLFW_PRESS);
                require(module.enabled() != before, "One deliberate key press toggles " + module);
                key(client, keys[module.ordinal()], GLFW.GLFW_REPEAT);
                require(module.enabled() != before, "Held-key repeat cannot toggle " + module + " again");
                key(client, keys[module.ordinal()], GLFW.GLFW_PRESS);
                require(module.enabled() == before, "Second deliberate press restores " + module);
            }
            client.player = null;
            client.level = null;
            for (var module : AzeiumModules.Module.values()) {
                boolean before = module.enabled();
                key(client, keys[module.ordinal()], GLFW.GLFW_PRESS);
                require(module.enabled() == before, "Missing gameplay session cannot toggle " + module);
            }
            LungeTestClient.LOG.info("KEYBINDS_VERIFIED: {} actual keyboard checks; capture, conflicts, cancel/clear, menu rebind and all gameplay module keys; no network transport", checks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not exercise actual keybind callbacks", e);
        } finally {
            LungeTestClient.disable();
            client.player = previousPlayer;
            client.level = previousLevel;
            client.screen = previousScreen;
            for (var module : AzeiumModules.Module.values()) AzeiumKeybinds.unbind(module);
            require(AzeiumKeybinds.bindMenu(originalMenu), "Original menu key restores after verification");
            for (var module : AzeiumModules.Module.values()) {
                int original = originalBindings.get(module);
                if (original != GLFW.GLFW_KEY_UNKNOWN)
                    require(AzeiumKeybinds.bind(module, original), "Original module key restores: " + module);
                if (module.enabled() != originalStates.get(module) && module.canToggle(client)) module.toggle(client);
            }
            initialScreen.selectModule(AzeiumModules.Module.PLAYER_HIGHLIGHTS);
        }
    }

    private static void beginModuleCapture(LungeTestScreen screen) {
        prefix(screen, "Keybind: ").onPress(ACTIVATE);
    }
    private static Button prefix(LungeTestScreen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().startsWith(prefix)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing control with prefix: " + prefix));
    }
    private static void key(Minecraft client, int code, int action) throws ReflectiveOperationException {
        var callback = client.keyboardHandler.getClass().getDeclaredMethod("keyPress", long.class, int.class,
                KeyEvent.class);
        callback.setAccessible(true);
        callback.invoke(client.keyboardHandler, client.getWindow().handle(), action, new KeyEvent(code, 0, 0));
        callback.invoke(client.keyboardHandler, client.getWindow().handle(), GLFW.GLFW_RELEASE,
                new KeyEvent(code, 0, 0));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
