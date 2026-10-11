package dev.lungetest;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;

/** Immediate manual-hit mace selection and optional normally charged mob combos. */
public final class CombatAssistModule {
    public enum Feature { MACE, STUNSLAM }
    public enum MaceAction {
        FALLING_SMASH("Falling smash"), SWAP_ONLY("Swap only"), CHARGED_FOLLOW_UP("Charged follow-up");
        private final String label;
        MaceAction(String label) { this.label = label; }
        public String label() { return label; }
    }
    public enum Activation {
        HOLD("Hold key"), AUTOMATIC("Automatic");
        private final String label;
        Activation(String label) { this.label = label; }
        public String label() { return label; }
    }
    public enum TargetMode {
        CROSSHAIR("Crosshair"), NEAREST_HOSTILE("Nearest hostile");
        private final String label;
        TargetMode(String label) { this.label = label; }
        public String label() { return label; }
    }
    public static final class Preferences {
        public Activation activation = Activation.AUTOMATIC;
        public TargetMode targetMode = TargetMode.CROSSHAIR;
        public int activationKey;
        public boolean fallingOnly;
        public MaceAction maceAction = MaceAction.FALLING_SMASH;
        public int maceRevision;
        public double smashReach = 0;
        public int reactionTicks = 0, actionIntervalTicks = 2;
        public Preferences() { activationKey = GLFW.GLFW_KEY_UNKNOWN; }
        public Preferences(Feature feature) {
            activationKey = feature == Feature.MACE ? GLFW.GLFW_KEY_R : GLFW.GLFW_KEY_UNKNOWN;
            maceRevision = 1;
        }
    }
    private enum Stage { READY, AXE_HIT, COMPLETE }
    private static final class State {
        final Feature feature;
        final InventoryHotbarLease inventory = new InventoryHotbarLease();
        Preferences prefs;
        boolean enabled, held, queued;
        ClientPacketListener connection;
        ClientLevel level;
        Stage stage = Stage.READY;
        int originalSlot = -1, ownedSlot = -1;
        long selectedAt, stageAt, queuedAt;
        LivingEntity comboTarget;
        String status = "Disabled";
        State(Feature feature) { this.feature = feature; prefs = new Preferences(feature); }
    }
    private static final EnumMap<Feature, State> STATES = new EnumMap<>(Feature.class);
    private static long ticks;
    private static Feature controller;
    private static boolean dispatching;
    static { for (Feature feature : Feature.values()) STATES.put(feature, new State(feature)); }
    private CombatAssistModule() {}

