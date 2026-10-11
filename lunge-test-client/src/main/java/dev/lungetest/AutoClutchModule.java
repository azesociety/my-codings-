package dev.lungetest;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Predictive bridge catches and ordinary bucket interactions; never alters physical fall data. */
public final class AutoClutchModule {
    public enum Mode {
        WATER("Water bucket"), BLOCKS("Blocks"), BOTH("Blocks + water"),
        TELLY("Telly bridge + clutch"), TELLY_ONLY("Telly only"), AUTO_TELLY("Auto Telly");
        private final String label;
        Mode(String label) { this.label = label; }
        public String label() { return label; }
    }
    public enum TellySpeed {
        CAUTIOUS("Careful · 1/tick", 1), NORMAL("Balanced · 2/tick", 2), FAST("Fast · 4/tick", 4);
        private final String label;
        private final int placements;
        TellySpeed(String label, int placements) { this.label = label; this.placements = placements; }
        public String label() { return label; }
        public int placements() { return placements; }
    }
    private static boolean enabled;
    private static boolean acted;
    private static Mode mode = Mode.BOTH;
    private static TellySpeed tellySpeed = TellySpeed.FAST;
    private static ClientPacketListener armedConnection;
    private static ClientLevel armedLevel;
    private static long ticks;
    private static boolean wasAirborne;
    private static int waterAttempts;
    private static int blockAttempts;
    private static final Map<BlockPos, Attempt> waterTargets = new HashMap<>();
    private static final Map<BlockPos, Attempt> blockTargets = new HashMap<>();
    // Telly bridging spans many jumps, so its retry window is independent of a fall's six attempts.
    private static final Map<BlockPos, Attempt> tellyTargets = new HashMap<>();
    private static final Deque<Long> tellyFailures = new ArrayDeque<>();
    private static Integer bridgeFloorY;
    private static BlockPos bridgeCursor;
    private static long lastTellyInteractionTick = Long.MIN_VALUE;
    private static int interactionsThisTick;
    private static String status = "Disabled";

    private AutoClutchModule() {}
    public static boolean enabled() { return enabled; }
    public static boolean actedThisTick() { return acted; }
    public static Mode mode() { return mode; }
    public static TellySpeed tellySpeed() { return tellySpeed; }
    public static void setTellySpeed(TellySpeed value) {
        tellySpeed = value == null ? TellySpeed.FAST : value;
    }
    public static void cycleTellySpeed() {
        TellySpeed[] speeds = TellySpeed.values();
        setTellySpeed(speeds[(tellySpeed.ordinal() + 1) % speeds.length]);
    }
    public static String status() { return mode == Mode.AUTO_TELLY ? TellyPilotModule.status() : status; }
    public static void setMode(Mode value) {
        Mode next = value == null ? Mode.BOTH : value;
        if (mode != next) {
            TellyPilotModule.reset(Minecraft.getInstance());
            resetTelly();
        }
        mode = next;
    }
    public static void setEnabled(boolean value) {
        TellyPilotModule.reset(Minecraft.getInstance());
        enabled = value;
        acted = false;
        interactionsThisTick = 0;
        armedConnection = null;
        armedLevel = null;
        resetFall();
        resetTelly();
        status = value ? readyStatus() : "Disabled";
    }

