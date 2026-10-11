package dev.lungetest.verify;

import dev.lungetest.LungeTestClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;
import java.nio.file.Files;
import java.nio.file.Path;

/** Simulates deliberate vanilla clicks with an attack fixture that has no network transport. */
public final class ManualAttackVerification {
    private static int checks;

    public static void verify(Minecraft client) {
        checks = 0;
        var previousPlayer = client.player;
        var previousLevel = client.level;
        var previousGameMode = client.gameMode;
        var previousScreen = client.screen;
        var previousHitResult = client.hitResult;
        try {
            require(!LungeTestClient.enabled(), "Manual fixture begins outside an armed session");
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (Unsafe) unsafeField.get(null);
            var local = (FixturePlayer) unsafe.allocateInstance(FixturePlayer.class);
            var connection = (ClientPacketListener) unsafe.allocateInstance(ClientPacketListener.class);
            var alternateConnection = (ClientPacketListener) unsafe.allocateInstance(ClientPacketListener.class);
            var localConnection = LocalPlayer.class.getField("connection");
            localConnection.setAccessible(true);
            localConnection.set(local, connection);
            local.held = new ItemStack(Items.DIAMOND_SPEAR);
            local.alive = true;
            var gameMode = (FixtureGameMode) unsafe.allocateInstance(FixtureGameMode.class);
            client.player = local;
            client.level = (FixtureLevel) unsafe.allocateInstance(FixtureLevel.class);
            client.gameMode = gameMode;
            client.screen = null;
            client.hitResult = BlockHitResult.miss(Vec3.ZERO, Direction.DOWN, BlockPos.ZERO);
            KeyMapping.releaseAll();

            var missTime = Minecraft.class.getDeclaredField("missTime");
            missTime.setAccessible(true);
            int previousMissTime = missTime.getInt(client);
            missTime.setInt(client, 0);
            try {
                var startAttack = Minecraft.class.getDeclaredMethod("startAttack");
                startAttack.setAccessible(true);
                ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(client);
                LungeTestClient.resetStats();
                require(!(boolean) startAttack.invoke(client), "An uncharged manual click is blocked while OFF");
                require(gameMode.attacks == 0, "OFF cannot dispatch an uncharged spear attack");
                require(LungeTestClient.attempts() == 1 && LungeTestClient.dispatched() == 0,
                        "OFF observes exactly one rejected baseline click");
                LungeTestClient.toggle(client);
                require(LungeTestClient.enabled(), "Cooldown removal enables in a live session");
                require(LungeTestClient.attempts() == 1 && LungeTestClient.dispatched() == 0,
                        "Enabling cooldown removal preserves baseline measurements");
                for (int tick = 0; tick < 40; tick++) ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(client);
                require(gameMode.attacks == 0 && LungeTestClient.attempts() == 1 && LungeTestClient.dispatched() == 0,
                        "Forty idle client ticks generate no automatic attacks or attempts");

                for (int click = 0; click < 10; click++) {
                    require((boolean) startAttack.invoke(client), "Each explicit manual click reaches vanilla piercing dispatch");
                }
                require(gameMode.attacks == 10, "Ten manual invocations produce exactly ten piercing dispatches");
                require(LungeTestClient.attempts() == 11 && LungeTestClient.dispatched() == 10,
                        "Observer adds exactly ten manual clicks and starts to the OFF baseline");
                client.screen = previousScreen;
                for (int tick = 0; tick < 40; tick++) ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(client);
                require(gameMode.attacks == 10 && LungeTestClient.attempts() == 11,
                        "An open GUI and idle ticks cannot generate clicks");
                client.screen = null;

                local.held = new ItemStack(Items.DIAMOND_SWORD);
                require(local.getAttackStrengthScale(0) == 0.0F, "Sword recharge remains vanilla while enabled");
                startAttack.invoke(client);
                require(LungeTestClient.attempts() == 11 && gameMode.attacks == 10,
                        "Other weapons do not enter spear observation or piercing dispatch");
                local.held = new ItemStack(Items.DIAMOND_SPEAR);
                LungeTestClient.recordManualAttack(client, false);
                require(gameMode.attacks == 10 && LungeTestClient.attempts() == 12 && LungeTestClient.dispatched() == 10,
                        "Recording a rejected click never dispatches an attack");

                LungeTestClient.disable();
                require(LungeTestClient.attempts() == 12 && LungeTestClient.dispatched() == 10,
                        "Disabling cooldown removal preserves measurements for comparison");
                require(!(boolean) startAttack.invoke(client), "Turning OFF restores the vanilla spear gate immediately");
                require(gameMode.attacks == 10 && LungeTestClient.attempts() == 13 && LungeTestClient.dispatched() == 10,
                        "OFF rejected clicks remain observable but never dispatch");
                LungeTestClient.recordPositionSync(connection);
                require(LungeTestClient.stats().positionSyncs() == 1 && gameMode.attacks == 10,
                        "An observed position synchronization adds a local counter without dispatching attacks");
                LungeTestClient.recordPositionSync(alternateConnection);
                require(LungeTestClient.stats().positionSyncs() == 1,
                        "Position updates from a different connection are ignored");
                var stats = LungeTestClient.stats();
                require(stats.clickHistory().length == 20 && stats.speedHistory().length == 20,
                        "Both five-second histories contain twenty samples");
                require(stats.clicksPerSecond() >= stats.startsPerSecond()
                                && stats.peakClicksPerSecond() >= stats.clicksPerSecond(),
                        "Click and started rates respect observed outcomes");
                verifyExport(client, 13, 10);
                LungeTestClient.resetStats();
                stats = LungeTestClient.stats();
                require(stats.attempts() == 0 && stats.started() == 0 && stats.clicksPerSecond() == 0
                                && stats.startsPerSecond() == 0 && stats.peakClicksPerSecond() == 0
                                && stats.speed() == 0 && stats.peakSpeed() == 0 && stats.distance() == 0
                                && stats.positionSyncs() == 0,
                        "Explicit Reset clears counters, peaks, movement and position-sync measurements");
                require(java.util.Arrays.stream(stats.clickHistory()).allMatch(value -> value == 0)
                                && java.util.Arrays.stream(stats.speedHistory()).allMatch(value -> value == 0),
                        "Explicit Reset clears both histories");

                LungeTestClient.toggle(client);
                require((boolean) startAttack.invoke(client) && LungeTestClient.attempts() == 1,
                        "A new explicit click after Reset starts a new measurement");
                client.level = (FixtureLevel) unsafe.allocateInstance(FixtureLevel.class);
                ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(client);
                require(LungeTestClient.attempts() == 0 && LungeTestClient.dispatched() == 0,
                        "Changing world identity resets measurements");
                require((boolean) startAttack.invoke(client) && LungeTestClient.attempts() == 1,
                        "Manual observations continue in the new world");
                localConnection.set(local, alternateConnection);
                ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(client);
                require(!LungeTestClient.enabled() && LungeTestClient.attempts() == 0 && LungeTestClient.dispatched() == 0,
                        "Changing connection disarms cooldown removal and resets measurements");
                localConnection.set(local, connection);
                ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(client);
                LungeTestClient.toggle(client);
                local.alive = false;
                ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(client);
                require(!LungeTestClient.enabled(), "Death turns cooldown removal OFF");
                local.alive = true;
                LungeTestClient.toggle(client);
                localConnection.set(local, alternateConnection);
                ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(client);
                require(!LungeTestClient.enabled(), "A changed connection cannot inherit the armed session");
                require(gameMode.attacks == 12, "Only the twelve explicit successful manual clicks dispatched attacks");
                LungeTestClient.LOG.info("MANUAL_ATTACK_VERIFIED: {} runtime checks passed; explicit clicks use vanilla startAttack, idle ticks dispatch nothing, no network transport", checks);
            } finally {
                missTime.setInt(client, previousMissTime);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not exercise the Fabric-transformed manual attack method", e);
        } finally {
            LungeTestClient.disable();
            client.player = previousPlayer;
            client.level = previousLevel;
            client.gameMode = previousGameMode;
            client.screen = previousScreen;
            client.hitResult = previousHitResult;
            LungeTestClient.resetStats();
        }
    }

    private static void verifyExport(Minecraft client, long attempts, long started) {
        Path exported = null;
        try {
            var message = LungeTestClient.exportStats(client);
            exported = LungeTestClient.lastExport();
            require(message != null && !message.isBlank() && exported != null && Files.isRegularFile(exported),
                    "Export writes a readable local JSON artifact");
            var json = JsonParser.parseString(Files.readString(exported));
            require(json.isJsonObject(), "Export has a JSON object root");
            require(find(json, "attempts").getAsLong() == attempts && find(json, "started").getAsLong() == started,
                    "Export contains the exact observed baseline and manual-start totals");
            require(find(json, "clickHistory").getAsJsonArray().size() == 20
                            && find(json, "speedHistory").getAsJsonArray().size() == 20,
                    "Export contains both complete histories");
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not inspect the local export artifact", e);
        } finally {
            if (exported != null) {
                try { Files.deleteIfExists(exported); }
                catch (java.io.IOException e) { throw new AssertionError("Could not remove fixture export", e); }
            }
        }
    }

    private static JsonElement find(JsonElement value, String name) {
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has(name)) return object.get(name);
            for (var child : object.entrySet()) {
                var found = find(child.getValue(), name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public static final class FixturePlayer extends LocalPlayer {
        ItemStack held;
        boolean alive;
        private FixturePlayer() { super(null, null, null, null, null, null, false); }
        @Override public ItemStack getMainHandItem() { return held; }
        @Override public ItemStack getItemInHand(InteractionHand hand) { return held; }
        @Override public double getAttributeValue(Holder<Attribute> attribute) { return 4.0; }
        @Override public Vec3 position() { return Vec3.ZERO; }
        @Override public Vec3 getDeltaMovement() { return Vec3.ZERO; }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isUsingItem() { return false; }
        @Override public boolean isHandsBusy() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public void swing(InteractionHand hand) { }
    }

    public static final class FixtureLevel extends ClientLevel {
        private FixtureLevel() { super(null, null, null, null, 0, 0, null, false, 0L, 0); }
        @Override public FeatureFlagSet enabledFeatures() { return FeatureFlags.DEFAULT_FLAGS; }
    }

    public static final class FixtureGameMode extends MultiPlayerGameMode {
        int attacks;
        private FixtureGameMode() { super(null, null); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean hasMissTime() { return false; }
        @Override public void piercingAttack(PiercingWeapon weapon) { attacks++; }
        @Override public void stopDestroyBlock() { }
    }
}