    public static void initialize(Map<String, Preferences> saved) {
        for (Feature feature : Feature.values()) {
            State state = STATES.get(feature);
            Preferences prefs = saved == null ? null : saved.get(feature.name());
            state.prefs = feature == Feature.STUNSLAM || prefs == null ? new Preferences(feature) : prefs;
            if (state.prefs.activation == null) state.prefs.activation = Activation.AUTOMATIC;
            if (state.prefs.targetMode == null) state.prefs.targetMode = TargetMode.CROSSHAIR;
            state.prefs.maceAction = MaceAction.FALLING_SMASH;
            if (!Double.isFinite(state.prefs.smashReach) || state.prefs.smashReach < 3) state.prefs.smashReach = 0;
            state.prefs.smashReach = Math.min(6, state.prefs.smashReach);
            if (feature == Feature.MACE && state.prefs.maceRevision < 1) {
                if (state.prefs.maceAction == MaceAction.SWAP_ONLY) state.prefs.maceAction = MaceAction.FALLING_SMASH;
                state.prefs.maceRevision = 1;
            }
            state.prefs.reactionTicks = Math.clamp(state.prefs.reactionTicks, 0, 200);
            state.prefs.actionIntervalTicks = Math.clamp(state.prefs.actionIntervalTicks, 2, 200);
        }
        for (Feature feature : Feature.values()) {
            State state = STATES.get(feature);
            int key = state.prefs.activationKey;
            if (!availableKey(feature, key)) state.prefs.activationKey = GLFW.GLFW_KEY_UNKNOWN;
            setEnabled(feature, false);
        }
    }
    public static Map<String, Preferences> snapshot() {
        var result = new LinkedHashMap<String, Preferences>();
        for (Feature feature : Feature.values()) result.put(feature.name(), STATES.get(feature).prefs);
        return result;
    }
    public static Preferences preferences(Feature feature) { return STATES.get(feature).prefs; }
    public static void setTiming(Feature feature, int reactionTicks, int actionIntervalTicks) {
        if (feature == Feature.STUNSLAM) return;
        State state = STATES.get(feature);
        cancel(Minecraft.getInstance(), state);
        state.prefs.reactionTicks = Math.clamp(reactionTicks, 0, 200);
        state.prefs.actionIntervalTicks = Math.clamp(actionIntervalTicks, 2, 200);
        save();
    }
    public static boolean busy() {
        for (State state : STATES.values()) if (state.queued || state.ownedSlot >= 0) return true;
        return false;
    }
    public static boolean enabled(Feature feature) { return STATES.get(feature).enabled; }
    public static String status(Feature feature) { return STATES.get(feature).status; }
    public static Activation activation(Feature feature) { return STATES.get(feature).prefs.activation; }
    public static TargetMode targetMode(Feature feature) { return STATES.get(feature).prefs.targetMode; }
    public static int activationKey(Feature feature) { return STATES.get(feature).prefs.activationKey; }
    public static String activationKeyLabel(Feature feature) {
        int code = activationKey(feature);
        return code == GLFW.GLFW_KEY_UNKNOWN ? "None"
                : InputConstants.getKey(new KeyEvent(code, 0, 0)).getDisplayName().getString();
    }
    public static MaceAction maceAction() { return STATES.get(Feature.MACE).prefs.maceAction; }
    public static String maceActionLabel() { return maceAction().label(); }
    public static void cycleMaceAction() {
        State state = STATES.get(Feature.MACE);
        cancel(Minecraft.getInstance(), state);
        state.prefs.maceAction = MaceAction.FALLING_SMASH;
        state.status = state.enabled ? idleStatus(Feature.MACE) : "Disabled";
        save();
    }
    public static double smashReach() { return STATES.get(Feature.MACE).prefs.smashReach; }
    public static void cycleSmashReach() {
        var prefs = STATES.get(Feature.MACE).prefs;
        prefs.smashReach = prefs.smashReach == 0 ? 3 : prefs.smashReach >= 6 ? 0 : prefs.smashReach + .5;
        save();
    }
    public static String smashReachLabel() { return smashReach() == 0 ? "Reach: Vanilla" : "Reach: " + smashReach() + " blocks"; }
    public static String maceModeLabel() {
        return STATES.get(Feature.MACE).prefs.fallingOnly ? "Falling smash" : "Normal + smash";
    }
    public static void cycleActivation(Feature feature) {
        if (feature == Feature.STUNSLAM) return;
        State state = STATES.get(feature);
        cancel(Minecraft.getInstance(), state);
        state.prefs.activation = activation(feature) == Activation.HOLD ? Activation.AUTOMATIC : Activation.HOLD;
        save();
    }
    public static void cycleTargetMode(Feature feature) {
        if (feature == Feature.STUNSLAM) return;
        State state = STATES.get(feature);
        cancel(Minecraft.getInstance(), state);
        state.prefs.targetMode = targetMode(feature) == TargetMode.CROSSHAIR ? TargetMode.NEAREST_HOSTILE : TargetMode.CROSSHAIR;
        save();
    }
    public static void cycleMaceMode() {
        State state = STATES.get(Feature.MACE);
        state.prefs.fallingOnly = !state.prefs.fallingOnly;
        save();
    }
    public static boolean isActivationKeyAssigned(int code) {
        if (code == GLFW.GLFW_KEY_UNKNOWN) return false;
        for (Feature feature : Feature.values()) if (activationKey(feature) == code) return true;
        return false;
    }
    private static boolean availableKey(Feature feature, int code) {
        if (code == GLFW.GLFW_KEY_UNKNOWN) return true;
        if (!AzeiumKeybinds.validKey(code) || code == SpearQuickLungeModule.key() || code == AzeiumKeybinds.menuKey() || code == AutoRejoinModule.key()) return false;
        for (var module : AzeiumModules.Module.values()) if (AzeiumKeybinds.key(module) == code) return false;
        for (Feature other : Feature.values()) if (other != feature && activationKey(other) == code) return false;
        return true;
    }
    public static boolean bindActivationKey(Feature feature, int code) {
        if (feature == Feature.STUNSLAM) return code == GLFW.GLFW_KEY_UNKNOWN;
        if (!availableKey(feature, code)) return false;
        cancel(Minecraft.getInstance(), STATES.get(feature));
        STATES.get(feature).prefs.activationKey = code;
        save();
        return true;
    }
    private static void save() { LungeTestClient.saveCombatPreferences(); }
    public static void setEnabled(Feature feature, boolean value) {
        State state = STATES.get(feature);
        cancel(Minecraft.getInstance(), state);
        if (feature == Feature.STUNSLAM) state.prefs = new Preferences(feature);
        state.enabled = value;
        state.held = false;
        state.connection = null;
        state.level = null;
        state.status = value ? idleStatus(feature) : "Disabled";
        if (value) CombatTestLog.begin(feature.name());
        CombatTestLog.event(feature.name(), "module_state", value ? "enabled" : "disabled");
        if (controller == feature) controller = null;
    }
    public static boolean handleActivationKey(Minecraft client, int action, KeyEvent event) {
        for (Feature feature : Feature.values()) {
            State state = STATES.get(feature);
            if (event.key() == GLFW.GLFW_KEY_UNKNOWN || event.key() != activationKey(feature)) continue;
            if (action == GLFW.GLFW_RELEASE) {
                state.held = false;
                return false; // Forward releases so a vanilla binding cannot remain pressed.
            }
            if (!state.enabled || activation(feature) != Activation.HOLD || client.screen != null
                    || client.player == null || client.level == null || !client.player.isAlive()) return false;
            if (action == GLFW.GLFW_PRESS) state.held = true;
            return action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT;
        }
        return false;
    }

