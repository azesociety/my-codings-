package dev.lungetest.verify;

import dev.lungetest.AutoClutchModule;
import dev.lungetest.LungeTestClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.HashMap;

import static dev.lungetest.verify.ClutchPlacementVerification.CapturedConnection;
import static dev.lungetest.verify.ClutchPlacementVerification.PlacementLevel;
import static dev.lungetest.verify.ClutchPlacementVerification.PlacementPlayer;

/** Actual vanilla placement during supported jump bridging, with captured local transport. */
public final class TellyBridgeVerification {
    private static int checks;
    private TellyBridgeVerification() {}

    public static void verify(Minecraft client) {
        var previousPlayer = client.player;
        var previousLevel = client.level;
        var previousGameMode = client.gameMode;
        var previousScreen = client.screen;
        var previousHit = client.hitResult;
        boolean previousEnabled = AutoClutchModule.enabled();
        var previousMode = AutoClutchModule.mode();
        var previousSpeed = AutoClutchModule.tellySpeed();
        checks = 0;
        try {
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (Unsafe) unsafeField.get(null);
            var connection = (CapturedConnection) unsafe.allocateInstance(CapturedConnection.class);
            connection.packets = new ArrayList<>();
            var player = (PlacementPlayer) unsafe.allocateInstance(PlacementPlayer.class);
            var connectionField = LocalPlayer.class.getField("connection");
            connectionField.setAccessible(true);
            connectionField.set(player, connection);
            player.inventory = new Inventory(player, new EntityEquipment());
            player.abilities = new Abilities();
            player.cooldowns = new ItemCooldowns();
            player.offhand = ItemStack.EMPTY;
            player.velocity = Vec3.ZERO;
            player.alive = true;
            player.setYRot(37);
            player.setXRot(-12);
            set(Entity.class, player, "eyeHeight", 1.62F);
            var level = (PlacementLevel) unsafe.allocateInstance(PlacementLevel.class);
            level.states = new HashMap<>();
            level.border = new WorldBorder();
            level.environment = EnvironmentAttributeSystem.builder()
                    .addConstantLayer(EnvironmentAttributes.WATER_EVAPORATES, ignored -> false).build();
            level.player = player;
            set(Level.class, level, "isClientSide", true);
            set(ClientLevel.class, level, "blockStatePredictionHandler", new BlockStatePredictionHandler());
            player.fixtureLevel = level;
            client.player = player;
            client.level = level;
            client.gameMode = new MultiPlayerGameMode(client, connection);
            client.screen = null;
            player.inventory.setItem(0, new ItemStack(Items.DIAMOND_SPEAR));
            player.inventory.setItem(4, new ItemStack(Items.WATER_BUCKET));
            player.inventory.setItem(6, new ItemStack(Items.COBBLESTONE, 64));
            player.inventory.setSelectedSlot(0);

            anchor(client, player, level, connection);
            for (int cell = 0; cell < 9; cell++) {
                double vertical = switch (cell % 3) { case 0 -> 0.28; case 1 -> 0; default -> -0.18; };
                locate(player, new Vec3(cell + 0.2, 4.2, 0.5));
                player.grounded = false;
                player.fallDistance = vertical < 0 ? 0.8 : 0;
                player.velocity = new Vec3(0.35, vertical, 0);
                int supplies = player.inventory.getItem(6).getCount();
                long packets = blockUses(connection);
                // tick is the once-per-client-tick maintenance callback. Movement may run
                // more than once in laggy frames, but all callbacks share one use budget.
                AutoClutchModule.tick(client);
                for (int callback = 0; callback < 5; callback++)
                    AutoClutchModule.beforeMove(client, player.velocity);
                require(level.getBlockState(new BlockPos(cell, 2, 0)).is(Blocks.COBBLESTONE),
                        "Real vanilla TELLY placement extends the grounded bridge floor while "
                                + (vertical > 0 ? "ascending" : vertical == 0 ? "at the jump apex" : "descending"));
                require(blockUses(connection) == packets + 1 && player.inventory.getItem(6).getCount() == supplies - 1,
                        "One real survival block and ordinary use-on packet per client tick across repeated movement callbacks");
                require(level.states.keySet().stream().allMatch(pos -> pos.getY() == 2),
                        "Airborne placement preserves the last grounded bridge height and creates no jump tower");
                require(player.inventory.getSelectedSlot() == 0 && player.getYRot() == 37 && player.getXRot() == -12,
                        "Jump bridging restores the selected spear and original camera after real item use");
                require(player.velocity.equals(new Vec3(0.35, vertical, 0))
                                && player.fallDistance == (vertical < 0 ? 0.8 : 0),
                        "Jump bridging preserves physical velocity and fall distance");
            }
            require(player.inventory.getItem(6).getCount() == 55 && blockUses(connection) == 9,
                    "Confirmed supported bridge placements continue beyond the old six-attempt fall budget");
            long confirmedUses = blockUses(connection);
            for (int tick = 0; tick < 5; tick++) {
                AutoClutchModule.tick(client);
                AutoClutchModule.beforeMove(client, player.velocity);
            }
            require(blockUses(connection) == confirmedUses,
                    "Already confirmed bridge cells are observed without repeated placement packets");

            anchor(client, player, level, connection);
            player.grounded = false;
            player.velocity = new Vec3(1.65, 0.15, 0);
            for (int cell = 0; cell < 3; cell++) {
                // The first two callbacks report the same already advanced position, as
                // can happen when rendering lags behind the most recent movement.
                locate(player, new Vec3(cell < 2 ? 1.5 : 2.5, 4.0, 0.5));
                long before = blockUses(connection);
                AutoClutchModule.tick(client);
                for (int callback = 0; callback < 6; callback++)
                    AutoClutchModule.beforeMove(client, player.velocity);
                require(level.getBlockState(new BlockPos(cell, 2, 0)).is(Blocks.COBBLESTONE),
                        "Bounded lag-gap bridging fills the next supported cell instead of skipping an unsupported gap");
                require(blockUses(connection) == before + 1,
                        "Fast horizontal movement extends exactly one supported bridge cell per client tick");
            }
            require(player.inventory.getItem(6).getCount() == 61,
                    "Three fast-movement bridge cells consume three actual survival blocks");

            // A lunge can cross several cells before the next movement callback. Every
            // placement below runs the real survival BlockItem prediction path; newly
            // confirmed support supplies the next visible face in the same client tick.
            for (AutoClutchModule.TellySpeed speed : AutoClutchModule.TellySpeed.values()) {
                anchor(client, player, level, connection, AutoClutchModule.Mode.TELLY, speed);
                locate(player, new Vec3(3.2, 3.4, 0.5));
                player.grounded = false;
                player.velocity = new Vec3(1.6, 0.2, 0);
                int expected = switch (speed) { case CAUTIOUS -> 1; case NORMAL -> 2; case FAST -> 4; };
                AutoClutchModule.tick(client);
                for (int callback = 0; callback < 12; callback++)
                    AutoClutchModule.beforeMove(client, player.velocity);
                require(blockUses(connection) == expected,
                        speed + " shares its " + expected + " ordinary-use budget across repeated movement callbacks");
                require(player.inventory.getItem(6).getCount() == 64 - expected,
                        speed + " spends one actual survival block for every confirmed lunge-gap placement");
                for (int cell = 0; cell < expected; cell++)
                    require(level.getBlockState(new BlockPos(cell, 2, 0)).is(Blocks.COBBLESTONE),
                            speed + " fills adjacent lunge-gap cells using support confirmed by the previous vanilla placement");
                require(level.getBlockState(new BlockPos(expected, 2, 0)).isAir(),
                        speed + " does not exceed its per-client-tick placement cap");
                require(level.states.keySet().stream().allMatch(pos -> pos.getY() == 2),
                        speed + " keeps all rapid lunge placements at the remembered bridge floor");
                require(player.inventory.getSelectedSlot() == 0 && player.getYRot() == 37 && player.getXRot() == -12,
                        speed + " restores the selected spear and camera after each rapid ordinary interaction");
                require(player.velocity.equals(new Vec3(1.6, 0.2, 0)) && player.fallDistance == 0,
                        speed + " preserves lunge movement and fall data");
                require(AutoClutchModule.actedThisTick(),
                        speed + " reports placement to the shared combat action guard");
            }

            anchor(client, player, level, connection, AutoClutchModule.Mode.TELLY_ONLY,
                    AutoClutchModule.TellySpeed.FAST);
            locate(player, new Vec3(3.2, 3.4, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(1.6, 0.2, 0);
            AutoClutchModule.tick(client);
            for (int callback = 0; callback < 12; callback++)
                AutoClutchModule.beforeMove(client, player.velocity);
            require(blockUses(connection) == 4 && player.inventory.getItem(6).getCount() == 60,
                    "Telly-only mode makes four actual supported bridge placements during an ascending lunge");
            require(connection.packets.stream().noneMatch(ServerboundUseItemPacket.class::isInstance)
                            && player.inventory.getItem(4).is(Items.WATER_BUCKET),
                    "Telly-only bridging does not invoke a bucket while placing blocks");

            anchor(client, player, level, connection, AutoClutchModule.Mode.TELLY_ONLY,
                    AutoClutchModule.TellySpeed.FAST);
            locate(player, new Vec3(2.2, 3.4, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(0, 0.2, 0);
            AutoClutchModule.tick(client);
            for (int callback = 0; callback < 12; callback++)
                AutoClutchModule.beforeMove(client, player.velocity);
            require(blockUses(connection) == 3 && player.inventory.getItem(6).getCount() == 61,
                    "A three-cell accumulated lunge gap is filled in one tick without fabricating a fourth target");
            require(level.getBlockState(new BlockPos(2, 2, 0)).is(Blocks.COBBLESTONE)
                            && level.getBlockState(new BlockPos(3, 2, 0)).isAir(),
                    "Fast bridging reaches the current foot cell and stops where the measured path ends");

            anchor(client, player, level, connection, AutoClutchModule.Mode.TELLY_ONLY,
                    AutoClutchModule.TellySpeed.FAST);
            player.inventory.setItem(6, new ItemStack(Items.COBBLESTONE, 2));
            locate(player, new Vec3(3.2, 3.4, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(1.6, 0.2, 0);
            AutoClutchModule.tick(client);
            for (int callback = 0; callback < 12; callback++)
                AutoClutchModule.beforeMove(client, player.velocity);
            require(blockUses(connection) == 2 && player.inventory.getItem(6).isEmpty(),
                    "Fast mode stops as soon as its two real survival blocks are exhausted");
            require(level.getBlockState(new BlockPos(1, 2, 0)).is(Blocks.COBBLESTONE)
                            && level.getBlockState(new BlockPos(2, 2, 0)).isAir(),
                    "Depleted supply cannot create the remaining lunge-gap bridge cells");

            anchor(client, player, level, connection, AutoClutchModule.Mode.TELLY_ONLY,
                    AutoClutchModule.TellySpeed.FAST);
            locate(player, new Vec3(3.2, 3.4, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(Double.NaN, 0.2, 0);
            AutoClutchModule.tick(client);
            AutoClutchModule.beforeMove(client, player.velocity);
            require(blockUses(connection) == 0 && player.inventory.getItem(6).getCount() == 64,
                    "Non-finite motion is rejected before any fast-mode placement or survival consumption");

            // Fresh fixtures deliberately reject the real BlockItem world write. Retries
            // still execute the vanilla item path; they cannot generate a success result.
            anchor(client, player, level, connection);
            AutoClutchModule.setTellySpeed(AutoClutchModule.TellySpeed.FAST);
            locate(player, new Vec3(0.2, 4.2, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(0.35, 0.2, 0);
            level.dropBlockWrite = true;
            level.reportDroppedWriteSuccessful = false;
            level.droppedBlockWrites = 0;
            int lastAttemptTick = -100;
            int attemptedTicks = 0;
            for (int tick = 0; tick < 20; tick++) {
                long before = blockUses(connection);
                AutoClutchModule.tick(client);
                for (int callback = 0; callback < 8; callback++)
                    AutoClutchModule.beforeMove(client, player.velocity);
                long sent = blockUses(connection) - before;
                require(sent <= 1, "Even fast mode stops its current tick after a rejected support prediction");
                if (sent != 0) {
                    require(tick - lastAttemptTick >= 2, "Failed bridge predictions have a two-client-tick retry gap");
                    lastAttemptTick = tick;
                    attemptedTicks++;
                }
            }
            require(attemptedTicks == 4 && level.droppedBlockWrites == 4 && blockUses(connection) == 4,
                    "The same rejected bridge target has four actual vanilla attempts, then stops");
            require(level.getBlockState(new BlockPos(0, 2, 0)).isAir()
                            && player.inventory.getItem(6).getCount() == 64,
                    "Rejected vanilla writes create no block and consume no survival supply");
            level.dropBlockWrite = false;

            anchor(client, player, level, connection);
            AutoClutchModule.setTellySpeed(AutoClutchModule.TellySpeed.FAST);
            locate(player, new Vec3(0.2, 4.2, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(0.35, 0.2, 0);
            level.dropBlockWrite = true;
            level.reportDroppedWriteSuccessful = true;
            for (int tick = 0; tick < 20; tick++) {
                AutoClutchModule.tick(client);
                AutoClutchModule.beforeMove(client, player.velocity);
            }
            require(blockUses(connection) == 4 && level.droppedBlockWrites == 4
                            && level.getBlockState(new BlockPos(0, 2, 0)).isAir(),
                    "A consuming vanilla interaction result without confirmed world support still has a bounded retry cap");
            require(player.inventory.getItem(6).getCount() == 60,
                    "Lost successful local predictions are measured through actual vanilla survival consumption");
            level.dropBlockWrite = false;

            anchor(client, player, level, connection);
            player.inventory.setItem(6, ItemStack.EMPTY);
            locate(player, new Vec3(0.2, 4.2, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(0.35, 0.2, 0);
            AutoClutchModule.tick(client);
            AutoClutchModule.beforeMove(client, player.velocity);
            require(blockUses(connection) == 0 && level.getBlockState(new BlockPos(0, 2, 0)).isAir(),
                    "Jump bridging without hotbar or offhand blocks synthesizes no inventory or placement");

            anchor(client, player, level, connection);
            locate(player, new Vec3(0.2, 9.0, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(0.35, 0.2, 0);
            AutoClutchModule.tick(client);
            AutoClutchModule.beforeMove(client, player.velocity);
            require(blockUses(connection) == 0, "A remembered bridge outside normal vanilla interaction reach receives no use packet");

            anchor(client, player, level, connection);
            level.states.clear();
            locate(player, new Vec3(0.2, 4.2, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(0.35, 0.2, 0);
            AutoClutchModule.tick(client);
            AutoClutchModule.beforeMove(client, player.velocity);
            require(blockUses(connection) == 0 && level.states.isEmpty(),
                    "A cached bridge height cannot place blocks in the air after all supported faces disappear");

            anchor(client, player, level, connection);
            for (int cell = 0; cell < 4; cell++)
                level.states.put(new BlockPos(cell, 2, 0), Blocks.STONE.defaultBlockState());
            locate(player, new Vec3(0.2, 4.2, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(0.35, 0.2, 0);
            AutoClutchModule.tick(client);
            AutoClutchModule.beforeMove(client, player.velocity);
            require(blockUses(connection) == 0 && level.getBlockState(new BlockPos(0, 2, 0)).is(Blocks.STONE),
                    "Occupied supported bridge cells are preserved and do not trigger item use");

            // A deep fall uses the ordinary bucket fallback in this same bridging mode.
            anchor(client, player, level, connection);
            level.states.clear();
            level.states.put(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            locate(player, new Vec3(0.5, 3, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(0, -0.8, 0);
            player.fallDistance = 5;
            AutoClutchModule.tick(client);
            for (int callback = 0; callback < 4; callback++)
                AutoClutchModule.beforeMove(client, player.velocity);
            require(level.getFluidState(BlockPos.ZERO.above()).is(Fluids.WATER)
                            && level.getFluidState(BlockPos.ZERO.above()).isSource(),
                    "TELLY mode retains real vanilla water-source fallback for a dangerous fall");
            require(player.inventory.getItem(4).is(Items.BUCKET)
                            && connection.packets.stream().filter(ServerboundUseItemPacket.class::isInstance).count() == 1,
                    "The deep-fall fallback transforms the real filled bucket and sends one ordinary use packet");
            require(player.velocity.equals(new Vec3(0, -0.8, 0)) && player.fallDistance == 5,
                    "TELLY water fallback preserves physical fall data");
            require(player.inventory.getSelectedSlot() == 0 && player.getYRot() == 37 && player.getXRot() == -12,
                    "TELLY water fallback restores the original selected item and camera");

            anchor(client, player, level, connection, AutoClutchModule.Mode.TELLY_ONLY,
                    AutoClutchModule.TellySpeed.FAST);
            level.states.clear();
            level.states.put(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            locate(player, new Vec3(0.5, 3, 0.5));
            player.grounded = false;
            player.velocity = new Vec3(0, -0.8, 0);
            player.fallDistance = 5;
            AutoClutchModule.tick(client);
            for (int callback = 0; callback < 12; callback++)
                AutoClutchModule.beforeMove(client, player.velocity);
            require(blockUses(connection) == 0
                            && connection.packets.stream().noneMatch(ServerboundUseItemPacket.class::isInstance),
                    "Telly-only mode makes no water or generic block catch during a dangerous fall below its bridge");
            require(player.inventory.getItem(4).is(Items.WATER_BUCKET)
                            && player.inventory.getItem(6).getCount() == 64
                            && level.getBlockState(BlockPos.ZERO.above()).isAir(),
                    "Telly-only falling consumes no bucket or blocks and leaves the landing cell unchanged");
            require(player.velocity.equals(new Vec3(0, -0.8, 0)) && player.fallDistance == 5,
                    "Telly-only falling preserves physical fall data");
            require(player.inventory.getSelectedSlot() == 0 && player.getYRot() == 37 && player.getXRot() == -12,
                    "Telly-only falling preserves the selected spear and original camera");
            LungeTestClient.LOG.info("TELLY_BRIDGE_VERIFIED: {} checks; actual vanilla BlockItem/BucketItem placement, ascending/apex/descending bridge height, nine continuous survival placements, cautious/normal/fast shared 1/2/4-use budgets, four same-tick adjacent lunge-gap placements, Telly-only without fall fallback, confirmed-cell observations, failed-prediction retry gap/cap, supply/reach/support/occupied-cell guards and combined long-fall water fallback; captured packets, no network transport", checks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not prepare actual vanilla TELLY bridge placement fixture", e);
        } finally {
            AutoClutchModule.setEnabled(false);
            AutoClutchModule.setMode(previousMode);
            AutoClutchModule.setTellySpeed(previousSpeed);
            client.player = previousPlayer;
            client.level = previousLevel;
            client.gameMode = previousGameMode;
            client.screen = previousScreen;
            client.hitResult = previousHit;
            AutoClutchModule.setEnabled(previousEnabled);
        }
    }

    private static void anchor(Minecraft client, PlacementPlayer player, PlacementLevel level,
                               CapturedConnection connection) throws ReflectiveOperationException {
        anchor(client, player, level, connection, AutoClutchModule.Mode.TELLY,
                AutoClutchModule.TellySpeed.CAUTIOUS);
    }
    private static void anchor(Minecraft client, PlacementPlayer player, PlacementLevel level,
                               CapturedConnection connection, AutoClutchModule.Mode mode,
                               AutoClutchModule.TellySpeed speed) throws ReflectiveOperationException {
        AutoClutchModule.setEnabled(false);
        AutoClutchModule.setMode(mode);
        AutoClutchModule.setTellySpeed(speed);
        level.states.clear();
        level.dropBlockWrite = false;
        level.reportDroppedWriteSuccessful = false;
        level.droppedBlockWrites = 0;
        level.states.put(new BlockPos(-1, 2, 0), Blocks.STONE.defaultBlockState());
        player.inventory.setItem(4, new ItemStack(Items.WATER_BUCKET));
        player.inventory.setItem(6, new ItemStack(Items.COBBLESTONE, 64));
        player.offhand = ItemStack.EMPTY;
        player.inventory.setSelectedSlot(0);
        player.fallDistance = 0;
        player.grounded = true;
        player.velocity = Vec3.ZERO;
        locate(player, new Vec3(-0.5, 3, 0.5));
        connection.packets.clear();
        AutoClutchModule.setEnabled(true);
        AutoClutchModule.tick(client);
        AutoClutchModule.beforeMove(client, Vec3.ZERO);
        require(blockUses(connection) == 0, "Standing still on the initial grounded bridge records support without placing a block");
    }
    private static long blockUses(CapturedConnection connection) {
        return connection.packets.stream().filter(ServerboundUseItemOnPacket.class::isInstance).count();
    }
    private static void locate(PlacementPlayer player, Vec3 position) throws ReflectiveOperationException {
        set(Entity.class, player, "position", position);
        set(Entity.class, player, "bb", new AABB(position.x - 0.3, position.y, position.z - 0.3,
                position.x + 0.3, position.y + 1.8, position.z + 0.3));
    }
    private static void set(Class<?> owner, Object target, String name, Object value) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
