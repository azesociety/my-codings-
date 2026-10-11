package dev.lungetest;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** Module hotkeys fire once per physical press, only while playing with no screen open. */
public final class AzeiumKeybinds {
    private static final EnumMap<AzeiumModules.Module, Integer> KEYS = new EnumMap<>(AzeiumModules.Module.class);
    private static KeyMapping menu;
    private AzeiumKeybinds() { }

    public static void initialize(Map<String, Integer> storedKeys, int storedMenu) {
        int menuCode = validKey(storedMenu) ? storedMenu : GLFW.GLFW_KEY_RIGHT_SHIFT;
        menu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.lunge_test_client.menu", menuCode, KeyMapping.Category.MISC));
        KEYS.clear();
        for (var module : AzeiumModules.Module.values()) {
            int code = storedKeys == null || storedKeys.get(module.name()) == null
                    ? GLFW.GLFW_KEY_UNKNOWN : storedKeys.get(module.name());
            if (!validKey(code) || code == menuCode || KEYS.containsValue(code)) code = GLFW.GLFW_KEY_UNKNOWN;
            KEYS.put(module, code);
        }
    }
    public static int key(AzeiumModules.Module module) { return KEYS.getOrDefault(module, GLFW.GLFW_KEY_UNKNOWN); }
    public static String label(AzeiumModules.Module module) { return display(key(module)); }
    public static int menuKey() {
        return menu == null ? GLFW.GLFW_KEY_RIGHT_SHIFT : KeyBindingHelper.getBoundKeyOf(menu).getValue();
    }
    public static String menuLabel() { return display(menuKey()); }
    public static boolean menuMatches(KeyEvent key) { return menu != null && menu.matches(key); }
    public static boolean consumeMenuClick() { return menu != null && menu.consumeClick(); }
    public static boolean validKey(int code) {
        return code >= GLFW.GLFW_KEY_SPACE && code <= GLFW.GLFW_KEY_LAST
                && code != GLFW.GLFW_KEY_ESCAPE && code != GLFW.GLFW_KEY_BACKSPACE && code != GLFW.GLFW_KEY_DELETE;
    }
    public static boolean bind(AzeiumModules.Module module, int code) {
        if (!validKey(code) || code == menuKey() || code == AutoRejoinModule.key() || code == SpearQuickLungeModule.key() || CombatAssistModule.isActivationKeyAssigned(code)) return false;
        for (var other : AzeiumModules.Module.values()) if (other != module && key(other) == code) return false;
        KEYS.put(module, code);
        LungeTestClient.saveKeybinds();
        return true;
    }
    public static void unbind(AzeiumModules.Module module) {
        KEYS.put(module, GLFW.GLFW_KEY_UNKNOWN);
        LungeTestClient.saveKeybinds();
    }
    public static boolean bindMenu(int code) {
        if (!validKey(code) || menu == null || code == AutoRejoinModule.key() || code == SpearQuickLungeModule.key() || KEYS.containsValue(code) || CombatAssistModule.isActivationKeyAssigned(code)) return false;
        menu.setKey(InputConstants.getKey(new KeyEvent(code, 0, 0)));
        KeyMapping.resetMapping();
        LungeTestClient.saveKeybinds();
        Minecraft.getInstance().options.save();
        return true;
    }
    public static Map<String, Integer> snapshot() {
        var result = new LinkedHashMap<String, Integer>();
        for (var module : AzeiumModules.Module.values()) result.put(module.name(), key(module));
        return result;
    }
    public static boolean handleModuleKey(Minecraft client, int action, KeyEvent event) {
        if (SpearQuickLungeModule.handleKey(client, action, event)) return true;
        if (AutoRejoinModule.handleKey(client, action, event)) return true;
        if (CombatAssistModule.handleActivationKey(client, action, event)) return true;
        if (client.screen != null || client.level == null || client.player == null
                || !client.player.isAlive() || client.getConnection() == null || menuMatches(event)) return false;
        for (var module : AzeiumModules.Module.values()) {
            if (key(module) == GLFW.GLFW_KEY_UNKNOWN || key(module) != event.key()) continue;
            if (action == GLFW.GLFW_PRESS && module.canToggle(client)) module.toggle(client);
            // Release is forwarded so vanilla cannot get stuck with a held movement key.
            return action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT;
        }
        return false;
    }
    private static String display(int code) {
        return code == GLFW.GLFW_KEY_UNKNOWN ? "None" : InputConstants.getKey(new KeyEvent(code, 0, 0)).getDisplayName().getString();
    }
}
