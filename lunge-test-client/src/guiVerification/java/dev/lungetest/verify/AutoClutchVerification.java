package dev.lungetest.verify;

import dev.lungetest.AutoClutchModule;
import dev.lungetest.LungeTestClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;
import java.util.HashMap;
import java.util.Map;

/** Runs the production planner against real block-state collision raycasts without network transport. */
public final class AutoClutchVerification {
    private static int checks;
    private AutoClutchVerification() {}

    public static void verify(Minecraft client) {
        var previousPlayer = client.player;
        var previousLevel = client.level;
        var previousGameMode = client.gameMode;
        var previousScreen = client.screen;
        boolean previousEnabled = AutoClutchModule.enabled();
        var previousMode = AutoClutchModule.mode();
        checks = 0;
        try {
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (Unsafe) unsafeField.get(null);
            var player = (FixturePlayer) unsafe.allocateInstance(FixturePlayer.class);
            var connection = (ClientPacketListener) unsafe.allocateInstance(ClientPacketListener.class);
            var connectionField = LocalPlayer.class.getField("connection");
            connectionField.setAccessible(true);
            connectionField.set(player, connection);
            player.inventory = new Inventory(player, new EntityEquipment());
            player.abilities = new Abilities();
            player.cooldowns = new ItemCooldowns();
            player.offhand = ItemStack.EMPTY;
            player.alive = true;
            player.velocity = new Vec3(0, -0.8, 0);
            player.fallDistance = 5;
            player.setYRot(37);
            player.setXRot(-12);
            set(Entity.class, player, "eyeHeight", 1.62F);
            locate(player, new Vec3(0.5, 3, 0.5));
            var level = (FixtureLevel) unsafe.allocateInstance(FixtureLevel.class);
            level.states = new HashMap<>();
            level.border = new WorldBorder();
            level.environment = environment(false);
            level.states.put(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            player.fixtureLevel = level;
            var gameMode = (FixtureGameMode) unsafe.allocateInstance(FixtureGameMode.class);
            gameMode.result = InteractionResult.SUCCESS;
            gameMode.placeResults = true;
            client.player = player;
            client.level = level;
            client.gameMode = gameMode;
            client.screen = null;
            player.inventory.setItem(0, new ItemStack(Items.DIAMOND_SPEAR));
            player.inventory.setItem(4, new ItemStack(Items.WATER_BUCKET));
            player.inventory.setSelectedSlot(0);

            AutoClutchModule.setEnabled(false);
            AutoClutchModule.setMode(AutoClutchModule.Mode.WATER);
            for (int i = 0; i < 8; i++) AutoClutchModule.tick(client);
            require(gameMode.uses == 0, "OFF performs no item use during a dangerous fall");
            AutoClutchModule.setEnabled(true);
            AutoClutchModule.tick(client);
            require(gameMode.uses == 1 && gameMode.blockUses == 0, "Water mode invokes exactly one ordinary bucket interaction");
            require(gameMode.usedHand == InteractionHand.MAIN_HAND && gameMode.usedSlot == 4,
                    "A bucket is selected from the actual hotbar");
            require(gameMode.usedStack.is(Items.WATER_BUCKET), "Bucket use receives the intended held stack");
            require(gameMode.usedPitch > 80, "Bucket use receives a downward aim toward the real top face");
            require(player.getInventory().getSelectedSlot() == 0, "The original selected spear slot is restored");
            require(player.getYRot() == 37 && player.getXRot() == -12, "The original yaw and pitch are restored immediately");
            require(player.fallDistance == 5 && player.velocity.equals(new Vec3(0, -0.8, 0)),
                    "Fall distance and physical movement remain unchanged");
            for (int i = 0; i < 20; i++) AutoClutchModule.tick(client);
            require(gameMode.uses == 1, "Observed water placement prevents repeated bucket use during the same fall");

            player.grounded = true;
            AutoClutchModule.tick(client);
            level.states.remove(new BlockPos(0, 1, 0));
            player.grounded = false;
            player.inventory.setItem(4, ItemStack.EMPTY);
            player.offhand = new ItemStack(Items.WATER_BUCKET);
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2 && gameMode.usedHand == InteractionHand.OFF_HAND,
                    "A new fall can use a bucket from offhand");
            require(player.getInventory().getSelectedSlot() == 0, "Offhand bucket use preserves the selected mainhand slot");

            rearm();
            level.states.remove(new BlockPos(0, 1, 0));
            player.offhand = ItemStack.EMPTY;
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2 && AutoClutchModule.status().contains("bucket"), "Missing bucket never synthesizes supplies");
            player.inventory.setItem(4, new ItemStack(Items.WATER_BUCKET));
            player.cooldowns.addCooldown(player.inventory.getItem(4), 10);
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2, "An item on a real vanilla cooldown is not used");
            player.cooldowns = new ItemCooldowns();