    /** Session maintenance and falling checks before the ordinary gameMode tick. */
    public static void tick(Minecraft client) {
        acted = false;
        interactionsThisTick = 0;
        ticks++;
        TellyPilotModule.tick(client);
        if (!ready(client)) return;
        LocalPlayer player = client.player;
        if (isTelly()) {
            pruneTellyAttempts();
            updateBridgeAnchor(client, player);
            if (player.isInWater() || player.isInLava() || player.onClimbable()) resetTelly();
            else attemptTellyBridge(client, player, player.getDeltaMovement());
        }
        if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()) {
            if (wasAirborne) resetFall();
            wasAirborne = false;
            blockAttempts = 0;
            blockTargets.entrySet().removeIf(entry -> ticks - entry.getValue().lastTick > 40);
            if (!acted && !isTelly()) status = readyStatus();
            return;
        }
        wasAirborne = true;
        attemptFall(client, player, player.getDeltaMovement());
    }

    /** Before vanilla movement: attach a bridge block while feet still clear its top. */
    public static void beforeMove(Minecraft client, Vec3 movement) {
        if (!ready(client) || !finite(movement)) return;
        LocalPlayer player = client.player;
        if (isTelly()) {
            if (player.isInWater() || player.isInLava() || player.onClimbable()) {
                resetTelly();
                status = "Telly: stand on a full solid bridge first";
                return;
            }
            updateBridgeAnchor(client, player);
            attemptTellyBridge(client, player, movement);
            if (!player.onGround() && mode != Mode.TELLY_ONLY && mode != Mode.AUTO_TELLY) {
                wasAirborne = true;
                attemptFall(client, player, movement);
            }
            return;
        }
        if (!player.onGround()) {
            wasAirborne = true;
            attemptFall(client, player, movement);
            return;
        }
        if (mode == Mode.WATER || movement.x * movement.x + movement.z * movement.z < 1.0e-8) return;
        AABB nextFeet = player.getBoundingBox().move(movement.x, 0, movement.z);
        if (hasFootSupport(client.level, nextFeet)) return;
        Plan plan = findBlockPlan(client, player, movement, true);
        if (plan != null) perform(client, player, plan);
    }

    private static boolean ready(Minecraft client) {
        if (!enabled) return false;
        if (client.player == null || client.level == null || client.gameMode == null
                || client.getConnection() == null || !client.player.isAlive()) {
            setEnabled(false);
            return false;
        }
        if (armedConnection == null) {
            armedConnection = client.getConnection();
            armedLevel = client.level;
        } else if (client.getConnection() != armedConnection || client.level != armedLevel) {
            setEnabled(false);
            return false;
        }
        if (client.screen != null || client.isPaused() || !client.isWindowActive()) {
            status = "Paused while menu is open";
            return false;
        }
        LocalPlayer player = client.player;
        if (player.isSpectator() || player.isCreative() || player.getAbilities().flying
                || player.isFallFlying() || player.isPassenger() || player.isUsingItem()) {
            status = "Waiting for normal survival movement";
            return false;
        }
        return finite(player.position());
    }

    private static void attemptFall(Minecraft client, LocalPlayer player, Vec3 velocity) {
        if (mode == Mode.TELLY_ONLY || mode == Mode.AUTO_TELLY || acted) return;
        if (!finite(velocity) || velocity.y >= -0.05) return;
        if (mode == Mode.TELLY && lastTellyInteractionTick == ticks) return;
        // Independent budgets let a missed block catch use its bucket later in the same fall.
        if (mode != Mode.BLOCKS && (player.fallDistance >= 2.0 || velocity.y <= -0.75)) {
            Plan water = findWaterPlan(client, player, velocity);
            if (water != null) { perform(client, player, water); return; }
        }
        if (mode != Mode.WATER && player.fallDistance < 3.0
                && (mode != Mode.TELLY || bridgeFloorY == null
                    || player.getBoundingBox().minY < bridgeFloorY + 1.0 - 1.0e-6)) {
            Plan block = findBlockPlan(client, player, velocity, false);
            if (block != null) perform(client, player, block);
        } else if (mode == Mode.BLOCKS && player.fallDistance >= 3.0) {
            status = "Long fall: enable Blocks + water";
        }
    }

    /** Keep a real bridge's floor fixed during ascent, apex and descent instead of building a tower. */
    private static void updateBridgeAnchor(Minecraft client, LocalPlayer player) {
        if (!player.onGround()) return;
        AABB body = player.getBoundingBox();
        AABB feet = new AABB(body.minX + 0.001, body.minY - 0.05, body.minZ + 0.001,
                body.maxX - 0.001, body.minY + 0.001, body.maxZ - 0.001);
        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(feet.minX, feet.minY, feet.minZ),
                BlockPos.containing(feet.maxX, feet.maxY, feet.maxZ))) {
            if (!client.level.isLoaded(pos) || !client.level.getWorldBorder().isWithinBounds(pos)
                    || !client.level.getBlockState(pos).isCollisionShapeFullBlock(client.level, pos)
                    || Math.abs(body.minY - (pos.getY() + 1.0)) > 0.05) continue;
            double distance = Vec3.atCenterOf(pos).distanceToSqr(player.position());
            if (distance < bestDistance) { best = pos.immutable(); bestDistance = distance; }
        }
        if (best != null) {
            bridgeFloorY = best.getY();
            bridgeCursor = best;
        }
    }

    private static void attemptTellyBridge(Minecraft client, LocalPlayer player, Vec3 movement) {
        if (!finite(movement) || interactionsThisTick >= tellySpeed.placements()
                || acted && lastTellyInteractionTick != ticks) return;
        if (bridgeFloorY == null || bridgeCursor == null) {
            status = "Telly: stand on a full solid bridge first";
            return;
        }
        // Only the combined mode hands a drop below the bridge top to the ordinary clutch checks.
        if (player.getBoundingBox().minY < bridgeFloorY + 1.0 - 1.0e-6
                || player.fallDistance >= 3.0) {
            if (mode == Mode.TELLY_ONLY || mode == Mode.AUTO_TELLY) status = "Telly only: below bridge height";
            return;
        }
        if (tellyFailures.size() >= 12) {
            status = mode == Mode.TELLY_ONLY || mode == Mode.AUTO_TELLY ? "Telly: placement retries paused"
                    : "Telly: placement retries paused; water fallback ready";
            return;
        }
        double reach = player.blockInteractionRange();
        if (!Double.isFinite(reach) || reach <= 0) return;
        Set<BlockPos> path = new LinkedHashSet<>();
        Vec3 foot = player.position();
        appendBridgePath(path, Vec3.atCenterOf(bridgeCursor), foot, bridgeFloorY);
        Vec3 previous = foot;
        for (int prediction = 1; prediction <= 2; prediction++) {
            Vec3 projected = foot.add(movement.x * prediction, 0, movement.z * prediction);
            appendBridgePath(path, previous, projected, bridgeFloorY);
            previous = projected;
        }
        int missingCandidates = 0;
        for (BlockPos placement : path) {
            if (!client.level.isLoaded(placement) || !client.level.getWorldBorder().isWithinBounds(placement)
                    || !client.level.getBlockState(placement).canBeReplaced()) continue;
            if (++missingCandidates > 4 || interactionsThisTick >= tellySpeed.placements()) break;
            if (player.getBoundingBox().intersects(new AABB(placement))
                    || !canAttempt(tellyTargets, placement, 4)) {
                status = "Telly: waiting for the next supported bridge cell";
                return;
            }
            Supply supply = findSupply(player, false, placement, client.level);
            if (supply == null) { status = "Telly: need solid blocks in hotbar"; return; }
            Plan plan = null;
            for (Direction fromPlacement : Direction.values()) {
                if (fromPlacement == Direction.UP) continue;
                BlockPos support = placement.relative(fromPlacement);
                if (!solidSupport(client.level, support)) continue;
                Direction face = fromPlacement.getOpposite();
                BlockHitResult hit = visibleTellyFace(client, player, support, face, reach);
                if (hit == null || !player.mayUseItemAt(placement, face, supply.stack)) continue;
                plan = new Plan(supply, hit, hit.getLocation(), placement, false, true);
                break;
            }
            // Confirm each real placement before using it as the next cell's support.
            // A refused/pending write stops this ordered path instead of jumping an unsupported gap.
            if (plan == null) {
                status = "Telly: waiting for a visible, supported bridge face";
                return;
            }
            if (!perform(client, player, plan)) return;
        }
        if (!acted) status = missingCandidates == 0 ? readyStatus()
                : "Telly: waiting for a visible, supported bridge face";
    }

    /** Rasterize horizontal motion into adjacent cells, including a side cell at diagonal corners. */
    private static void appendBridgePath(Set<BlockPos> path, Vec3 start, Vec3 end, int floorY) {
        int x = (int) Math.floor(start.x), z = (int) Math.floor(start.z);
        int endX = (int) Math.floor(end.x), endZ = (int) Math.floor(end.z);
        path.add(new BlockPos(x, floorY, z));
        double dx = end.x - start.x, dz = end.z - start.z;
        int stepX = Double.compare(dx, 0), stepZ = Double.compare(dz, 0);
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);
        double nextX = stepX == 0 ? Double.POSITIVE_INFINITY
                : ((stepX > 0 ? x + 1.0 : x) - start.x) / dx;
        double nextZ = stepZ == 0 ? Double.POSITIVE_INFINITY
                : ((stepZ > 0 ? z + 1.0 : z) - start.z) / dz;
        for (int cell = 0; cell < 16 && (x != endX || z != endZ); cell++) {
            if (nextX <= nextZ) { x += stepX; nextX += deltaX; }
            else { z += stepZ; nextZ += deltaZ; }
            path.add(new BlockPos(x, floorY, z));
        }
    }

    private static boolean solidSupport(ClientLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        var state = level.getBlockState(pos);
        return state.isCollisionShapeFullBlock(level, pos) && !state.hasBlockEntity()
                && !state.is(Blocks.CRAFTING_TABLE) && !state.is(Blocks.CARTOGRAPHY_TABLE)
                && !state.is(Blocks.LOOM) && !state.is(Blocks.SMITHING_TABLE)
                && !state.is(Blocks.STONECUTTER) && !state.is(Blocks.NOTE_BLOCK)
                && !state.is(Blocks.RESPAWN_ANCHOR);
    }

    private static BlockHitResult visibleTellyFace(Minecraft client, LocalPlayer player,
            BlockPos support, Direction face, double reach) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        Vec3 center = Vec3.atCenterOf(support).add(normal.scale(0.5));
        Vec3 eye = player.getEyePosition();
        // A side-face point near its top stays reachable during a jump. Every candidate still uses a real ray;
        // standing above the parent block cannot reach its occluded outward face through that block's top.
        Vec3 closest = new Vec3(Math.clamp(eye.x, support.getX() + 0.05, support.getX() + 0.95),
                support.getY() + 0.98, Math.clamp(eye.z, support.getZ() + 0.05, support.getZ() + 0.95));
        if (face.getAxis() == Direction.Axis.X) closest = new Vec3(center.x, closest.y, closest.z);
        else if (face.getAxis() == Direction.Axis.Z) closest = new Vec3(closest.x, closest.y, center.z);
        else closest = new Vec3(closest.x, center.y, closest.z);
        for (Vec3 facePoint : new Vec3[] {closest, center}) {
            Vec3 inside = facePoint.subtract(normal.scale(0.001));
            if (eye.distanceToSqr(inside) > reach * reach) continue;
            BlockHitResult hit = client.level.clip(new ClipContext(eye, inside,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support)
                    && hit.getDirection() == face) return hit;
        }
        return null;
    }

    private static String readyStatus() {
        return mode == Mode.TELLY_ONLY || mode == Mode.AUTO_TELLY ? "Telly ready: solid blocks in hotbar"
                : mode == Mode.TELLY ? "Telly ready: solid blocks + bucket in hotbar"
                : "Ready: blocks + bucket in hotbar";
    }

    private static boolean isTelly() {
        return mode == Mode.TELLY || mode == Mode.TELLY_ONLY || mode == Mode.AUTO_TELLY;
    }

    private static void pruneTellyAttempts() {
        tellyTargets.entrySet().removeIf(entry -> ticks - entry.getValue().lastTick >= 40);
        while (!tellyFailures.isEmpty() && ticks - tellyFailures.peekFirst() >= 40) tellyFailures.removeFirst();
    }

    private static boolean hasFootSupport(ClientLevel level, AABB body) {
        AABB feet = new AABB(body.minX + 0.001, body.minY - 0.05, body.minZ + 0.001,
                body.maxX - 0.001, body.minY + 0.001, body.maxZ - 0.001);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(feet.minX, feet.minY, feet.minZ),
                BlockPos.containing(feet.maxX, feet.maxY, feet.maxZ))) {
            if (!level.isLoaded(pos)) continue;
            for (AABB shape : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                if (shape.move(pos).intersects(feet)) return true;
            }
        }
        return false;
    }

    private static Plan findWaterPlan(Minecraft client, LocalPlayer player, Vec3 velocity) {
        Supply supply = findSupply(player, true, null, client.level);
        if (supply == null) { status = "Need a water bucket in hotbar"; return null; }
        if (waterAttempts >= 6) { status = "Water attempts exhausted for this fall"; return null; }
        double reach = player.blockInteractionRange();
        if (!Double.isFinite(reach) || reach <= 0) return null;
        Vec3 projected = player.position();
        Vec3 previous = projected;
        Vec3 movement = velocity;
        for (int prediction = 0; prediction < 7; prediction++) {
            if (prediction > 0) {
                previous = projected;
                projected = projected.add(movement);
                movement = new Vec3(movement.x * 0.91, (movement.y - 0.08) * 0.98, movement.z * 0.91);
            }
            BlockHitResult ground = floorAt(client, player, projected.x, projected.z, reach);
            if (!usableFloor(client, player, ground)) continue;
            double cushionTop = ground.getLocation().y + 1.0;
            if (projected.y > cushionTop) continue;
            // Interpolate the crossing of the water surface; do not aim several fast-fall ticks ahead.
            if (prediction > 0 && previous.y > cushionTop && previous.y > projected.y) {
                double fraction = (previous.y - cushionTop) / (previous.y - projected.y);
                Vec3 crossing = previous.add(projected.subtract(previous).scale(fraction));
                ground = floorAt(client, player, crossing.x, crossing.z, reach);
                if (!usableFloor(client, player, ground)) continue;
            }
            BlockPos water = ground.getBlockPos().above();
            if (!client.level.isLoaded(water) || !client.level.getWorldBorder().isWithinBounds(water)
                    || !client.level.getBlockState(water).canBeReplaced(Fluids.WATER)
                    || !client.level.getFluidState(water).isEmpty() || !canAttempt(waterTargets, water, 3)) continue;
            if (client.level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, water)) {
                status = "Water evaporates here; use block catch";
                return null;
            }
            Vec3 aim = ground.getLocation().add(0, -0.001, 0);
            Vec3 eye = player.getEyePosition();
            if (eye.distanceToSqr(aim) > reach * reach) continue;
            BlockHitResult visible = client.level.clip(new ClipContext(eye, aim,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (visible.getType() != HitResult.Type.BLOCK || visible.getDirection() != Direction.UP
                    || !visible.getBlockPos().equals(ground.getBlockPos())) continue;
            if (!player.mayUseItemAt(water, Direction.UP, supply.stack)) continue;
            return new Plan(supply, visible, aim, water, true);
        }
        status = "Waiting for reachable landing surface";
        return null;
    }
    private static BlockHitResult floorAt(Minecraft client, LocalPlayer player, double x, double z, double reach) {
        Vec3 start = new Vec3(x, player.getY() + 0.05, z);
        return client.level.clip(new ClipContext(start, start.add(0, -(reach + 1.0), 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }
    private static boolean usableFloor(Minecraft client, LocalPlayer player, BlockHitResult ground) {
        if (ground.getType() != HitResult.Type.BLOCK || ground.getDirection() != Direction.UP
                || ground.getLocation().y >= player.getY()) return false;
        var state = client.level.getBlockState(ground.getBlockPos());
        return !(state.getBlock() instanceof LiquidBlockContainer)
                && state.isFaceSturdy(client.level, ground.getBlockPos(), Direction.UP);
    }

    private static Plan findBlockPlan(Minecraft client, LocalPlayer player, Vec3 velocity, boolean grounded) {
        if (!grounded && blockAttempts >= 6) return null;
        double reach = player.blockInteractionRange();
        if (!Double.isFinite(reach) || reach <= 0) return null;
        Vec3 foot = player.position();
        // Grounded beforeMove runs while feet are still at bridge height; falling below it is too late.
        double placementY = Math.floor(foot.y + 1.0e-6) - 1.0;
        for (int prediction = grounded ? 1 : 0; prediction < 3; prediction++) {
            Vec3 projected = foot.add(velocity.x * prediction, 0, velocity.z * prediction);
            BlockPos placement = BlockPos.containing(projected.x, placementY, projected.z);
            if (!client.level.isLoaded(placement) || !client.level.getWorldBorder().isWithinBounds(placement)
                    || !client.level.getBlockState(placement).canBeReplaced()
                    || player.getBoundingBox().intersects(new AABB(placement))
                    || !canAttempt(blockTargets, placement, 2)) continue;
            Supply supply = findSupply(player, false, placement, client.level);
            if (supply == null) { status = "Need a solid block in hotbar"; return null; }
            for (Direction fromPlacement : Direction.values()) {
                if (fromPlacement == Direction.UP) continue;
                BlockPos support = placement.relative(fromPlacement);
                if (!client.level.isLoaded(support)) continue;
                var supportState = client.level.getBlockState(support);
                if (!supportState.isCollisionShapeFullBlock(client.level, support)
                        || supportState.hasBlockEntity()
                        || supportState.is(Blocks.CRAFTING_TABLE) || supportState.is(Blocks.CARTOGRAPHY_TABLE)
                        || supportState.is(Blocks.LOOM) || supportState.is(Blocks.SMITHING_TABLE)
                        || supportState.is(Blocks.STONECUTTER) || supportState.is(Blocks.NOTE_BLOCK)
                        || supportState.is(Blocks.RESPAWN_ANCHOR)) continue;
                Direction face = fromPlacement.getOpposite();
                Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
                Vec3 faceCenter = Vec3.atCenterOf(support).add(normal.scale(0.5));
                Vec3 eye = player.getEyePosition();
                if (eye.distanceToSqr(faceCenter) > reach * reach) continue;
                Vec3 inside = faceCenter.subtract(normal.scale(0.001));
                BlockHitResult hit = client.level.clip(new ClipContext(eye, inside,
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(support)
                        || hit.getDirection() != face || !player.mayUseItemAt(placement, face, supply.stack)) continue;
                return new Plan(supply, hit, inside, placement, false);
            }
        }
        status = "Waiting for reachable block support";
        return null;
    }

    private static Supply findSupply(LocalPlayer player, boolean water, BlockPos placement, ClientLevel level) {
        int selected = player.getInventory().getSelectedSlot();
        ItemStack held = player.getInventory().getItem(selected);
        if (usable(player, held, water, placement, level)) return new Supply(InteractionHand.MAIN_HAND, selected, held);
        ItemStack offhand = player.getOffhandItem();
        if (usable(player, offhand, water, placement, level)) return new Supply(InteractionHand.OFF_HAND, -1, offhand);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (usable(player, stack, water, placement, level)) return new Supply(InteractionHand.MAIN_HAND, slot, stack);
        }
        return null;
    }
    private static boolean usable(LocalPlayer player, ItemStack stack, boolean water, BlockPos placement, ClientLevel level) {
        if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) return false;
        if (water) return stack.is(Items.WATER_BUCKET);
        return stack.getItem() instanceof BlockItem block && !(block.getBlock() instanceof FallingBlock)
                && block.getBlock().defaultBlockState().isCollisionShapeFullBlock(level, placement);
    }
    private static boolean canAttempt(Map<BlockPos, Attempt> targets, BlockPos target, int cap) {
        Attempt attempt = targets.get(target);
        return attempt == null || attempt.count < cap && ticks - attempt.lastTick >= 2;
    }
    private static boolean perform(Minecraft client, LocalPlayer player, Plan plan) {
        if (plan.telly) {
            if (interactionsThisTick >= tellySpeed.placements()
                    || acted && lastTellyInteractionTick != ticks) return false;
        } else if (acted) return false;
        if (mode == Mode.AUTO_TELLY && !TellyPilotModule.canPlace(client, plan.hit)) return false;
        int oldSlot = player.getInventory().getSelectedSlot();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        if (plan.supply.slot >= 0) player.getInventory().setSelectedSlot(plan.supply.slot);
        Vec3 direction = plan.aim.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float aimedYaw = horizontal > 1.0e-8
                ? (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F : oldYaw;
        float aimedPitch = (float) -Math.toDegrees(Math.atan2(direction.y, horizontal));
        // Block use carries its checked hit face directly; only vanilla bucket use needs a local ray aim.
        if (plan.water) {
            player.setYRot(aimedYaw);
            player.setXRot(aimedPitch);
        }
        Map<BlockPos, Attempt> targets = plan.water ? waterTargets : plan.telly ? tellyTargets : blockTargets;
        Attempt previous = targets.get(plan.target);
        targets.put(plan.target.immutable(), new Attempt(previous == null ? 1 : previous.count + 1, ticks));
        if (plan.water) waterAttempts++; else if (!plan.telly) blockAttempts++;
        try {
            acted = true;
            interactionsThisTick++;
            if (plan.telly) lastTellyInteractionTick = ticks;
            InteractionResult result = plan.water ? client.gameMode.useItem(player, plan.supply.hand)
                    : client.gameMode.useItemOn(player, plan.supply.hand, plan.hit);
            boolean placed = plan.water ? client.level.getFluidState(plan.target).is(Fluids.WATER)
                    : client.level.getBlockState(plan.target).isCollisionShapeFullBlock(client.level, plan.target);
            if (plan.telly) {
                if (placed) bridgeCursor = plan.target;
                else tellyFailures.addLast(ticks);
            }
            if (mode == Mode.AUTO_TELLY) TellyPilotModule.onPlacement(client, plan.hit, placed);
            status = placed ? (plan.water ? "Water placed; landing pending"
                    : plan.telly ? "Telly bridge block placed" : "Block placed under next step")
                    : result.consumesAction() ? "Interaction sent; waiting for placement" : "Placement refused; fallback ready";
            // A consumed local result alone never disables the bucket fallback or later bounded retries.
            return placed;
        } finally {
            if (plan.supply.slot >= 0 && player.getInventory().getSelectedSlot() == plan.supply.slot)
                player.getInventory().setSelectedSlot(oldSlot);
            if (plan.water && Float.compare(player.getYRot(), aimedYaw) == 0) player.setYRot(oldYaw);
            if (plan.water && Float.compare(player.getXRot(), aimedPitch) == 0) player.setXRot(oldPitch);
        }
    }
    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private static void resetFall() {
        wasAirborne = false;
        waterAttempts = blockAttempts = 0;
        waterTargets.clear();
        blockTargets.clear();
    }
    private static void resetTelly() {
        bridgeFloorY = null;
        bridgeCursor = null;
        tellyTargets.clear();
        tellyFailures.clear();
        lastTellyInteractionTick = Long.MIN_VALUE;
    }
    private record Attempt(int count, long lastTick) {}
    private record Supply(InteractionHand hand, int slot, ItemStack stack) {}
    private record Plan(Supply supply, BlockHitResult hit, Vec3 aim, BlockPos target, boolean water, boolean telly) {
        Plan(Supply supply, BlockHitResult hit, Vec3 aim, BlockPos target, boolean water) {
            this(supply, hit, aim, target, water, false);
        }
    }
}
