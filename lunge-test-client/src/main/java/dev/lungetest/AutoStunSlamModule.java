package dev.lungetest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.EntityHitResult;

/** Adapted from the user-supplied PrivateStunslamClient: crosshair axe, then mace. */
public final class AutoStunSlamModule {
    public enum Trigger { CLICK, LOOK }
    public static final class Preferences { public int followUpTicks = 0; public int retriggerTicks = 0; public Trigger trigger = Trigger.CLICK; }
    private static Preferences prefs = new Preferences();
    public static void initialize(Preferences saved) {
        prefs = saved == null ? new Preferences() : saved;
        if (prefs.trigger == null) prefs.trigger = Trigger.CLICK;
        prefs.followUpTicks = Math.clamp(prefs.followUpTicks, 0, 10);
        prefs.retriggerTicks = Math.clamp(prefs.retriggerTicks, 0, 10);
    }
    public static Preferences preferences() { return prefs; }
    public static String triggerLabel() { return "Trigger: " + (prefs.trigger == Trigger.CLICK ? "Click shield" : "Look at shield"); }
    public static void cycleTrigger() { prefs.trigger = prefs.trigger == Trigger.CLICK ? Trigger.LOOK : Trigger.CLICK; setEnabled(enabled); LungeTestClient.saveStunPreferences(); }
    public static String delayLabel() { return "Mace delay: " + prefs.followUpTicks + " ticks"; }
    public static String retryLabel() { return "Repeat delay: " + prefs.retriggerTicks + " ticks"; }
    public static void cycleDelay() { prefs.followUpTicks = (prefs.followUpTicks + 1) % 11; LungeTestClient.saveStunPreferences(); }
    public static void cycleRetry() { prefs.retriggerTicks = (prefs.retriggerTicks + 1) % 11; LungeTestClient.saveStunPreferences(); }
    private static final InventoryHotbarLease inventory = new InventoryHotbarLease();
    private static boolean enabled, dispatching;
    private static LivingEntity pendingTarget, clickedTarget;
    private static long sequences;
    private static int followUpIn, retriggerDelay;
    private static int originalSlot = -1, ownedSlot = -1;
    private static ClientLevel level;
    private static ClientPacketListener connection;
    private static String status = "Disabled";
    private AutoStunSlamModule() {}
    public static boolean enabled() { return enabled; }
    public static boolean dispatching() { return dispatching; }
    public static boolean busy() { return pendingTarget != null; }
    public static String status() { return status; }
    public static void setEnabled(boolean value) {
        if (VisibleSwapReturn.pending("stun")) VisibleSwapReturn.finishNow();
        cancel(Minecraft.getInstance());
        enabled = value;
        retriggerDelay = 0;
        level = null; connection = null;
        status = value ? "Click a raised shield" : "Disabled";
    }
    public static void tick(Minecraft client) { update(client, true); }
    private static void update(Minecraft client, boolean advance) {
        inventory.tick(client);
        if (!enabled || VisibleSwapReturn.pending("stun")) return;
        if (client.player == null || client.level == null || client.gameMode == null || client.getConnection() == null
                || !client.player.isAlive() || level != null && (level != client.level || connection != client.getConnection())) {
            setEnabled(false); return;
        }
        if (level == null) { level = client.level; connection = client.getConnection(); }
        if (client.screen != null || client.isPaused() || !client.isWindowActive() || client.player.isUsingItem()
                || client.player.isSpectator() || client.player.isCreative() || client.player.getAbilities().flying
                || client.player.isPassenger() || client.player.isInWater()
                || client.player.isInLava() || client.player.onClimbable() || AutoClutchModule.actedThisTick()
                || TellyPilotModule.active()) {
            cancel(client); status = "Paused: screen, movement or another action"; return;
        }
        if (ownedSlot >= 0 && client.player.getInventory().getSelectedSlot() != ownedSlot) {
            cancel(client); retriggerDelay = prefs.retriggerTicks; status = "Cancelled: you changed slots"; return;
        }
        if (pendingTarget != null) {
            if (!valid(client, pendingTarget) || !client.player.hasLineOfSight(pendingTarget)
                    || !client.player.isWithinEntityInteractionRange(pendingTarget.getBoundingBox(), 0)) {
                cancel(client); retriggerDelay = prefs.retriggerTicks;
                status = "Cancelled: target died, left reach or moved behind a wall"; return;
            }
            if (advance) followUpIn--;
            if (followUpIn > 0) return;
            LivingEntity target = pendingTarget;
            int mace = find(client, false);
            if (mace >= 0 && valid(client, target) && selectAndCheck(client, mace, target)) {
                attack(client, target);
                status = "Axe + mace sent; normal damage rules apply";
            } else status = "Follow-up cancelled: target, reach or mace lost";
            if (LungeTestClient.legitMode() && ownedSlot >= 0) VisibleSwapReturn.defer("stun", () -> cancel(client));
            else cancel(client);
            retriggerDelay = prefs.retriggerTicks;
            return;
        }
        if (retriggerDelay > 0) { if (advance) retriggerDelay--; return; }
        LivingEntity target = clickedTarget;
        clickedTarget = null;
        if (target == null && advance && prefs.trigger == Trigger.LOOK
                && client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity looked) target = looked;
        if (!valid(client, target) || !target.isBlocking()) {
            status = "Click a raised shield"; return;
        }
        // Check ordinary axe reach before borrowing anything from the inventory.
        if (!client.player.hasLineOfSight(target) || !client.player.isWithinEntityInteractionRange(target.getBoundingBox(), 0)) {
            status = "Move within axe reach and sight"; return;
        }
        VisibleSwapReturn.finishNow();
        if (!inventory.ensure(client, stack -> stack.getItem() instanceof AxeItem || stack.is(ItemTags.AXES),
                stack -> stack.is(Items.MACE))) {
            status = "Need axe + mace in inventory; close other containers"; return;
        }
        originalSlot = client.player.getInventory().getSelectedSlot();
        int axe = find(client, true);
        if (axe < 0 || !selectAndCheck(client, axe, target)) {
            cancel(client); status = "Waiting for axe reach or item cooldown"; return;
        }
        attack(client, target);
        pendingTarget = target;
        followUpIn = prefs.followUpTicks;
        sequences++;
        status = "Axe hit sent; mace in " + followUpIn + " ticks";
        if (followUpIn == 0) update(client, false);
    }
    public static boolean beginShieldHit(Minecraft client, net.minecraft.world.entity.Entity entity) {
        if (!enabled || dispatching || prefs.trigger != Trigger.CLICK || !(entity instanceof LivingEntity target) || !target.isBlocking()) return false;
        if (pendingTarget != null || VisibleSwapReturn.pending("stun")) {
            boolean manualSmash = client.player != null && CombatAssistModule.enabled(CombatAssistModule.Feature.MACE)
                    && !client.player.onGround() && client.player.getDeltaMovement().y < 0
                    && net.minecraft.world.item.MaceItem.canSmashAttack(client.player);
            return !manualSmash;
        }
        clickedTarget = target;
        long before = sequences;
        try { update(client, false); return sequences != before; }
        finally { clickedTarget = null; }
    }
    public static void yieldForManualAttack(Minecraft client) {
        if (!busy() || dispatching) return;
        cancel(client);
        retriggerDelay = prefs.retriggerTicks;
        status = "Manual mace hit took priority";
    }
    private static boolean valid(Minecraft client, LivingEntity target) {
        if (target == null || target == client.player || !target.isAlive() || target.level() != client.level
                || target.isSpectator()) return false;
        return !(target instanceof Player player) || !player.isCreative() && client.player.canHarmPlayer(player);
    }
    private static boolean selectAndCheck(Minecraft client, int slot, LivingEntity target) {
        client.player.getInventory().setSelectedSlot(slot);
        ownedSlot = slot;
        ((HotbarSyncAccess) client.gameMode).azeium$syncCarriedItem();
        return client.player.hasLineOfSight(target) && client.player.isWithinAttackRange(target.getBoundingBox(), 0)
                && !client.player.getCooldowns().isOnCooldown(client.player.getMainHandItem())
                && !client.player.cannotAttackWithItem(client.player.getMainHandItem(), 0);
    }
    private static void attack(Minecraft client, LivingEntity target) {
        dispatching = true;
        try {
            client.gameMode.attack(client.player, target);
            client.player.swing(InteractionHand.MAIN_HAND);
            CombatTestLog.event("AutoStunSlam", "attack_dispatched", "target=" + target.getUUID()
                    + ",slot=" + client.player.getInventory().getSelectedSlot());
        } finally { dispatching = false; }
    }
    private static int find(Minecraft client, boolean axe) {
        for (int i = 0; i < 9; i++) {
            var stack = client.player.getInventory().getItem(i);
            if (axe ? stack.getItem() instanceof AxeItem || stack.is(ItemTags.AXES) : stack.is(Items.MACE)) return i;
        }
        return -1;
    }
    private static void cancel(Minecraft client) {
        if (client.player != null && connection == client.getConnection() && level == client.level
                && ownedSlot >= 0 && originalSlot >= 0 && client.player.getInventory().getSelectedSlot() == ownedSlot) {
            client.player.getInventory().setSelectedSlot(originalSlot);
            if (client.gameMode != null) ((HotbarSyncAccess) client.gameMode).azeium$syncCarriedItem();
        }
        pendingTarget = null; originalSlot = ownedSlot = -1; followUpIn = 0;
        inventory.release(client);
    }
}