            locate(player, new Vec3(0.5, 12, 0.5));
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2, "Distant ground outside the survival interaction range is not used");
            locate(player, new Vec3(0.5, 3, 0.5));
            level.environment = environment(true);
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2 && AutoClutchModule.status().contains("evaporates"),
                    "Vanilla water-evaporation environment attributes disable water clutching");
            level.environment = environment(false);
            level.states.put(new BlockPos(0, 1, 0), Blocks.STONE.defaultBlockState());
            level.states.put(new BlockPos(0, 2, 0), Blocks.STONE.defaultBlockState());
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2, "An occupied landing column is not treated as a valid water destination");
            level.states.remove(new BlockPos(0, 1, 0));
            level.states.remove(new BlockPos(0, 2, 0));

            client.screen = previousScreen;
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2, "A menu prevents automatic item interactions");
            client.screen = null;
            player.flying = true;
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2, "Elytra flight is excluded");
            player.flying = false;
            player.abilities.flying = true;
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2, "Creative-style flying is excluded");
            player.abilities.flying = false;
            player.using = true;
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2, "An existing manually held item use is not interrupted");
            player.using = false;
            player.spectator = true;
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2, "Spectators are excluded");
            player.spectator = false;
            player.velocity = new Vec3(0, 0.1, 0);
            AutoClutchModule.tick(client);
            require(gameMode.uses == 2, "An ascending player is not clutching");
            player.velocity = new Vec3(0, -0.8, 0);

            gameMode.result = InteractionResult.FAIL;
            rearm();
            AutoClutchModule.tick(client);
            require(gameMode.uses == 3, "A rejected local use is observed as an attempt");
            AutoClutchModule.tick(client);
            require(gameMode.uses == 3, "Rejected attempts have a two-tick per-target retry guard");
            AutoClutchModule.tick(client);
            require(gameMode.uses == 4, "A rejected use allows a bounded delayed retry");
            AutoClutchModule.tick(client);
            require(gameMode.uses == 4, "The second attempt observes the same retry guard");
            AutoClutchModule.tick(client);
            require(gameMode.uses == 5, "A final retry is allowed for a reachable water target");
            for (int i = 0; i < 16; i++) AutoClutchModule.tick(client);
            require(gameMode.uses == 5, "The per-target attempt cap prevents repeated bucket spam");

            gameMode.result = InteractionResult.SUCCESS;
            gameMode.placeResults = false;
            rearm();
            AutoClutchModule.tick(client);
            int sentWithoutPlacement = gameMode.uses;
            for (int i = 0; i < 2; i++) AutoClutchModule.tick(client);
            require(gameMode.uses == sentWithoutPlacement + 1,
                    "SUCCESS without an observed water block does not falsely suppress retries");
            gameMode.placeResults = true;
            rearm();
            gameMode.onInteraction = () -> {
                player.inventory.setSelectedSlot(2);
                player.setYRot(73);
                player.setXRot(25);
            };
            AutoClutchModule.tick(client);
            require(player.inventory.getSelectedSlot() == 2 && player.getYRot() == 73 && player.getXRot() == 25,
                    "Changes made during the interaction callback are preserved");
            gameMode.onInteraction = null;
            player.inventory.setSelectedSlot(0);
            player.setYRot(37);
            player.setXRot(-12);

            // A bridge slip can attach a block to a real adjacent supporting face before a fatal fall.
            rearm();
            AutoClutchModule.setMode(AutoClutchModule.Mode.BLOCKS);
            player.inventory.setItem(6, new ItemStack(Items.COBBLESTONE));
            locate(player, new Vec3(0.65, 3.2, 0.5));
            player.fallDistance = 0.4;
            player.velocity = new Vec3(0.08, -0.15, 0);
            level.states.clear();
            level.states.put(new BlockPos(-1, 2, 0), Blocks.STONE.defaultBlockState());
            AutoClutchModule.tick(client);
            require(gameMode.blockUses == 1 && gameMode.usedSlot == 6,
                    "A bridge catch calls normal block interaction with an actual solid hotbar block");
            require(gameMode.usedHit.getBlockPos().equals(new BlockPos(-1, 2, 0))
                            && gameMode.usedHit.getDirection() == net.minecraft.core.Direction.EAST,
                    "The block interaction targets the reachable neighboring support face");
            require(player.inventory.getSelectedSlot() == 0 && player.getYRot() == 37 && player.getXRot() == -12,
                    "Block catching restores held slot and camera");
            for (int i = 0; i < 12; i++) AutoClutchModule.tick(client);
            require(gameMode.blockUses == 1, "A successful block catch is not repeatedly placed");
            rearm();
            player.fallDistance = 8;
            AutoClutchModule.tick(client);
            require(gameMode.blockUses == 1, "Ordinary blocks are not advertised as a cure for accumulated fatal fall damage");
            player.fallDistance = 0.4;
            level.states.clear();
            AutoClutchModule.tick(client);
            require(gameMode.blockUses == 1, "No neighboring support means no impossible air placement");
            level.states.put(new BlockPos(-1, 2, 0), Blocks.STONE.defaultBlockState());
            level.states.put(new BlockPos(0, 2, 0), Blocks.STONE.defaultBlockState());
            AutoClutchModule.tick(client);
            require(gameMode.blockUses == 1, "An occupied block destination is not used");
            level.states.remove(new BlockPos(0, 2, 0));
            rearm();
            AutoClutchModule.setMode(AutoClutchModule.Mode.BOTH);
            player.fallDistance = 2.5;
            level.states.put(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            level.environment = environment(true);
            AutoClutchModule.tick(client);
            require(gameMode.blockUses == 2, "Combined mode can fall back to an early block catch when water is unsuitable");
            // The movement hook catches a grounded edge before feet have dropped into its block layer.
            rearm();
            level.states.clear();
            level.states.put(new BlockPos(-1, 2, 0), Blocks.STONE.defaultBlockState());
            player.grounded = true;
            player.fallDistance = 0;
            locate(player, new Vec3(0.20, 3.0, 0.5));
            AutoClutchModule.beforeMove(client, new Vec3(0.35, -0.08, 0));
            require(gameMode.blockUses == 3 && level.getBlockState(new BlockPos(0, 2, 0)).is(Blocks.COBBLESTONE),
                    "The grounded movement hook places at bridge height before the first falling tick");
            require(player.getY() == 3.0 && player.fallDistance == 0,
                    "Anticipatory placement preserves the physical feet position and fall distance");
            AutoClutchModule.beforeMove(client, new Vec3(0.35, -0.08, 0));
            require(gameMode.blockUses == 3, "A supported next step does not place more blocks");
            player.grounded = false;

            // A failed early block interaction must not prevent the bucket on a later long fall.
            rearm();
            gameMode.placeResults = false;
            player.grounded = true;
            AutoClutchModule.beforeMove(client, new Vec3(0.35, -0.08, 0));
            level.states.remove(new BlockPos(0, 2, 0));
            AutoClutchModule.beforeMove(client, new Vec3(0.35, -0.08, 0));
            int earlyBlockUses = gameMode.blockUses;
            require(earlyBlockUses == 4, "A block interaction really was attempted before the water fallback");
            player.grounded = false;
            player.fallDistance = 5;
            player.velocity = new Vec3(0, -0.8, 0);
            locate(player, new Vec3(0.5, 3.0, 0.5));
            level.states.clear();
            level.states.put(BlockPos.ZERO, Blocks.STONE.defaultBlockState());
            level.environment = environment(false);
            gameMode.placeResults = true;
            int priorWaterUses = gameMode.uses;
            AutoClutchModule.tick(client);
            require(gameMode.uses == priorWaterUses + 1 && gameMode.blockUses == earlyBlockUses,
                    "Combined mode retains a bucket fallback after a block interaction without placement");

            player.alive = false;
            AutoClutchModule.tick(client);
            require(!AutoClutchModule.enabled(), "Death automatically disarms Auto clutch");
            player.alive = true;
            rearm();
            AutoClutchModule.tick(client);
            connectionField.set(player,
                    unsafe.allocateInstance(ClientPacketListener.class));
            AutoClutchModule.tick(client);
            require(!AutoClutchModule.enabled(), "Changing connections automatically disarms Auto clutch");
            connectionField.set(player, connection);
            rearm();
            AutoClutchModule.tick(client);
            client.level = null;
            AutoClutchModule.tick(client);
            require(!AutoClutchModule.enabled(), "Disconnecting automatically disarms Auto clutch");
            LungeTestClient.LOG.info("AUTO_CLUTCH_VERIFIED: {} planner checks; real collision raycasts, observed fixture placement, grounded edge prediction, block-to-water fallback, reach, retry limits and session guards", checks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not prepare Auto clutch interaction fixture", e);
        } finally {
            AutoClutchModule.setEnabled(previousEnabled);
            AutoClutchModule.setMode(previousMode);
            client.player = previousPlayer;
            client.level = previousLevel;
            client.gameMode = previousGameMode;
            client.screen = previousScreen;
        }
    }

    private static void rearm() { AutoClutchModule.setEnabled(false); AutoClutchModule.setEnabled(true); }
    private static EnvironmentAttributeSystem environment(boolean evaporates) {
        return EnvironmentAttributeSystem.builder()
                .addConstantLayer(EnvironmentAttributes.WATER_EVAPORATES, ignored -> evaporates).build();
    }
    private static void locate(FixturePlayer player, Vec3 position) throws ReflectiveOperationException {
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

    public static final class FixturePlayer extends LocalPlayer {
        Inventory inventory;
        Abilities abilities;
        ItemCooldowns cooldowns;
        ItemStack offhand;
        Vec3 velocity;
        ClientLevel fixtureLevel;
        boolean alive, grounded, flying, using, spectator;
        private FixturePlayer() { super(null, null, null, null, null, null, false); }
        @Override public Inventory getInventory() { return inventory; }
        @Override public Abilities getAbilities() { return abilities; }
        @Override public ItemCooldowns getCooldowns() { return cooldowns; }
        @Override public ItemStack getOffhandItem() { return offhand; }
        @Override public ItemStack getMainHandItem() { return inventory.getSelectedItem(); }
        @Override public ItemStack getItemInHand(InteractionHand hand) {
            return hand == InteractionHand.OFF_HAND ? offhand : inventory.getSelectedItem();
        }
        @Override public Vec3 getDeltaMovement() { return velocity; }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean onGround() { return grounded; }
        @Override public boolean isInWater() { return false; }
        @Override public boolean isInLava() { return false; }
        @Override public boolean onClimbable() { return false; }
        @Override public boolean isFallFlying() { return flying; }
        @Override public boolean isPassenger() { return false; }
        @Override public boolean isShiftKeyDown() { return false; }
        @Override public boolean isUsingItem() { return using; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public boolean isCreative() { return false; }
        @Override public double blockInteractionRange() { return 4.5; }
        @Override public boolean mayUseItemAt(BlockPos pos, net.minecraft.core.Direction face, ItemStack stack) { return true; }
        @Override public Level level() { return fixtureLevel; }
    }
    public static final class FixtureLevel extends ClientLevel {
        Map<BlockPos, BlockState> states;
        WorldBorder border;
        EnvironmentAttributeSystem environment;
        private FixtureLevel() { super(null, null, null, null, 0, 0, null, false, 0, 0); }
        @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public boolean isLoaded(BlockPos pos) { return true; }
        @Override public WorldBorder getWorldBorder() { return border; }
        @Override public EnvironmentAttributeSystem environmentAttributes() { return environment; }
    }
    public static final class FixtureGameMode extends MultiPlayerGameMode {
        int uses, blockUses, usedSlot;
        float usedPitch;
        ItemStack usedStack;
        InteractionHand usedHand;
        BlockHitResult usedHit;
        InteractionResult result;
        Runnable onInteraction;
        boolean placeResults;
        private FixtureGameMode() { super(null, null); }
        private void record(LocalPlayer player, InteractionHand hand) {
            usedSlot = player.getInventory().getSelectedSlot();
            usedPitch = player.getXRot();
            usedStack = player.getItemInHand(hand);
            usedHand = hand;
            if (onInteraction != null) onInteraction.run();
        }
        @Override public InteractionResult useItem(net.minecraft.world.entity.player.Player player, InteractionHand hand) {
            uses++;
            var local = (FixturePlayer) player;
            var eye = local.getEyePosition();
            var hit = local.fixtureLevel.clip(new net.minecraft.world.level.ClipContext(eye,
                    eye.add(local.getViewVector(1.0F).scale(local.blockInteractionRange())),
                    net.minecraft.world.level.ClipContext.Block.OUTLINE,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, local));
            record(local, hand);
            if (placeResults && result.consumesAction()
                    && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)
                ((FixtureLevel) local.fixtureLevel).states.put(hit.getBlockPos().relative(hit.getDirection()),
                        Blocks.WATER.defaultBlockState());
            return result;
        }
        @Override public InteractionResult useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
            blockUses++;
            usedHit = hit;
            record(player, hand);
            if (placeResults && result.consumesAction() && usedStack.getItem() instanceof net.minecraft.world.item.BlockItem block)
                ((FixtureLevel) player.level()).states.put(hit.getBlockPos().relative(hit.getDirection()),
                        block.getBlock().defaultBlockState());
            return result;
        }
    }
}
