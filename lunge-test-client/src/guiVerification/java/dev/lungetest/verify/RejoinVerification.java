package dev.lungetest.verify;

import dev.lungetest.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

/** Session detection and cancellation use actual client screens; no connection attempt is dispatched. */
public final class RejoinVerification {
    private static int checks;
    public static void verify(Minecraft client) {
        var previousPlayer = client.player;
        var previousLevel = client.level;
        var previousScreen = client.screen;
        int previousKey = AutoRejoinModule.key();
        boolean previousEnabled = AutoRejoinModule.enabled();
        try {
            var f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true);
            var unsafe = (Unsafe) f.get(null);
            var connection = (RejoinConnection) unsafe.allocateInstance(RejoinConnection.class);
            connection.server = new ServerData("Fixture server", "127.0.0.1:25565", ServerData.Type.OTHER);
            var player = (LocalPlayer) unsafe.allocateInstance(LocalPlayer.class);
            var connectionField = LocalPlayer.class.getField("connection"); connectionField.setAccessible(true);
            connectionField.set(player, connection);
            var level = (ManualAttackVerification.FixtureLevel) unsafe.allocateInstance(ManualAttackVerification.FixtureLevel.class);
            var retryField = AutoRejoinModule.class.getDeclaredField("retries"); retryField.setAccessible(true);
            var schedule = (RejoinSchedule) retryField.get(null);
            checks = 0;
            AutoRejoinModule.bindKey(GLFW.GLFW_KEY_UNKNOWN);
            require(AutoRejoinModule.bindKey(GLFW.GLFW_KEY_F16), "Manual rejoin key accepts a supported unused key");
            require(!AzeiumKeybinds.bindMenu(GLFW.GLFW_KEY_F16), "Menu binding rejects the manual rejoin key");
            require(!AzeiumKeybinds.bind(AzeiumModules.Module.ZOOM, GLFW.GLFW_KEY_F16), "Module binding rejects the manual rejoin key");
            require(!CombatAssistModule.bindActivationKey(CombatAssistModule.Feature.MACE, GLFW.GLFW_KEY_F16),
                    "Combat use key rejects the manual rejoin key");
            require(!AutoRejoinModule.bindKey(AzeiumKeybinds.menuKey()), "Rejoin binding rejects the menu key");
            AutoRejoinModule.setEnabled(true);
            client.player = player; client.level = level; client.screen = null;
            AutoRejoinModule.tick(client);
            require(AutoRejoinModule.canRejoin(client) && !schedule.pending(), "A connected remote session is remembered without scheduling a reconnect");
            client.player = null; client.level = null;
            client.screen = new DisconnectedScreen(new TitleScreen(), Component.literal("Disconnected"), Component.literal("Fixture connection lost"));
            AutoRejoinModule.tick(client);
            require(schedule.pending() && schedule.attempts() == 0, "A lost remote session starts a delayed retry instead of an immediate connection");
            AutoRejoinModule.tick(client);
            require(schedule.pending() && schedule.attempts() == 0, "Repeated disconnected frames do not reset or burst the retry sequence");
            client.screen = new TitleScreen(); AutoRejoinModule.tick(client);
            require(!schedule.pending(), "Going Back cancels the scheduled automatic retry");
            client.player = player; client.level = level; client.screen = null; AutoRejoinModule.tick(client);
            client.player = null; client.level = null; client.screen = new TitleScreen(); AutoRejoinModule.tick(client);
            require(!schedule.pending(), "Intentionally leaving to the title screen never schedules a reconnect");
            client.player = player; client.level = level; client.screen = null; AutoRejoinModule.tick(client);
            client.player = null; client.level = null;
            client.screen = new DisconnectedScreen(new TitleScreen(), Component.literal("Disconnected"), Component.literal("Fixture connection lost"));
            AutoRejoinModule.tick(client); AutoRejoinModule.setEnabled(false);
            require(!schedule.pending(), "Disabling Auto Rejoin cancels its pending retry");
            connection.server = new ServerData("Fixture realm", "127.0.0.1:25565", ServerData.Type.REALM);
            client.player = player; client.level = level; client.screen = null;
            require(!AutoRejoinModule.canRejoin(client), "A Realm is excluded from ordinary server reconnect handling");
            LungeTestClient.LOG.info("REJOIN_VERIFIED: {} checks; remote-session memory, delayed retries, intentional-exit/back/disable cancellation, Realm exclusion and key conflicts; no connection attempts", checks);
        } catch (ReflectiveOperationException e) { throw new AssertionError("Could not prepare rejoin session fixture", e); }
        finally {
            AutoRejoinModule.setEnabled(false);
            for (String field : new String[]{"lastServer", "failureScreen"}) {
                try { var f = AutoRejoinModule.class.getDeclaredField(field); f.setAccessible(true); f.set(null, null); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
            AutoRejoinModule.bindKey(GLFW.GLFW_KEY_UNKNOWN);
            AutoRejoinModule.bindKey(previousKey);
            AutoRejoinModule.setEnabled(previousEnabled);
            client.player = previousPlayer; client.level = previousLevel; client.screen = previousScreen;
        }
    }
    private static void require(boolean c, String message) { if (!c) throw new AssertionError(message); checks++; }
    public static final class RejoinConnection extends ClientPacketListener {
        ServerData server;
        private RejoinConnection() { super(null, null, null); }
        @Override public ServerData getServerData() { return server; }
    }
}