    /** End-of-tick execution sees manual attacks and the player's real equipment update first. */
    public static void tick(Minecraft client) {
        ticks++;
        for (Feature feature : Feature.values()) { STATES.get(feature).inventory.tick(client); maintain(client, feature); }
        Feature requested = requested(Feature.STUNSLAM) ? Feature.STUNSLAM
                : requested(Feature.MACE) ? Feature.MACE : null;
        if (controller != requested) {
            if (controller != null) cancel(client, STATES.get(controller));
            controller = requested;
        }
        if (requested == null) return;
        State state = STATES.get(requested);
        if (!ready(client, state)) return;
        if (AutoClutchModule.actedThisTick()) {
            state.status = "Auto Clutch has priority";
            return;
        }
        if (ticks - state.queuedAt < state.prefs.reactionTicks) {
            state.status = "Waiting for configured reaction delay";
            return;
        }
        LocalPlayer player = client.player;
        if (state.ownedSlot >= 0 && player.getInventory().getSelectedSlot() != state.ownedSlot) {
            cancel(client, state);
            state.status = "Combo cancelled: hit again to restart";
            return;
        }
        if (requested == Feature.MACE) tickMace(client, state);
        else tickSlam(client, state);
    }
    private static boolean requested(Feature feature) {
        State state = STATES.get(feature);
        return state.enabled && state.queued && (activation(feature) == Activation.AUTOMATIC || state.held);
    }
    private static void maintain(Minecraft client, Feature feature) {
        State state = STATES.get(feature);
        if (!state.enabled) return;
        if (client.player == null || client.level == null || client.getConnection() == null || !client.player.isAlive()
                || state.connection != null && (state.connection != client.getConnection() || state.level != client.level)) {
            setEnabled(feature, false);
            return;
        }
        if (state.connection == null) { state.connection = client.getConnection(); state.level = client.level; }
        if (client.screen != null || client.isPaused() || !client.isWindowActive()) {
            cancel(client, state);
            state.held = false;
            state.status = "Paused: menu or window focus";
        } else if (activation(feature) == Activation.HOLD && !state.held) {
            cancel(client, state);
            state.status = idleStatus(feature);
        }
    }
    private static String idleStatus(Feature feature) {
        if (feature == Feature.STUNSLAM) return "Click a raised shield to start";
        if (feature == Feature.MACE && maceAction() == MaceAction.FALLING_SMASH)
            return activation(feature) == Activation.AUTOMATIC ? "Fall and click: hit with your mace"
                    : "Hold " + activationKeyLabel(feature) + " and click while falling";
        String target = feature == Feature.MACE && maceAction() == MaceAction.SWAP_ONLY
                ? "any entity" : feature == Feature.STUNSLAM ? "a player or hostile mob" : "a hostile mob";
        return activation(feature) == Activation.AUTOMATIC ? "Hit " + target + " to start"
                : "Hold " + activationKeyLabel(feature) + " and hit " + target;
    }
    private static boolean ready(Minecraft client, State state) {
        if (!state.enabled || client.player == null || client.level == null || client.gameMode == null
                || client.getConnection() == null || client.screen != null || client.isPaused() || !client.isWindowActive()) return false;
        LocalPlayer player = client.player;
        if (player.isSpectator() || player.isCreative() || player.getAbilities().flying || player.isFallFlying()
                || player.isPassenger() || player.isInWater() || player.isInLava() || player.onClimbable()
                || player.isUsingItem()) {
            releaseSlot(client, state);
            state.status = "Waiting for normal survival combat";
            return false;
        }
        return true;
    }
    private static void tickMace(Minecraft client, State state) {
        LivingEntity target = state.comboTarget;
        if (!eligible(client.player, target, state)) {
            cancel(client, state); state.status = "Target lost: hit again to restart"; return;
        }
        if (ticks - state.stageAt > 100 + state.prefs.reactionTicks + state.prefs.actionIntervalTicks) {
            cancel(client, state); state.status = "Follow-up timed out: hit again to restart"; return;
        }
        if (!select(client, state, maceSlot(client.player))) { state.status = "Put a mace in your hotbar"; return; }
        if (!inReach(client.player, target, state)) {
            state.status = "Waiting for target within sight and reach";
            return;
        }
        if (!charged(client.player, state)) { state.status = "Charging mace"; return; }
        boolean smash = falling(client.player);
        if (state.prefs.fallingOnly && !smash) { state.status = "Mace ready: jump or fall toward the mob"; return; }
        if (attack(client, state, target)) {
            cancel(client, state);
            state.status = smash ? "Mace smash sent" : "Mace attack sent";
        }
    }
    private static void tickSlam(Minecraft client, State state) {
        LocalPlayer player = client.player;
        if (state.stage == Stage.COMPLETE) {
            releaseSlot(client, state);
            state.status = "Combo finished: hit again to restart";
            return;
        }
        if (ticks - state.stageAt > (state.stage == Stage.READY ? 100 + state.prefs.reactionTicks : 80)
                + state.prefs.actionIntervalTicks) {
            finish(client, state, "Combo timed out: hit again to restart");
            return;
        }
        LivingEntity target = state.comboTarget == null ? findTarget(client, state) : state.comboTarget;
        if (!eligible(player, target, state)) {
            finish(client, state, "Target lost: hit again to restart");
            return;
        }
        if (state.stage == Stage.READY) {
            if (!target.isBlocking()) { finish(client, state, "Shield lowered: click a raised shield again"); return; }
            if (!inReach(player, target, state)) { state.status = "Move within axe reach"; return; }
            if (!select(client, state, axeSlot(player))) { finish(client, state, "Axe missing: combo stopped"); return; }
            if (!charged(player, state)) { state.status = "Charging axe"; return; }
            if (attack(client, state, target)) {
                state.comboTarget = target;
                state.stage = Stage.AXE_HIT;
                state.stageAt = ticks;
                state.status = "Axe hit sent: preparing mace";
            }
            return;
        }
        if (!select(client, state, maceSlot(player))) { finish(client, state, "Mace missing: combo stopped"); return; }
        if (!charged(player, state)) { state.status = "Charging mace"; return; }
        if (attack(client, state, target)) finish(client, state,
                falling(player) ? "Mace smash sent: combo finished" : "Mace follow-up sent: combo finished");
    }
    private static void finish(Minecraft client, State state, String status) {
        state.stage = Stage.COMPLETE;
        state.status = status;
        releaseSlot(client, state);
        state.queued = false;
    }
    private static boolean select(Minecraft client, State state, int slot) {
        if (slot < 0) { releaseSlot(client, state); return false; }
        LocalPlayer player = client.player;
        if (state.originalSlot < 0) state.originalSlot = player.getInventory().getSelectedSlot();
        if (state.ownedSlot != slot) {
            player.getInventory().setSelectedSlot(slot);
            state.ownedSlot = slot;
            state.selectedAt = ticks;
            if (client.gameMode != null) ((HotbarSyncAccess) client.gameMode).azeium$syncCarriedItem();
            CombatTestLog.event("CombatAssist", "hotbar_selected", "slot=" + slot);
        }
        return true;
    }
    private static boolean charged(LocalPlayer player, State state) {
        ItemStack stack = player.getMainHandItem();
        return ticks - state.selectedAt >= state.prefs.actionIntervalTicks && player.getAttackStrengthScale(0.5F) >= .98F
                && !player.cannotAttackWithItem(stack, 0) && !player.getCooldowns().isOnCooldown(stack);
    }
    private static boolean falling(LocalPlayer player) {
        return !player.onGround() && player.getDeltaMovement().y < -0.05 && MaceItem.canSmashAttack(player);
    }
    private static int maceSlot(LocalPlayer player) { return itemSlot(player, Items.MACE); }
    private static int itemSlot(LocalPlayer player, net.minecraft.world.item.Item item) {
        int selected = player.getInventory().getSelectedSlot();
        if (player.getInventory().getItem(selected).is(item)) return selected;
        for (int slot = 0; slot < 9; slot++) if (player.getInventory().getItem(slot).is(item)) return slot;
        return -1;
    }
    private static int axeSlot(LocalPlayer player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof AxeItem || stack.is(ItemTags.AXES)) return slot;
        }
        return -1;
    }
    private static boolean eligible(LocalPlayer player, Entity target, State state) {
        boolean supported = target instanceof Enemy || state.feature == Feature.STUNSLAM
                && target instanceof Player other && !other.isSpectator() && !other.isCreative();
        return supported && target instanceof LivingEntity living && target != player
                && living.isAlive() && !target.isRemoved()
                && (state.feature == Feature.STUNSLAM && target instanceof Player other
                    ? player.canHarmPlayer(other) : !player.isAlliedTo(target));
    }
    private static boolean inReach(LocalPlayer player, LivingEntity target, State state) {
        return eligible(player, target, state) && visibleInReach(player, target);
    }
    private static boolean visibleInReach(LocalPlayer player, Entity target) {
        if (!player.isWithinAttackRange(target.getBoundingBox(), 0.0)
                || !player.hasLineOfSight(target)) return false;
        Vec3 eyes = player.getEyePosition();
        var box = target.getBoundingBox();
        Vec3 aim = new Vec3(Math.clamp(eyes.x, box.minX, box.maxX),
                Math.clamp(eyes.y, box.minY, box.maxY), Math.clamp(eyes.z, box.minZ, box.maxZ));
        var obstruction = player.level().clip(new ClipContext(eyes, aim,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS
                || obstruction.getLocation().distanceToSqr(eyes) + 1.0e-5 >= aim.distanceToSqr(eyes);
    }
    private static LivingEntity findTarget(Minecraft client, State state) {
        LocalPlayer player = client.player;
        if (state.prefs.targetMode == TargetMode.CROSSHAIR) {
            Entity target = client.hitResult instanceof EntityHitResult hit ? hit.getEntity() : client.crosshairPickEntity;
            return eligible(player, target, state) && inReach(player, (LivingEntity) target, state) ? (LivingEntity) target : null;
        }
        LivingEntity closest = null;
        double distance = Double.MAX_VALUE;
        for (var entity : client.level.entitiesForRendering()) if (eligible(player, entity, state)) {
            var candidate = (LivingEntity) entity;
            double next = player.distanceToSqr(candidate);
            if (next < distance && inReach(player, candidate, state)) { closest = candidate; distance = next; }
        }
        return closest;
    }
    private static boolean attack(Minecraft client, State state, LivingEntity target) {
        if (!charged(client.player, state) || !inReach(client.player, target, state)) {
            state.status = "Waiting for charge, sight and reach";
            return false;
        }
        dispatching = true;
        try {
            float strength = client.player.getAttackStrengthScale(.5f);
            client.gameMode.attack(client.player, target);
            client.player.swing(InteractionHand.MAIN_HAND);
            CombatTestLog.event("CombatAssist", "attack_dispatched", "target=" + target.getUUID()
                    + ",slot=" + client.player.getInventory().getSelectedSlot()
                    + ",strength=" + strength);
        } finally { dispatching = false; }
        return true;
    }
    /** Prepare the axe before vanilla chooses its spear or ordinary attack path. */
    private static boolean prepareStunSlamAttack(Minecraft client, Entity target) {
        State state = STATES.get(Feature.STUNSLAM);
        if (dispatching || SpearQuickLungeModule.dispatching() || !state.enabled
                || client.player == null || !client.player.isAlive() || !ready(client, state)
                || !eligible(client.player, target, state)) return false;
        if (state.connection != null && (state.connection != client.getConnection() || state.level != client.level)) {
            setEnabled(Feature.STUNSLAM, false);
            return false;
        }
        if (state.ownedSlot >= 0 && client.player.getInventory().getSelectedSlot() != state.ownedSlot)
            cancel(client, state);
        if (state.queued) return inReach(client.player, (LivingEntity) target, state);
        if (!((LivingEntity) target).isBlocking()) {
            state.status = "Click a raised shield to start";
            return false;
        }
        if (!state.inventory.ensure(client, stack -> stack.getItem() instanceof AxeItem || stack.is(ItemTags.AXES),
                stack -> stack.is(Items.MACE))) {
            state.status = "Need axe + mace in inventory; close other containers";
            return false;
        }
        int axe = axeSlot(client.player);
        if (axe < 0 || maceSlot(client.player) < 0) {
            state.status = "Put an axe and mace in your hotbar";
            return false;
        }
        // Range belongs to the axe we will use, not the previously held spear or item.
        LocalPlayer player = client.player;
        int previous = player.getInventory().getSelectedSlot();
        boolean withinReach;
        player.getInventory().setSelectedSlot(axe);
        try { withinReach = inReach(player, (LivingEntity) target, state); }
        finally { player.getInventory().setSelectedSlot(previous); }
        if (!withinReach) { state.inventory.release(client); state.status = "Move within axe reach and sight"; return false; }
        state.connection = client.getConnection();
        state.level = client.level;
        select(client, state, axe);
        state.status = "Axe ready for your click";
        return true;
    }

    /** Prepares Stun Slam first, otherwise selects the mace for a falling click. */
    public static boolean prepareManualAttack(Minecraft client, Entity target) {
        if (AutoStunSlamModule.busy() || AutoStunSlamModule.dispatching()) return false;
        if (prepareStunSlamAttack(client, target)) return false;
        State state = STATES.get(Feature.MACE);
        if (dispatching || state.prefs.maceAction != MaceAction.FALLING_SMASH
                || !canSwap(client, state, target) || !ready(client, state) || !falling(client.player)) return false;
        if (!state.inventory.ensure(client, stack -> stack.is(Items.MACE))) {
            state.status = "Need a mace in inventory; close other containers"; return false;
        }
        int slot = maceSlot(client.player);
        if (slot < 0) { state.status = "Need a mace in inventory"; return false; }
        LocalPlayer player = client.player;
        int previous = player.getInventory().getSelectedSlot();
        // Test vanilla reach with the actual mace rather than the spear's minimum range.
        player.getInventory().setSelectedSlot(slot);
        if (!smashInReach(player, target) || player.getCooldowns().isOnCooldown(player.getMainHandItem())
                || player.cannotAttackWithItem(player.getMainHandItem(), 0)) {
            player.getInventory().setSelectedSlot(previous);
            state.inventory.release(client);
            state.status = "Waiting for mace reach and item cooldown";
            return false;
        }
        // A falling smash takes this new click; don't leave an older axe combo queued behind it.
        player.getInventory().setSelectedSlot(previous);
        cancel(client, STATES.get(Feature.STUNSLAM));
        if (state.ownedSlot < 0) state.originalSlot = previous;
        state.ownedSlot = slot;
        player.getInventory().setSelectedSlot(slot);
        state.connection = client.getConnection();
        state.level = client.level;
        state.status = "Mace ready for this hit";
        CombatTestLog.event("AutoMace", "falling_mace_prepared", "target=" + target.getUUID()
                + ",slot=" + slot + ",fallDistance=" + player.fallDistance);
        // MultiPlayerGameMode.attack synchronizes this slot before the ordinary attack packet.
        // No separate attack is scheduled and no movement/cooldown state is changed.
        return true;
    }
    /** Routes a real player click to an axe/mace combo before vanilla sends the initial attack. */
    public static boolean beginManualAttack(Minecraft client, Entity target) {
        if (dispatching || AutoStunSlamModule.dispatching()) return false;
        if (prepareStunSlamAttack(client, target)) {
            State state = STATES.get(Feature.STUNSLAM);
            if (!state.queued) queue(client, state, (LivingEntity) target);
            return true;
        }
        prepareManualAttack(client, target);
        return false;
    }
    /** Selects the mace after the real attack dispatch, preserving the player's original hit. */
    public static void onManualAttack(Minecraft client, Entity target) {
        if (dispatching || AutoStunSlamModule.dispatching()) return;
        State state = STATES.get(Feature.MACE);
        if (state.prefs.maceAction == MaceAction.FALLING_SMASH) {
            if (canSwap(client, state, target) && client.player.getMainHandItem().is(Items.MACE)) {
                state.status = "Mace hit sent";
                CombatTestLog.event("AutoMace", "manual_mace_attack", "target=" + target.getUUID());
            }
            finishManualAttack(client);
            return;
        }
        if (state.prefs.maceAction == MaceAction.SWAP_ONLY) {
            if (!canSwap(client, state, target)) return;
            int slot = maceSlot(client.player);
            if (slot < 0) { state.status = "Put a mace in your hotbar"; return; }
            state.connection = client.getConnection();
            state.level = client.level;
            // The ordinary game-mode tick syncs this selection before the next manual attack.
            // No ownership/restore queue: scrolling, toggling off and the next click stay manual.
            if (client.player.getInventory().getSelectedSlot() != slot)
                client.player.getInventory().setSelectedSlot(slot);
            state.status = "Mace selected: you control the next hit";
            CombatTestLog.event("AutoMace", "manual_hit_swap", "target=" + target.getUUID() + ",slot=" + slot);
            return;
        }
        if (!canStart(client, Feature.MACE, target) || maceSlot(client.player) < 0) return;
        queue(client, state, (LivingEntity) target);
        if (state.prefs.reactionTicks == 0) {
            select(client, state, maceSlot(client.player));
            state.status = "Mace selected: waiting for normal charge";
        } else state.status = "Waiting for configured reaction delay";
    }
    /** Restore only our temporary selection; never overwrite a user's subsequent scroll. */
    public static void finishManualAttack(Minecraft client) {
        State slam = STATES.get(Feature.STUNSLAM);
        // A blocked vanilla click never reaches the game-mode hook that queues the combo.
        if (!slam.queued) releaseSlot(client, slam);
        State state = STATES.get(Feature.MACE);
        boolean owned = state.ownedSlot >= 0 && client.player != null
                && state.connection == client.getConnection() && state.level == client.level
                && client.player.getInventory().getSelectedSlot() == state.ownedSlot;
        releaseSlot(client, state);
        if (owned && client.gameMode != null) ((HotbarSyncAccess) client.gameMode).azeium$syncCarriedItem();
    }
    private static boolean smashInReach(LocalPlayer player, Entity target) {
        double range = smashReach();
        if (range == 0) return visibleInReach(player, target);
        Vec3 eyes = player.getEyePosition();
        var box = target.getBoundingBox();
        Vec3 aim = new Vec3(Math.clamp(eyes.x, box.minX, box.maxX),
                Math.clamp(eyes.y, box.minY, box.maxY), Math.clamp(eyes.z, box.minZ, box.maxZ));
        if (eyes.distanceToSqr(aim) > range * range || !player.hasLineOfSight(target)) return false;
        var obstruction = player.level().clip(new ClipContext(eyes, aim,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS
                || obstruction.getLocation().distanceToSqr(eyes) + 1.0e-5 >= aim.distanceToSqr(eyes);
    }
    private static boolean canSwap(Minecraft client, State state, Entity target) {
        if (!state.enabled || state.prefs.activation == Activation.HOLD && !state.held
                || client.player == null || client.level == null || client.gameMode == null
                || client.getConnection() == null || client.screen != null || client.isPaused()
                || !client.isWindowActive() || !client.player.isAlive() || client.player.isSpectator()
                || target == null || target == client.player) return false;
        if (state.connection != null
                && (state.connection != client.getConnection() || state.level != client.level)) {
            setEnabled(Feature.MACE, false);
            return false;
        }
        // The tail hook already represents a dispatched manual hit; a killing blow must also swap.
        return true;
    }
    private static boolean canStart(Minecraft client, Feature feature, Entity target) {
        State state = STATES.get(feature);
        if (state.ownedSlot >= 0 && client.player != null
                && client.player.getInventory().getSelectedSlot() != state.ownedSlot) {
            cancel(client, state);
            state.status = "Combo cancelled: ready for your next hit";
        }
        return state.enabled && (state.prefs.activation == Activation.AUTOMATIC || state.held)
                && client.player != null && client.level != null && client.gameMode != null
                && client.getConnection() != null && client.player.isAlive() && ready(client, state)
                && eligible(client.player, target, state) && inReach(client.player, (LivingEntity) target, state);
    }
    private static void queue(Minecraft client, State state, LivingEntity target) {
        state.connection = client.getConnection();
        state.level = client.level;
        state.queued = true;
        state.queuedAt = ticks;
        CombatTestLog.event("CombatAssist", "sequence_queued", "target=" + target.getUUID()
                + ",reactionTicks=" + state.prefs.reactionTicks + ",intervalTicks=" + state.prefs.actionIntervalTicks);
        resetCombo(state);
        state.comboTarget = state == STATES.get(Feature.MACE) || state.prefs.targetMode == TargetMode.CROSSHAIR
                ? target : findTarget(client, state);
        if (state.comboTarget == null) state.comboTarget = target;
        state.status = "Combat follow-up queued";
    }
    private static void releaseSlot(Minecraft client, State state) {
        if (state.ownedSlot >= 0 && client.player != null && state.connection == client.getConnection()
                && state.level == client.level && client.player.getInventory().getSelectedSlot() == state.ownedSlot) {
            client.player.getInventory().setSelectedSlot(state.originalSlot);
            if (client.gameMode != null) ((HotbarSyncAccess) client.gameMode).azeium$syncCarriedItem();
        }
        state.originalSlot = state.ownedSlot = -1;
        state.inventory.release(client);
    }
    private static void resetCombo(State state) {
        state.stage = Stage.READY;
        state.stageAt = ticks;
        state.comboTarget = null;
    }
    private static void cancel(Minecraft client, State state) {
        releaseSlot(client, state);
        state.queued = false;
        resetCombo(state);
    }
}
