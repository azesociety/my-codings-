package dev.lungetest.verify;

import dev.lungetest.AutoClutchModule;
import dev.lungetest.LungeTestClient;
import dev.lungetest.TellyPilotModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.lungetest.verify.ClutchPlacementVerification.CapturedConnection;

/**
 * Controlled physics integration, actual KeyboardInput mixin, inherited vanilla jump impulse,
 * actual vanilla collision shapes and survival BlockItem predictions. No joined server or full
 * LocalPlayer.aiStep is claimed: storage/transport and horizontal/gravity integration are local.
 */
public final class AutoTellyVerification {
    private static int checks;
    private AutoTellyVerification() {}

    public static void verify(Minecraft client) {
        var previousPlayer = client.player;
        var previousLevel = client.level;
        var previousGameMode = client.gameMode;
        var previousScreen = client.screen;
        var previousHit = client.hitResult;
        boolean previousWindowActive = client.isWindowActive();
        boolean previousEnabled = AutoClutchModule.enabled();
        var previousMode = AutoClutchModule.mode();
        var previousSpeed = AutoClutchModule.tellySpeed();
        var keys = keys(client);
        boolean[] previousKeys = new boolean[keys.size()];
        for (int index = 0; index < keys.size(); index++) previousKeys[index] = keys.get(index).isDown();
        checks = 0;
        try {
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (Unsafe) unsafeField.get(null);
            var connection = (CapturedConnection) unsafe.allocateInstance(CapturedConnection.class);
            connection.packets = new ArrayList<>();
            var player = (PilotPlayer) unsafe.allocateInstance(PilotPlayer.class);
            set(LocalPlayer.class, player, "connection", connection);
            player.inventory = new Inventory(player, new EntityEquipment());
            player.abilities = new Abilities();
            player.cooldowns = new ItemCooldowns();
            player.offhand = ItemStack.EMPTY;
            player.velocity = Vec3.ZERO;
            player.alive = true;
            set(Entity.class, player, "eyeHeight", 1.62F);
            set(Entity.class, player, "mainSupportingBlockPos", Optional.empty());
            set(LivingEntity.class, player, "attributes", new AttributeMap(Player.createAttributes().build()));
            set(LivingEntity.class, player, "activeEffects", new HashMap<>());
            var level = (PilotLevel) unsafe.allocateInstance(PilotLevel.class);
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
            client.setWindowActive(true);
            player.input = new KeyboardInput(client.options);

            // Complete multiple cycles through real keyboard processing, ordinary jumping,
            // local collision integration and vanilla block use rather than setting pilot phases.
            anchor(client, player, level, connection, AutoClutchModule.TellySpeed.FAST);
            int jumps = 0, landings = 0, backwardTicks = 0, forwardTicks = 0;
            int uses = 0;
            double startX = player.getX();
            boolean previousJump = false;
            boolean previousGrounded = player.grounded;
            for (int tick = 0; tick < 160 && landings < 3; tick++) {
                float yawBefore = player.getYRot();
                float pitchBefore = player.getXRot();
                Vec3 physicalBefore = player.velocity;
                double fallBefore = player.fallDistance;
                long sentBefore = blockUses(connection);
                AutoClutchModule.tick(client);
                require(player.velocity.equals(physicalBefore) && player.fallDistance == fallBefore,
                        "Automatic input/camera planning leaves velocity and physical fall distance untouched");
                require(Math.abs(Mth.wrapDegrees(player.getYRot() - yawBefore)) <= 48.001F
                                && Math.abs(player.getXRot() - pitchBefore) <= 25.001F,
                        "Pilot START tick turns the actual camera within its yaw/pitch limits");
                player.input.tick();
                var input = player.input.keyPresses;
                if (input.jump()) {
                    require(player.grounded && !previousJump,
                            "Each automatic jump is a single grounded ordinary keyboard pulse");
                    jumps++;
                    player.jumpFromGround();
                    require(Math.abs(player.velocity.y - 0.42) < 0.0001,
                            "Inherited vanilla LocalPlayer jumpFromGround applies default survival jump strength");
                    player.grounded = false;
                }
                previousJump = input.jump();
                var vector = player.input.getMoveVector();
                if (vector.lengthSquared() > 0.01F) {
                    double yaw = Math.toRadians(player.getYRot());
                    Vec3 worldInput = new Vec3(vector.x * Math.cos(yaw) - vector.y * Math.sin(yaw),
                            0, vector.y * Math.cos(yaw) + vector.x * Math.sin(yaw));
                    require(worldInput.normalize().dot(TellyPilotModule.direction()) > 0.98,
                            "Camera-relative keyboard input keeps travel in the initial world direction");
                    if (input.backward()) backwardTicks++;
                    if (input.forward()) forwardTicks++;
                }
                integrate(client, player);
                long sent = blockUses(connection) - sentBefore;
                require(sent >= 0 && sent <= 4,
                        "Automatic movement and camera callbacks share the Fast four-use client-tick budget");
                uses += (int) sent;
                if (!previousGrounded && player.grounded) landings++;
                previousGrounded = player.grounded;
                require(player.getY() >= 3.0 - 1.0e-6,
                        "Controlled jump trajectory lands on real bridge collision rather than falling through a gap");
            }
            require(jumps >= 3 && landings >= 3, "Automatic Telly completes three jump/backward-place/landing cycles: jumps=" + jumps + ", landings=" + landings + ", uses=" + uses + ", position=" + player.position() + ", status=" + TellyPilotModule.status());
            require(player.getX() > startX + 3 && backwardTicks > 0 && forwardTicks > 0,
                    "Telly moves along the bridge using forward travel and backward travel while looking back");
            require(uses >= 3 && player.inventory.getItem(6).getCount() == 64 - uses,
                    "Continuous automatic cycles send ordinary placement packets and consume actual survival blocks");
            require(level.states.keySet().stream().allMatch(pos -> pos.getY() == 2),
                    "Automatic jumping builds a horizontal bridge instead of a jump tower");
            require(connection.packets.stream().noneMatch(net.minecraft.network.protocol.game.ServerboundUseItemPacket.class::isInstance),
                    "Auto Telly remains a block-only strategy without bucket actions");
            require(player.inventory.getSelectedSlot() == 0,
                    "Every ordinary bridge use restores the user's originally selected hotbar item");

            // Cancel while a backwards camera and movement override are actually owned.
            anchor(client, player, level, connection, AutoClutchModule.TellySpeed.FAST);
            advanceToBack(client, player, level);
            require(Math.abs(Mth.wrapDegrees(player.getYRot() + 90F)) > 45,
                    "Stop checks begin while the pilot genuinely owns a turned camera");
            AutoClutchModule.setEnabled(false);
            require(close(player.getYRot(), -90) && close(player.getXRot(), -12),
                    "Disabling restores the original camera when it remains owned by the pilot");
            player.input.tick();
            require(player.input.keyPresses.equals(net.minecraft.world.entity.player.Input.EMPTY)
                            && player.input.getMoveVector().equals(net.minecraft.world.phys.Vec2.ZERO),
                    "Disabled automation relinquishes the real keyboard input without stuck movement or jump");

            for (String guard : List.of("menu", "focus", "supply", "path", "movement", "jump", "crouch", "attack", "use", "mouse", "death", "disconnect", "session")) {
                anchor(client, player, level, connection, AutoClutchModule.TellySpeed.FAST);
                advanceToBack(client, player, level);
                long before = blockUses(connection);
                float manualYaw = 0;
                switch (guard) {
                    case "menu" -> client.screen = new net.minecraft.client.gui.screens.TitleScreen();
                    case "focus" -> client.setWindowActive(false);
                    case "supply" -> player.inventory.setItem(6, ItemStack.EMPTY);
                    case "path" -> player.horizontalCollision = true;
                    case "movement" -> client.options.keyUp.setDown(true);
                    case "jump" -> client.options.keyJump.setDown(true);
                    case "crouch" -> client.options.keyShift.setDown(true);
                    case "attack" -> client.options.keyAttack.setDown(true);
                    case "use" -> client.options.keyUse.setDown(true);
                    case "mouse" -> { manualYaw = player.getYRot() + 15; player.setYRot(manualYaw); }
                    case "death" -> player.alive = false;
                    case "disconnect" -> client.player = null;
                    case "session" -> client.level = null;
                }
                AutoClutchModule.tick(client);
                player.input.tick();
                require(!TellyPilotModule.active() && blockUses(connection) == before,
                        "Automatic input/placement stops on " + guard + " without a further item-use packet");
                if (guard.equals("mouse")) require(close(player.getYRot(), manualYaw),
                        "Mouse override preserves the user's new camera direction rather than restoring over it");
                else if (!guard.equals("disconnect")) require(close(player.getYRot(), -90) && close(player.getXRot(), -12),
                        "The " + guard + " guard restores the camera while pilot ownership is unchanged");
                client.player = player;
                client.level = level;
                client.screen = null;
                client.setWindowActive(true);
                player.alive = true;
                player.horizontalCollision = false;
                clearKeys(client);
                AutoClutchModule.tick(client);
                player.input.tick();
                require(!TellyPilotModule.active() && !player.input.keyPresses.jump(),
                        "A stopped pilot cannot silently resume or jump after the " + guard + " condition clears");
            }

            anchor(client, player, level, connection, AutoClutchModule.TellySpeed.FAST);
            advanceToBack(client, player, level);
            AutoClutchModule.setMode(AutoClutchModule.Mode.TELLY_ONLY);
            player.input.tick();
            require(!TellyPilotModule.active() && close(player.getYRot(), -90) && close(player.getXRot(), -12)
                            && !player.input.keyPresses.jump(),
                    "Changing to manual Telly-only releases the automated camera and jump controller immediately");

            LungeTestClient.LOG.info("AUTO_TELLY_VERIFIED: {} checks; actual KeyboardInput mixin and inherited vanilla jump impulse, three automatic cycles with locally integrated horizontal/gravity motion and actual vanilla collision shapes, visible camera turns and direction-correct backward input, real survival BlockItem consumption/ordinary captured packets, no physical fall edits, ownership-aware camera/input cancellation for disable/mode/menu/focus/supplies/blocked-path/manual movement/jump/crouch/attack/use/mouse/death/disconnect/session. Controlled fixture: no joined server or full LocalPlayer.aiStep", checks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not prepare automatic Telly vanilla input/placement fixture", e);
        } finally {
            AutoClutchModule.setEnabled(false);
            AutoClutchModule.setMode(previousMode);
            AutoClutchModule.setTellySpeed(previousSpeed);
            client.player = previousPlayer;
            client.level = previousLevel;
            client.gameMode = previousGameMode;
            client.screen = previousScreen;
            client.hitResult = previousHit;
            client.setWindowActive(previousWindowActive);
            for (int index = 0; index < keys.size(); index++) keys.get(index).setDown(previousKeys[index]);
            AutoClutchModule.setEnabled(previousEnabled);
        }
    }

    private static void anchor(Minecraft client, PilotPlayer player, PilotLevel level,
                               CapturedConnection connection, AutoClutchModule.TellySpeed speed)
            throws ReflectiveOperationException {
        AutoClutchModule.setEnabled(false);
        clearKeys(client);
        client.player = player;
        client.level = level;
        client.screen = null;
        client.setWindowActive(true);
        player.alive = true;
        player.horizontalCollision = false;
        player.grounded = true;
        player.velocity = Vec3.ZERO;
        player.fallDistance = 0;
        player.setYRot(-90);
        player.setXRot(-12);
        player.inventory.setItem(0, new ItemStack(Items.DIAMOND_SPEAR));
        player.inventory.setItem(6, new ItemStack(Items.COBBLESTONE, 64));
        player.inventory.setSelectedSlot(0);
        player.offhand = ItemStack.EMPTY;
        level.states.clear();
        level.states.put(new BlockPos(-1, 2, 0), Blocks.STONE.defaultBlockState());
        locate(player, new Vec3(-0.5, 3, 0.5));
        connection.packets.clear();
        AutoClutchModule.setMode(AutoClutchModule.Mode.AUTO_TELLY);
        AutoClutchModule.setTellySpeed(speed);
        AutoClutchModule.setEnabled(true);
    }

    private static void advanceToBack(Minecraft client, PilotPlayer player, PilotLevel level)
            throws ReflectiveOperationException {
        for (int tick = 0; tick < 8; tick++) {
            AutoClutchModule.tick(client);
            player.input.tick();
            if (player.input.keyPresses.jump() && player.grounded) {
                player.jumpFromGround();
                player.grounded = false;
            }
            integrate(client, player);
            if (Math.abs(Mth.wrapDegrees(player.getYRot() + 90F)) > 70) return;
        }
        require(TellyPilotModule.active(), "Freshly enabled pilot remains active through its actual look-back transition");
    }

    private static void integrate(Minecraft client, PilotPlayer player) throws ReflectiveOperationException {
        var input = player.input.getMoveVector();
        double yaw = Math.toRadians(player.getYRot());
        // Movement speed is locally controlled; the exact vanilla keyboard vector, camera yaw,
        // jump impulse and block collision are real. This deliberately is not full aiStep.
        double speed = 0.16;
        Vec3 desired = new Vec3((input.x * Math.cos(yaw) - input.y * Math.sin(yaw)) * speed,
                player.grounded ? -0.08 : player.velocity.y, (input.y * Math.cos(yaw) + input.x * Math.sin(yaw)) * speed);
        Vec3 physicalBefore = player.velocity;
        double fallBefore = player.fallDistance;
        for (int callback = 0; callback < 8; callback++) AutoClutchModule.beforeMove(client, desired);
        require(player.velocity.equals(physicalBefore) && player.fallDistance == fallBefore,
                "Repeated automatic pre-movement placement callbacks never edit velocity or fall distance");
        Vec3 movement = Entity.collideBoundingBox(player, desired, player.getBoundingBox(), client.level, List.of());
        locate(player, player.position().add(movement));
        player.grounded = desired.y < 0 && Math.abs(desired.y - movement.y) > 1.0e-6;
        if (player.grounded) {
            player.velocity = new Vec3(desired.x, 0, desired.z);
            player.fallDistance = 0;
        } else {
            if (movement.y < 0) player.fallDistance += (float) -movement.y;
            player.velocity = new Vec3(desired.x, (desired.y - 0.08) * 0.98, desired.z);
        }
    }

    private static List<KeyMapping> keys(Minecraft client) {
        var o = client.options;
        return List.of(o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint,
                o.keyAttack, o.keyUse);
    }
    private static void clearKeys(Minecraft client) { keys(client).forEach(key -> key.setDown(false)); }
    private static long blockUses(CapturedConnection connection) {
        return connection.packets.stream().filter(ServerboundUseItemOnPacket.class::isInstance).count();
    }
    private static boolean close(float actual, float expected) { return Math.abs(Mth.wrapDegrees(actual - expected)) < 0.001; }
    private static void locate(PilotPlayer player, Vec3 position) throws ReflectiveOperationException {
        set(Entity.class, player, "position", position);
        set(Entity.class, player, "blockPosition", BlockPos.containing(position));
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

    public static final class PilotPlayer extends LocalPlayer {
        Inventory inventory;
        Abilities abilities;
        ItemCooldowns cooldowns;
        ItemStack offhand;
        Vec3 velocity;
        ClientLevel fixtureLevel;
        boolean alive, grounded;
        private PilotPlayer() { super(null, null, null, null, null, null, false); }
        @Override public Inventory getInventory() { return inventory; }
        @Override public Abilities getAbilities() { return abilities; }
        @Override public ItemCooldowns getCooldowns() { return cooldowns; }
        @Override public ItemStack getOffhandItem() { return offhand; }
        @Override public ItemStack getMainHandItem() { return inventory.getSelectedItem(); }
        @Override public ItemStack getItemInHand(InteractionHand hand) { return hand == InteractionHand.OFF_HAND ? offhand : inventory.getSelectedItem(); }
        @Override public void setItemInHand(InteractionHand hand, ItemStack stack) {
            if (hand == InteractionHand.OFF_HAND) offhand = stack;
            else inventory.setItem(inventory.getSelectedSlot(), stack);
        }
        @Override public Vec3 getDeltaMovement() { return velocity; }
        @Override public void setDeltaMovement(Vec3 value) { velocity = value; }
        @Override public boolean isSprinting() { return false; }
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
    public static final class PilotLevel extends ClientLevel {
        Map<BlockPos, BlockState> states;
        WorldBorder border;
        EnvironmentAttributeSystem environment;
        PilotPlayer player;
        private PilotLevel() { super(null, null, null, null, 0, 0, null, false, 0, 0); }
        @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags) { states.put(pos.immutable(), state); return true; }
        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursion) { return setBlock(pos, state, flags); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public boolean isLoaded(BlockPos pos) { return true; }
        @Override public WorldBorder getWorldBorder() { return border; }
        @Override public EnvironmentAttributeSystem environmentAttributes() { return environment; }
        @Override public FeatureFlagSet enabledFeatures() { return FeatureFlags.DEFAULT_FLAGS; }
        @Override public BlockGetter getChunkForCollisions(int x, int z) { return this; }
        @Override public boolean isUnobstructed(Entity excluded, VoxelShape shape) {
            return excluded == player || !Shapes.joinIsNotEmpty(shape, Shapes.create(player.getBoundingBox()), BooleanOp.AND);
        }
        @Override public java.util.List<Entity> getEntities(Entity excluded, net.minecraft.world.phys.AABB bounds, java.util.function.Predicate<? super Entity> filter) {
            return player != excluded && player != null && bounds.intersects(player.getBoundingBox()) && filter.test(player)
                    ? java.util.List.of(player) : java.util.List.of();
        }
        @Override public boolean mayInteract(Entity entity, BlockPos pos) { return true; }
        @Override public void playSound(Entity source, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) { }
        @Override public void gameEvent(Holder<GameEvent> event, Vec3 position, GameEvent.Context context) { }
    }
}
