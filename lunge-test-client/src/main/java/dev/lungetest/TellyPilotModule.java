package dev.lungetest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Drives ordinary jump/movement input and the visible view; block use stays in AutoClutchModule. */
public final class TellyPilotModule {
    public enum Phase { WAITING, JUMP, TURN_BACK, PLACE, TURN_FORWARD, STOPPED }
    public record InputOverride(Input keyPresses, Vec2 moveVector) {}

    private static final float YAW_PER_TICK = 48.0F;
    private static final float PITCH_PER_TICK = 25.0F;
    private static final float OWNERSHIP_EPSILON = 0.8F;
    private static boolean active;
    private static boolean stopped;
    private static boolean viewOwned;
    private static boolean jumpIssued;
    private static boolean sawAirborne;
    private static boolean placedThisJump;
    private static int refusedPlacements;
    private static int airborneTicks;
    private static int launchTicks;
    private static int groundedTicks;
    private static float heading;
    private static float originalYaw;
    private static float originalPitch;
    private static float ownedYaw;
    private static float ownedPitch;
    private static float yawBudget;
    private static float pitchBudget;
    private static double bridgeTop;
    private static Vec3 travel = Vec3.ZERO;
    private static LocalPlayer ownedPlayer;
    private static ClientLevel ownedLevel;
    private static ClientPacketListener ownedConnection;
    private static ClientInput ownedInput;
    private static Phase phase = Phase.WAITING;
    private static String status = "Auto Telly: ready on solid ground";

    private TellyPilotModule() {}

    public static boolean active() { return active; }
    public static Phase phase() { return phase; }
    public static String status() { return status; }
    public static Vec3 direction() { return travel; }

    /** Explicit module/mode changes clear a stopped run and release only this controller's state. */
    public static void reset(Minecraft client) {
        release(client, true);
        stopped = false;
        phase = Phase.WAITING;
        status = "Auto Telly: ready on solid ground";
    }

    /** Called once at client START, before the ordinary bridge planner and player movement. */
    public static void tick(Minecraft client) {
        yawBudget = YAW_PER_TICK;
        pitchBudget = PITCH_PER_TICK;
        if (!selected()) {
            if (active || stopped) reset(client);
            return;
        }
        if (stopped) return;
        if (!available(client)) {
            release(client, true);
            phase = Phase.WAITING;
            status = "Auto Telly: paused; close menus and return to survival movement";
            return;
        }
        if (active && (client.player != ownedPlayer || client.level != ownedLevel
                || client.getConnection() != ownedConnection)) {
            stop(client, "world changed");
            return;
        }
        if (manualKeys(client)) { stop(client, "manual input; toggle to restart"); return; }
        if (active && !stillOwnsView(client.player)) {
            stop(client, "manual view change; toggle to restart");
            return;
        }
        LocalPlayer player = client.player;
        if (!hasBlocks(client, player)) { stop(client, "need solid blocks in hotbar or offhand"); return; }
        if (!active) {
            BlockPos floor = solidFloor(client, player);
            if (!player.onGround() || floor == null) {
                status = "Auto Telly: stand on a full solid block first";
                return;
            }
            start(client, floor);
        }
        if (player.getY() < bridgeTop - 0.15 || player.fallDistance >= 3.0
                || !finite(player.getDeltaMovement())) {
            stop(client, "below bridge height; toggle to restart");
            return;
        }
        if (player.horizontalCollision || !clearForward(client, player)) {
            stop(client, "path blocked; toggle to restart");
            return;
        }
        if (player.onGround()) {
            if (solidFloor(client, player) == null) {
                stop(client, "landing has no full solid support");
                return;
            }
            if (sawAirborne) {
                phase = Phase.TURN_FORWARD;
                groundedTicks++;
                // A real landing is required before issuing another jump. No airborne jump spam.
                rotate(player, heading, forwardPitch());
                status = "Auto Telly: face forward and prepare the next jump";
                if (groundedTicks >= 1 && aimed(player, heading, forwardPitch(), 1.5F)) {
                    beginJump();
                }
            } else {
                rotate(player, heading, forwardPitch());
                if (jumpIssued && ++launchTicks > 4) {
                    stop(client, "jump did not take off; toggle to restart");
                    return;
                }
                status = "Auto Telly: jump forward";
            }
        } else {
            sawAirborne = true;
            airborneTicks++;
            if (airborneTicks > 32) {
                stop(client, "landing not confirmed; toggle to restart");
                return;
            }
            // Recover the forward view while descending over confirmed support. If the
            // next footstep needs another block, canPlace takes priority and lines up that face.
            if (placedThisJump && player.getDeltaMovement().y < 0 && supportedLanding(client, player)) {
                phase = Phase.TURN_FORWARD;
                rotate(player, heading, forwardPitch());
                status = "Auto Telly: turn forward for landing";
                return;
            }
            phase = Phase.TURN_BACK;
            rotate(player, heading + 180.0F, 75.0F);
            if (aimed(player, heading + 180.0F, 75.0F, 12.0F)) phase = Phase.PLACE;
            status = phase == Phase.PLACE ? "Auto Telly: look back, place, then land"
                    : "Auto Telly: turn back and look down";
        }
    }

