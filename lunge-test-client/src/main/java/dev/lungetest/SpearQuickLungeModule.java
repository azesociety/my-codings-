package dev.lungetest;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

/** One deliberate press queues one ordinary spear jab; temporary selection is always released. */
public final class SpearQuickLungeModule {
    private static int key = GLFW.GLFW_KEY_UNKNOWN, preferredSlot = -1;
    private static LocalPlayer requestedPlayer;
    private static ClientLevel requestedLevel;
    private static ClientPacketListener requestedConnection;
    private static boolean dispatching;
    private static int pendingPresses;
    private static String status = "Assign a quick-lunge key";
    private SpearQuickLungeModule() {}
    public static void initialize(int savedKey, int savedSlot) {
        key = available(savedKey) ? savedKey : GLFW.GLFW_KEY_UNKNOWN;
        preferredSlot = savedSlot >= -1 && savedSlot < 9 ? savedSlot : -1;
        cancel();
        // Fabric client initializers run before GLFW initialization. Key labels use
        // glfwGetKeyName internally, so resolve them only from the later GUI/input path.
        status = key == GLFW.GLFW_KEY_UNKNOWN ? "Assign a quick-lunge key" : "Quick lunge ready: spear jab + return";
    }
    public static int key() { return key; }
    public static int mouseCode(int button) { return -100 - button; }
    private static boolean mouseKey(int code) { return code <= -100 && code >= -100 - GLFW.GLFW_MOUSE_BUTTON_LAST; }
    public static String keyLabel() { return key == GLFW.GLFW_KEY_UNKNOWN ? "None"
            : mouseKey(key) ? "Mouse " + (-key - 99) : InputConstants.getKey(new KeyEvent(key, 0, 0)).getDisplayName().getString(); }
    public static int preferredSlot() { return preferredSlot; }
    public static String slotLabel() { return preferredSlot < 0 ? "Auto" : Integer.toString(preferredSlot + 1); }
    public static void cycleSlot() { preferredSlot = preferredSlot == 8 ? -1 : preferredSlot + 1; LungeTestClient.saveSpearPreferences(); }
    public static boolean dispatching() { return dispatching; }
    public static String status() { return key == GLFW.GLFW_KEY_UNKNOWN ? "Assign a quick-lunge key"
            : status; }
    private static boolean available(int code) {
        if (code == GLFW.GLFW_KEY_UNKNOWN || mouseKey(code)) return true;
        if (!AzeiumKeybinds.validKey(code) || code == AzeiumKeybinds.menuKey() || code == AutoRejoinModule.key()
                || CombatAssistModule.isActivationKeyAssigned(code)) return false;
        for (var module : AzeiumModules.Module.values()) if (AzeiumKeybinds.key(module) == code) return false;
        return true;
    }
    public static boolean bindKey(int code) {
        if (!available(code)) return false;
        key = code; cancel(); status = "Press " + keyLabel() + ": spear jab + return"; LungeTestClient.saveSpearPreferences(); return true;
    }
    public static boolean handleKey(Minecraft client, int action, KeyEvent event) {
        if (key == GLFW.GLFW_KEY_UNKNOWN || event.key() != key || client.screen != null) return false;
        if (action == GLFW.GLFW_PRESS && ready(client)) {
            if (client.player != requestedPlayer || client.level != requestedLevel || client.getConnection() != requestedConnection) cancel();
            requestedPlayer = client.player; requestedLevel = client.level; requestedConnection = client.getConnection();
            pendingPresses = Math.min(16, pendingPresses + 1);
        }
        return ready(client) && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT);
    }
    public static boolean handleMouse(Minecraft client, int action, int button) {
        if (!mouseKey(key) || mouseCode(button) != key || client.screen != null || !ready(client)) return false;
        if (action == GLFW.GLFW_PRESS) handleKey(client, action, new KeyEvent(key, 0, 0));
        return true;
    }
    private static boolean ready(Minecraft client) {
        return client.player != null && client.getConnection() != null && client.player.isAlive() && client.level != null && client.gameMode != null
                && client.screen == null && client.isWindowActive() && !client.isPaused() && !client.player.isSpectator()
                && !client.player.isUsingItem() && !client.player.isHandsBusy();
    }
    public static void cancel() { requestedPlayer = null; requestedLevel = null; requestedConnection = null; pendingPresses = 0; }
    private static int spearSlot(LocalPlayer player) {
        var inventory = player.getInventory();
        if (preferredSlot >= 0) return LungeTestClient.isSpear(inventory.getItem(preferredSlot)) ? preferredSlot : -1;
        int selected = inventory.getSelectedSlot();
        if (LungeTestClient.isSpear(inventory.getItem(selected))) return selected;
        for (int slot = 0; slot < 9; slot++) if (LungeTestClient.isSpear(inventory.getItem(slot))) return slot;
        return -1;
    }
    public static void tick(Minecraft client) {
        if (requestedPlayer == null) return;
        boolean valid = ready(client) && client.player == requestedPlayer && client.level == requestedLevel
                && client.getConnection() == requestedConnection;
        int presses = pendingPresses;
        var owner = requestedPlayer; var level = requestedLevel; var connection = requestedConnection;
        cancel();
        if (!valid) { status = "Quick lunge cancelled"; return; }
        for (int press = 0; press < presses; press++) {
            if (!ready(client) || client.player != owner || client.level != level || client.getConnection() != connection) break;
            perform(client);
        }
    }
    private static void perform(Minecraft client) {
        VisibleSwapReturn.finishNow();
        var player = client.player;
        int slot = spearSlot(player);
        if (slot < 0) { status = "Put a spear in " + (preferredSlot < 0 ? "your hotbar" : "slot " + slotLabel()); return; }
        var stack = player.getInventory().getItem(slot);
        if (player.getCooldowns().isOnCooldown(stack)) { status = "Spear item cooldown is active"; return; }
        int originalSlot = player.getInventory().getSelectedSlot();
        var connection = client.getConnection();
        player.getInventory().setSelectedSlot(slot);
        dispatching = true;
        try {
            boolean started = ((SpearAttackAccess) client).azeium$startAttack();
            status = started ? LungeTestClient.legitMode() && LungeTestClient.spearReturnTicks() > 0
                    ? "Spear jab sent; visible return in " + LungeTestClient.spearReturnTicks() + " ticks"
                    : "Spear jab sent; slot restored" : "Vanilla attack was unavailable";
            CombatTestLog.event("Spear", "quick_lunge", "slot=" + slot + ",return=" + originalSlot + ",started=" + started);
        } finally {
            dispatching = false;
            Runnable restore = () -> {
                if (client.player == player && client.getConnection() == connection && client.gameMode != null
                        && player.getInventory().getSelectedSlot() == slot) {
                    player.getInventory().setSelectedSlot(originalSlot);
                    if (VanillaInputPhase.active()) ((HotbarSyncAccess) client.gameMode).azeium$syncCarriedItem();
                }
            };
            if (LungeTestClient.legitMode()) VisibleSwapReturn.defer("spear", restore, LungeTestClient.spearReturnTicks());
            else restore.run();
        }
    }
}
