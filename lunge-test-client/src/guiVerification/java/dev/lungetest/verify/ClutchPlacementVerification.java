package dev.lungetest.verify;

import dev.lungetest.AutoClutchModule;
import dev.lungetest.LungeTestClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exercises the actual vanilla game-mode prediction and item placement methods. Only world
 * storage, sounds/events and network transport are replaced: item results are never mocked.
 */
public final class ClutchPlacementVerification {
    private static int checks;
    private ClutchPlacementVerification() {}

    public static void verify(Minecraft client) {
        var previousPlayer = client.player;
        var previousLevel = client.level;
        var previousGameMode = client.gameMode;
        var previousScreen = client.screen;
        var previousHit = client.hitResult;
        boolean previousEnabled = AutoClutchModule.enabled();
        var previousMode = AutoClutchModule.mode();
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
            player.inventory.setItem(6, new ItemStack(Items.COBBLESTONE, 8));
            player.inventory.setSelectedSlot(0);

            // A straight fall over a normal floor must create water, not merely return SUCCESS.
            locate(player, new Vec3(0.5, 3.0, 0.5));
            player.velocity = new Vec3(0, -0.8, 0);
            player.fallDistance = 5;
            level.states.put(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            AutoClutchModule.setMode(AutoClutchModule.Mode.WATER);
            rearm();
            AutoClutchModule.tick(client);
            require(level.getFluidState(BlockPos.ZERO.above()).is(Fluids.WATER)
                            && level.getFluidState(BlockPos.ZERO.above()).isSource(),
                    "The real vanilla BucketItem places a water source above the floor");
            require(player.inventory.getItem(4).is(Items.BUCKET),
                    "Vanilla bucket use replaces the actual hotbar water bucket with an empty bucket");
            require(player.inventory.getSelectedSlot() == 0 && player.getYRot() == 37 && player.getXRot() == -12,
                    "Water placement restores the selected spear and the original camera");
            var waterPacket = first(connection, ServerboundUseItemPacket.class);
            require(waterPacket != null && waterPacket.getHand() == InteractionHand.MAIN_HAND
                            && waterPacket.getXRot() > 80 && waterPacket.getSequence() > 0,
                    "Vanilla water use captures the downward aim and prediction sequence in its ordinary packet");
            require(hasSlotBefore(connection, 4, ServerboundUseItemPacket.class),
                    "Vanilla selects the bucket on the captured transport before the use packet");
            require(player.fallDistance == 5 && player.velocity.equals(new Vec3(0, -0.8, 0)),
                    "Water use does not edit physical velocity or fall distance");

            // The first falling tick beside an ordinary bridge places an actual solid block.
            connection.packets.clear();
            level.states.clear();
            level.states.put(new BlockPos(-1, 2, 0), Blocks.STONE.defaultBlockState());
            locate(player, new Vec3(0.65, 3.2, 0.5));
            player.velocity = new Vec3(0.08, -0.15, 0);
            player.fallDistance = 0.4;
            player.grounded = false;
            AutoClutchModule.setMode(AutoClutchModule.Mode.BLOCKS);
            rearm();
            AutoClutchModule.tick(client);
            require(level.getBlockState(new BlockPos(0, 2, 0)).is(Blocks.COBBLESTONE),
                    "Real BlockItem placement fills the bridge gap below the player's feet");
            require(player.inventory.getItem(6).getCount() == 7,
                    "Real block placement consumes exactly one block from survival supplies");
            var blockPacket = first(connection, ServerboundUseItemOnPacket.class);
            require(blockPacket != null && blockPacket.getHitResult().getBlockPos().equals(new BlockPos(-1, 2, 0))
                            && blockPacket.getHitResult().getDirection() == Direction.EAST,
                    "Vanilla sends an ordinary placement packet for the real adjacent bridge face");
            require(hasSlotBefore(connection, 6, ServerboundUseItemOnPacket.class),
                    "The captured normal protocol selects cobblestone before placing it");
            require(player.inventory.getSelectedSlot() == 0 && player.getYRot() == 37 && player.getXRot() == -12,
                    "Block placement restores the original slot and camera");

            // A bridge slip must be caught while the feet are still exactly at the bridge top.
            // Waiting for a falling tick puts the block inside the player's body and is too late.
            connection.packets.clear();
            level.states.clear();
            level.states.put(new BlockPos(-1, 2, 0), Blocks.STONE.defaultBlockState());
            locate(player, new Vec3(0.2, 3.0, 0.5));
            player.velocity = new Vec3(0.35, -0.08, 0);
            player.fallDistance = 0;
            player.grounded = true;
            player.inventory.setItem(6, new ItemStack(Items.COBBLESTONE, 8));
            AutoClutchModule.setMode(AutoClutchModule.Mode.BOTH);
            rearm();
            var outgoing = new Vec3(0.35, -0.08, 0);
            var afterHorizontal = player.getBoundingBox().move(outgoing.x, 0, outgoing.z);
            require(Shapes.collide(Direction.Axis.Y, afterHorizontal,
                            level.getBlockCollisions(player, afterHorizontal.expandTowards(0, -0.08, 0)), -0.08) < 0,
                    "The outgoing bridge step genuinely has no original block support");
            AutoClutchModule.beforeMove(client, outgoing);
            require(level.getBlockState(new BlockPos(0, 2, 0)).is(Blocks.COBBLESTONE),
                    "The actual vanilla block item catches a grounded edge before the feet fall below its top");
            require(Shapes.collide(Direction.Axis.Y, afterHorizontal,
                            level.getBlockCollisions(player, afterHorizontal.expandTowards(0, -0.08, 0)), -0.08) == 0,
                    "Vanilla collision shapes stop downward movement on the newly placed bridge catch");
            require(player.getY() == 3.0 && player.fallDistance == 0
                            && player.velocity.equals(new Vec3(0.35, -0.08, 0)) && player.inventory.getItem(6).getCount() == 7,
                    "Grounded catching consumes a real block without changing the player's movement or fall data");

            // A moving bucket clutch must cushion the predicted landing path, not the old column.
            connection.packets.clear();
            level.states.clear();
            for (int x = -1; x <= 5; x++)
                level.states.put(new BlockPos(x, 0, 0), Blocks.STONE.defaultBlockState());
            locate(player, new Vec3(0.5, 3.0, 0.5));
            player.velocity = new Vec3(0.8, -0.8, 0);
            player.fallDistance = 5;
            player.grounded = false;
            player.inventory.setItem(4, new ItemStack(Items.WATER_BUCKET));
            AutoClutchModule.setMode(AutoClutchModule.Mode.BOTH);
            rearm();
            AutoClutchModule.tick(client);
            var movingWater = level.states.entrySet().stream()
                    .filter(entry -> entry.getValue().getFluidState().is(Fluids.WATER))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            require(movingWater != null && movingWater.getX() >= 1 && movingWater.getY() == 1,
                    "Actual bucket use puts moving-fall water ahead of the old player column");
            require(predictedBodyEntersWater(player, movingWater),
                    "The predicted falling body intersects the actual water cushion before the floor");
            require(player.inventory.getItem(4).is(Items.BUCKET)
                            && first(connection, ServerboundUseItemPacket.class) != null,
                    "The moving clutch uses the actual bucket and ordinary vanilla water-use packet");

            // A failed or locally reported-successful block prediction must keep the bucket fallback.
            // Both interactions execute real item code; only the fixture's world storage rejects it.
            verifyWaterAfterLostBlock(client, player, level, connection, false);
            verifyWaterAfterLostBlock(client, player, level, connection, true);

            // Offhand use executes the same actual bucket code without inventing a mainhand slot switch.
            connection.packets.clear();
            level.states.clear();
            level.states.put(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            locate(player, new Vec3(0.5, 3, 0.5));
            player.velocity = new Vec3(0, -0.8, 0);
            player.fallDistance = 5;
            player.offhand = new ItemStack(Items.WATER_BUCKET);
            player.inventory.setItem(4, ItemStack.EMPTY);
            AutoClutchModule.setMode(AutoClutchModule.Mode.WATER);
            rearm();
            AutoClutchModule.tick(client);
            require(level.getFluidState(BlockPos.ZERO.above()).is(Fluids.WATER)
                            && player.offhand.is(Items.BUCKET),
                    "Actual offhand bucket use creates a source and transforms the offhand item");
            var offhandPacket = first(connection, ServerboundUseItemPacket.class);
            require(offhandPacket != null && offhandPacket.getHand() == InteractionHand.OFF_HAND,
                    "The vanilla packet specifies the actual offhand");
            require(player.inventory.getSelectedSlot() == 0,
                    "Offhand clutch preserves the existing spear slot");
            LungeTestClient.LOG.info("CLUTCH_PLACEMENT_VERIFIED: {} checks; actual vanilla game-mode prediction, grounded bridge support, moving landing cushions, refused/lost block-to-water fallback, BlockItem placement, BucketItem water sources and survival supply consumption; captured packets, no network transport", checks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not prepare actual vanilla clutch placement fixture", e);
        } finally {
            AutoClutchModule.setEnabled(false);
            AutoClutchModule.setMode(previousMode);
            client.player = previousPlayer;
            client.level = previousLevel;
            client.gameMode = previousGameMode;
            client.screen = previousScreen;
            client.hitResult = previousHit;
            AutoClutchModule.setEnabled(previousEnabled);
        }
    }

    private static void verifyWaterAfterLostBlock(Minecraft client, PlacementPlayer player,
            PlacementLevel level, CapturedConnection connection, boolean pretendSuccess) throws ReflectiveOperationException {
        connection.packets.clear();
        level.states.clear();
        level.states.put(new BlockPos(-1, 2, 0), Blocks.STONE.defaultBlockState());
        level.states.put(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
        locate(player, new Vec3(0.2, 3.0, 0.5));
        player.velocity = new Vec3(0.35, -0.08, 0);
        player.fallDistance = 0;
        player.grounded = true;
        player.offhand = ItemStack.EMPTY;
        player.inventory.setItem(4, new ItemStack(Items.WATER_BUCKET));
        player.inventory.setItem(6, new ItemStack(Items.COBBLESTONE, 8));
        level.dropBlockWrite = true;
        level.reportDroppedWriteSuccessful = pretendSuccess;
        level.droppedBlockWrites = 0;
        AutoClutchModule.setMode(AutoClutchModule.Mode.BOTH);
        rearm();
        AutoClutchModule.beforeMove(client, new Vec3(0.35, -0.08, 0));
        require(level.droppedBlockWrites == 1 && level.getBlockState(new BlockPos(0, 2, 0)).isAir(),
                "The real block item attempts the bridge face but the fixture retains no catch block");
        require(first(connection, ServerboundUseItemOnPacket.class) != null,
                "A lost block still traverses the real vanilla prediction and packet path");
        require(player.inventory.getItem(6).getCount() == (pretendSuccess ? 7 : 8),
                pretendSuccess ? "Vanilla really returns consuming success despite a lost predicted world write"
                        : "A refused vanilla block write preserves the survival block supply");

        level.dropBlockWrite = false;
        player.grounded = false;
        locate(player, new Vec3(0.5, 2.4, 0.5));
        player.velocity = new Vec3(0, -0.8, 0);
        player.fallDistance = 5;
        // Do not rearm: the bucket must remain available in this same fall after the block attempt.
        AutoClutchModule.tick(client);
        require(level.getFluidState(BlockPos.ZERO.above()).is(Fluids.WATER)
                        && player.inventory.getItem(4).is(Items.BUCKET),
                pretendSuccess ? "A SUCCESS result without real block support still allows water in the same fall"
                        : "A refused block catch still allows actual water placement in the same fall");
        require(first(connection, ServerboundUseItemPacket.class) != null,
                "The fallback sends an actual vanilla bucket-use packet without waiting for another fall");
    }

    private static boolean predictedBodyEntersWater(PlacementPlayer player, BlockPos water) {
        Vec3 position = player.position();
        Vec3 movement = player.velocity;
        var waterShape = new AABB(water.getX(), water.getY(), water.getZ(),
                water.getX() + 1, water.getY() + 8.0 / 9.0, water.getZ() + 1);
        for (int tick = 0; tick < 6; tick++) {
            var body = new AABB(position.x - 0.3, position.y, position.z - 0.3,
                    position.x + 0.3, position.y + 1.8, position.z + 0.3);
            if (body.intersects(waterShape)) return position.y > water.getY();
            position = position.add(movement);
            movement = new Vec3(movement.x * 0.91, (movement.y - 0.08) * 0.98, movement.z * 0.91);
        }
        return false;
    }

    private static void rearm() { AutoClutchModule.setEnabled(false); AutoClutchModule.setEnabled(true); }
    private static <T extends Packet<?>> T first(CapturedConnection connection, Class<T> type) {
        for (var packet : connection.packets) if (type.isInstance(packet)) return type.cast(packet);
        return null;
    }
    private static boolean hasSlotBefore(CapturedConnection connection, int slot, Class<? extends Packet<?>> use) {
        boolean selected = false;
        for (var packet : connection.packets) {
            if (packet instanceof ServerboundSetCarriedItemPacket carried) selected = carried.getSlot() == slot;
            if (use.isInstance(packet)) return selected;
        }
        return false;
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

    public static final class PlacementPlayer extends LocalPlayer {
        Inventory inventory;
        Abilities abilities;
        ItemCooldowns cooldowns;
        ItemStack offhand;
        Vec3 velocity;
        ClientLevel fixtureLevel;
        boolean alive, grounded;
        private PlacementPlayer() { super(null, null, null, null, null, null, false); }
        @Override public Inventory getInventory() { return inventory; }
        @Override public Abilities getAbilities() { return abilities; }
        @Override public ItemCooldowns getCooldowns() { return cooldowns; }
        @Override public ItemStack getOffhandItem() { return offhand; }
        @Override public ItemStack getMainHandItem() { return inventory.getSelectedItem(); }
        @Override public ItemStack getItemInHand(InteractionHand hand) {
            return hand == InteractionHand.OFF_HAND ? offhand : inventory.getSelectedItem();
        }
        @Override public void setItemInHand(InteractionHand hand, ItemStack stack) {
            if (hand == InteractionHand.OFF_HAND) offhand = stack;
            else inventory.setItem(inventory.getSelectedSlot(), stack);
        }
        @Override public Vec3 getDeltaMovement() { return velocity; }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean onGround() { return grounded; }
        @Override public boolean isInWater() { return false; }
        @Override public boolean isInLava() { return false; }
        @Override public boolean onClimbable() { return false; }
        @Override public boolean isFallFlying() { return false; }
        @Override public boolean isPassenger() { return false; }
        @Override public boolean isShiftKeyDown() { return false; }
        @Override public boolean isSecondaryUseActive() { return false; }
        @Override public boolean isUsingItem() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean hasInfiniteMaterials() { return false; }
        @Override public double blockInteractionRange() { return 4.5; }
        @Override public boolean mayUseItemAt(BlockPos pos, Direction face, ItemStack stack) { return true; }
        @Override public void awardStat(Stat<?> stat) { }
        @Override public Level level() { return fixtureLevel; }
    }
    public static final class PlacementLevel extends ClientLevel {
        Map<BlockPos, BlockState> states;
        WorldBorder border;
        EnvironmentAttributeSystem environment;
        PlacementPlayer player;
        boolean dropBlockWrite, reportDroppedWriteSuccessful;
        int droppedBlockWrites;
        private PlacementLevel() { super(null, null, null, null, 0, 0, null, false, 0, 0); }
        @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) {
            if (dropBlockWrite && !state.getFluidState().is(Fluids.WATER)) {
                droppedBlockWrites++;
                return reportDroppedWriteSuccessful;
            }
            states.put(pos.immutable(), state);
            return true;
        }
        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursion) {
            return setBlock(pos, state, flags);
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public boolean isLoaded(BlockPos pos) { return true; }
        @Override public WorldBorder getWorldBorder() { return border; }
        @Override public EnvironmentAttributeSystem environmentAttributes() { return environment; }
        @Override public FeatureFlagSet enabledFeatures() { return FeatureFlags.DEFAULT_FLAGS; }
        @Override public BlockGetter getChunkForCollisions(int x, int z) { return this; }
        @Override public boolean isUnobstructed(Entity excluded, VoxelShape shape) {
            return excluded == player || !Shapes.joinIsNotEmpty(shape, Shapes.create(player.getBoundingBox()), BooleanOp.AND);
        }
        @Override public boolean mayInteract(Entity entity, BlockPos pos) { return true; }
        @Override public void playSound(Entity source, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) { }
        @Override public void gameEvent(Holder<GameEvent> event, Vec3 position, GameEvent.Context context) { }
    }
    public static final class CapturedConnection extends ClientPacketListener {
        List<Packet<?>> packets;
        private CapturedConnection() { super(null, null, null); }
        @Override public void send(Packet<?> packet) { packets.add(packet); }
        @Override public boolean isFeatureEnabled(FeatureFlagSet flags) { return flags.isSubsetOf(FeatureFlags.DEFAULT_FLAGS); }
    }
}