    /** KeyboardInput TAIL replaces only this tick's input; global KeyMapping states are untouched. */
    public static InputOverride applyInput(Minecraft client, ClientInput input, Vec2 originalMovement) {
        if (!selected() || !active || stopped) return null;
        if (!available(client) || client.player != ownedPlayer || client.player.input != input) {
            release(client, true);
            return null;
        }
        Input physical = input.keyPresses;
        if (physical.forward() || physical.backward() || physical.left() || physical.right()
                || physical.jump() || physical.shift() || physical.sprint() || manualKeys(client)) {
            stop(client, "manual input; toggle to restart");
            return null;
        }
        if (!stillOwnsView(client.player)) {
            stop(client, "manual view change; toggle to restart");
            return null;
        }
        ownedInput = input;
        boolean turningOnGround = phase == Phase.TURN_FORWARD && client.player.onGround();
        Vec2 movement = turningOnGround ? Vec2.ZERO : movementForYaw(client.player.getYRot());
        boolean jump = phase == Phase.JUMP && !jumpIssued && client.player.onGround();
        if (jump) { jumpIssued = true; launchTicks = 0; }
        boolean sprint = phase == Phase.JUMP && movement.y > 0.8F;
        return new InputOverride(new Input(movement.y > 0.05F, movement.y < -0.05F,
                movement.x > 0.05F, movement.x < -0.05F, jump, turningOnGround, sprint), movement);
    }

    /** Preserve the captured world direction while the camera turns through forward, side and back. */
    public static Vec2 movementForYaw(float viewYaw) {
        double relative = Math.toRadians(Mth.wrapDegrees(viewYaw - heading));
        return new Vec2((float) Math.sin(relative), (float) Math.cos(relative));
    }

