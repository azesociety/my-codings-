package dev.lungetest;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.lwjgl.glfw.GLFW;

/** Reuses vanilla connection handling and remembers only this session's ordinary multiplayer server. */
public final class AutoRejoinModule {
    private static final RejoinSchedule retries = new RejoinSchedule();
    private static ServerData lastServer;
    private static Screen failureScreen;
    private static boolean enabled, wasConnected, retryAllowed;
    private static int rejoinKey = GLFW.GLFW_KEY_UNKNOWN;
    private static String status = "Join a multiplayer server first";
    private AutoRejoinModule() {}
    public static void initialize(int savedKey) { rejoinKey = availableKey(savedKey) ? savedKey : GLFW.GLFW_KEY_UNKNOWN; }
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) {
        enabled = value;
        retries.reset();
        failureScreen = null;
        retryAllowed = false;
        status = value ? "Reconnect after a lost connection" : "Auto Rejoin disabled";
    }
    public static String status() { return status; }
    public static int key() { return rejoinKey; }
    public static String keyLabel() { return rejoinKey == GLFW.GLFW_KEY_UNKNOWN ? "None"
            : InputConstants.getKey(new KeyEvent(rejoinKey, 0, 0)).getDisplayName().getString(); }
    private static boolean availableKey(int code) {
        if (code == GLFW.GLFW_KEY_UNKNOWN) return true;
        if (!AzeiumKeybinds.validKey(code) || code == SpearQuickLungeModule.key() || code == AzeiumKeybinds.menuKey()
                || CombatAssistModule.isActivationKeyAssigned(code)) return false;
        for (var module : AzeiumModules.Module.values()) if (AzeiumKeybinds.key(module) == code) return false;
        return true;
    }
    public static boolean bindKey(int code) {
        if (!availableKey(code)) return false;
        rejoinKey = code;
        LungeTestClient.saveRejoinKey();
        return true;
    }
    private static boolean remoteSession(Minecraft client) {
        ServerData server = client.getCurrentServer();
        return client.level != null && client.getConnection() != null && server != null
                && !client.isLocalServer() && !server.isRealm();
    }
    private static void remember(ServerData server) {
        lastServer = new ServerData(server.name, server.ip, server.type());
        lastServer.copyFrom(server);
    }
    public static boolean canRejoin(Minecraft client) {
        return !(client.screen instanceof ConnectScreen) && !client.isLocalServer()
                && (remoteSession(client) || lastServer != null && client.level == null);
    }
    public static boolean rejoin(Minecraft client) {
        if (!canRejoin(client)) { status = "Join a multiplayer server first"; return false; }
        if (remoteSession(client)) remember(client.getCurrentServer());
        wasConnected = false;
        retryAllowed = true;
        failureScreen = null;
        retries.reset();
        connect(client);
        return true;
    }
    private static void connect(Minecraft client) {
        status = "Rejoining " + lastServer.name;
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client,
                ServerAddress.parseString(lastServer.ip), lastServer, false, null);
    }
    public static void tick(Minecraft client) {
        long now = System.nanoTime();
        if (remoteSession(client)) {
            if (!wasConnected || lastServer == null || !lastServer.ip.equals(client.getCurrentServer().ip))
                remember(client.getCurrentServer());
            wasConnected = true;
            retryAllowed = false;
            failureScreen = null;
            retries.reset();
            status = enabled ? "Connected: Auto Rejoin ready" : "Auto Rejoin disabled";
            return;
        }
        if (wasConnected) {
            wasConnected = false;
            retryAllowed = client.screen instanceof DisconnectedScreen;
        }
        if (!enabled || !retryAllowed || lastServer == null) return;
        if (client.screen instanceof DisconnectedScreen) {
            if (failureScreen != client.screen) {
                failureScreen = client.screen;
                retries.schedule(now);
            }
            if (retries.poll(now)) connect(client);
            else status = retries.pending() ? "Rejoin in " + retries.remainingSeconds(now) + "s ("
                    + (retries.attempts() + 1) + "/" + RejoinSchedule.MAX_ATTEMPTS + ")"
                    : "Retry limit reached: use Rejoin to try again";
        } else if (!(client.screen instanceof ConnectScreen)) {
            // Going Back, cancelling a connection or intentionally leaving ends this retry sequence.
            retryAllowed = false;
            failureScreen = null;
            retries.reset();
        }
    }
    public static boolean handleKey(Minecraft client, int action, KeyEvent event) {
        if (rejoinKey == GLFW.GLFW_KEY_UNKNOWN || event.key() != rejoinKey || client.screen != null
                || client.player == null || client.level == null || !client.player.isAlive()) return false;
        if (action == GLFW.GLFW_PRESS) rejoin(client);
        return action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT;
    }
}