    /** A block may be used only after the actual visible view ray reaches the planner's checked face. */
    public static boolean canPlace(Minecraft client, BlockHitResult plannedHit) {
        if (!selected()) return true;
        if (!active || stopped || !available(client) || client.player != ownedPlayer
                || plannedHit == null || plannedHit.getType() != HitResult.Type.BLOCK) return false;
        if (!stillOwnsView(client.player)) {
            stop(client, "manual view change; toggle to restart");
            return false;
        }
        LocalPlayer player = client.player;
        // Grounded placement must not replace the jump/forward-turn phase before input runs.
        if (player.onGround()) return false;
        Vec3 direction = plannedHit.getLocation().subtract(player.getEyePosition());
        double horizontal = Math.hypot(direction.x, direction.z);
        if (!finite(direction) || direction.lengthSqr() < 1.0e-8) return false;
        float yaw = horizontal > 1.0e-8
                ? (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F : player.getYRot();
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, horizontal));
        rotate(player, yaw, pitch);
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();
        if (!Double.isFinite(reach) || reach <= 0.0) return false;
        BlockHitResult visible = client.level.clip(new ClipContext(eye,
                eye.add(player.getLookAngle().scale(reach)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        boolean sameFace = visible.getType() == HitResult.Type.BLOCK
                && visible.getBlockPos().equals(plannedHit.getBlockPos())
                && visible.getDirection() == plannedHit.getDirection();
        phase = sameFace ? Phase.PLACE : Phase.TURN_BACK;
        status = sameFace ? "Auto Telly: placing on the visible block face"
                : "Auto Telly: lining up the next block face";
        return sameFace;
    }

    public static void onPlacement(Minecraft client, BlockHitResult hit, boolean placed) {
        if (!selected() || !active || stopped) return;
        if (placed) {
            placedThisJump = true;
            refusedPlacements = 0;
            status = "Auto Telly: block placed; keep bridging until landing";
        } else if (++refusedPlacements >= 3) {
            stop(client, "placements refused; toggle to restart");
        } else {
            status = "Auto Telly: placement pending; bounded retry";
        }
    }

    private static void start(Minecraft client, BlockPos floor) {
        ownedPlayer = client.player;
        ownedLevel = client.level;
        ownedConnection = client.getConnection();
        originalYaw = heading = ownedPlayer.getYRot();
        originalPitch = ownedPlayer.getXRot();
        ownedYaw = originalYaw;
        ownedPitch = originalPitch;
        travel = new Vec3(-Math.sin(Math.toRadians(heading)), 0.0, Math.cos(Math.toRadians(heading)));
        bridgeTop = floor.getY() + 1.0;
        active = viewOwned = true;
        beginJump();
    }

    private static void beginJump() {
        jumpIssued = sawAirborne = placedThisJump = false;
        airborneTicks = launchTicks = groundedTicks = refusedPlacements = 0;
        phase = Phase.JUMP;
    }

    private static boolean selected() {
        return AutoClutchModule.enabled() && AutoClutchModule.mode() == AutoClutchModule.Mode.AUTO_TELLY;
    }

    private static boolean available(Minecraft client) {
        if (client == null || client.player == null || client.level == null || client.gameMode == null
                || client.getConnection() == null || client.screen != null || client.isPaused()
                || !client.isWindowActive()) return false;
        LocalPlayer player = client.player;
        return player.isAlive() && !player.isSpectator() && !player.isCreative()
                && !player.getAbilities().flying && !player.isFallFlying() && !player.isPassenger()
                && !player.isUsingItem() && !player.isInWater() && !player.isInLava()
                && !player.onClimbable() && finite(player.position());
    }

    private static boolean manualKeys(Minecraft client) {
        return client.options.keyUp.isDown() || client.options.keyDown.isDown()
                || client.options.keyLeft.isDown() || client.options.keyRight.isDown()
                || client.options.keyJump.isDown() || client.options.keyShift.isDown()
                || client.options.keySprint.isDown() || client.options.keyAttack.isDown()
                || client.options.keyUse.isDown();
    }

    private static boolean hasBlocks(Minecraft client, LocalPlayer player) {
        BlockPos probe = player.blockPosition().below();
        if (usableBlock(client, player, player.getOffhandItem(), probe)) return true;
        for (int slot = 0; slot < 9; slot++) {
            if (usableBlock(client, player, player.getInventory().getItem(slot), probe)) return true;
        }
        return false;
    }

    private static boolean usableBlock(Minecraft client, LocalPlayer player, ItemStack stack, BlockPos probe) {
        return !stack.isEmpty() && !player.getCooldowns().isOnCooldown(stack)
                && stack.getItem() instanceof BlockItem item && !(item.getBlock() instanceof FallingBlock)
                && item.getBlock().defaultBlockState().isCollisionShapeFullBlock(client.level, probe);
    }

    private static BlockPos solidFloor(Minecraft client, LocalPlayer player) {
        AABB body = player.getBoundingBox();
        double feet = body.minY;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX + 0.001, feet - 0.05,
                body.minZ + 0.001), BlockPos.containing(body.maxX - 0.001, feet - 0.001, body.maxZ - 0.001))) {
            if (client.level.isLoaded(pos) && client.level.getWorldBorder().isWithinBounds(pos)
                    && Math.abs(feet - (pos.getY() + 1.0)) < 0.05
                    && client.level.getBlockState(pos).isCollisionShapeFullBlock(client.level, pos))
                return pos.immutable();
        }
        return null;
    }

    private static boolean supportedLanding(Minecraft client, LocalPlayer player) {
        Vec3 landing = player.position().add(player.getDeltaMovement().multiply(2, 0, 2));
        BlockPos floor = BlockPos.containing(landing.x, bridgeTop - 0.05, landing.z);
        return client.level.isLoaded(floor) && client.level.getWorldBorder().isWithinBounds(floor)
                && client.level.getBlockState(floor).isCollisionShapeFullBlock(client.level, floor);
    }

    private static boolean clearForward(Minecraft client, LocalPlayer player) {
        AABB projected = player.getBoundingBox().move(travel.x * 0.4, 0.05, travel.z * 0.4);
        BlockPos next = BlockPos.containing(player.position().add(travel.scale(0.6)));
        if (!client.level.isLoaded(next) || !client.level.getWorldBorder().isWithinBounds(next)
                || !client.level.noCollision(player, projected)) return false;
        return !player.onGround() || client.level.noCollision(player, player.getBoundingBox().move(0, 1.25, 0));
    }

    private static void rotate(LocalPlayer player, float desiredYaw, float desiredPitch) {
        float yawDelta = Mth.wrapDegrees(desiredYaw - player.getYRot());
        float pitchDelta = Math.clamp(desiredPitch, -90.0F, 90.0F) - player.getXRot();
        float yawStep = Math.clamp(yawDelta, -yawBudget, yawBudget);
        float pitchStep = Math.clamp(pitchDelta, -pitchBudget, pitchBudget);
        player.setYRot(player.getYRot() + yawStep);
        player.setXRot(Math.clamp(player.getXRot() + pitchStep, -90.0F, 90.0F));
        yawBudget = Math.max(0.0F, yawBudget - Math.abs(yawStep));
        pitchBudget = Math.max(0.0F, pitchBudget - Math.abs(pitchStep));
        ownedYaw = player.getYRot();
        ownedPitch = player.getXRot();
        viewOwned = true;
    }

    private static boolean aimed(LocalPlayer player, float yaw, float pitch, float epsilon) {
        return Math.abs(Mth.wrapDegrees(player.getYRot() - yaw)) <= epsilon
                && Math.abs(player.getXRot() - pitch) <= epsilon;
    }

    private static boolean stillOwnsView(LocalPlayer player) {
        return viewOwned && aimed(player, ownedYaw, ownedPitch, OWNERSHIP_EPSILON);
    }

    private static float forwardPitch() { return Math.clamp(originalPitch, -15.0F, 20.0F); }

    private static void stop(Minecraft client, String reason) {
        release(client, true);
        stopped = true;
        phase = Phase.STOPPED;
        status = "Auto Telly stopped: " + reason;
    }

    private static void release(Minecraft client, boolean restoreView) {
        LocalPlayer previousPlayer = ownedPlayer;
        ClientInput previousInput = ownedInput;
        active = false;
        if (restoreView && client != null && client.player == previousPlayer && previousPlayer != null
                && stillOwnsView(previousPlayer)) {
            previousPlayer.setYRot(originalYaw);
            previousPlayer.setXRot(originalPitch);
        }
        viewOwned = false;
        ownedPlayer = null;
        ownedLevel = null;
        ownedConnection = null;
        ownedInput = null;
        travel = Vec3.ZERO;
        jumpIssued = sawAirborne = placedThisJump = false;
        if (previousPlayer != null && previousInput != null && previousPlayer.input == previousInput)
            previousInput.tick(); // Regenerate physical input after active=false; TAIL now leaves it alone.
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
