package dev.lungetest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/** Timed, finite crosshair combat trials using normal game-mode attacks and hotbar selection. */
public final class ShieldDrainerModule {
    public enum Scenario { MACE_CLICKS, AXE_TO_MACE }
    public static final class Preferences {
        public int clicksPerSecond = 10, durationSeconds = 10, reactionTicks = 0, switchTicks = 2;
        public boolean shieldOnly = true;
        public Scenario scenario = Scenario.MACE_CLICKS;
    }
    private static Preferences prefs = new Preferences();
    private static boolean enabled, armed, maceNext;
    private static long tick, started, nextAction;
    private static double credit;
    private static java.util.UUID comboTarget;
    private static int originalSlot = -1, ownedSlot = -1;
    private static ClientLevel level;
    private static ClientPacketListener connection;
    private static String status = "Disabled";
    private ShieldDrainerModule() {}
    public static void initialize(Preferences value) {
        prefs = value == null ? new Preferences() : value;
        prefs.clicksPerSecond = Math.clamp(prefs.clicksPerSecond, 1, 40);
        prefs.durationSeconds = Math.clamp(prefs.durationSeconds, 1, 60);
        prefs.reactionTicks = Math.clamp(prefs.reactionTicks, 0, 200);
        prefs.switchTicks = Math.clamp(prefs.switchTicks, 1, 200);
        if (prefs.scenario == null) prefs.scenario = Scenario.MACE_CLICKS;
        setEnabled(false);
    }
    public static Preferences preferences() { return prefs; }
    public static boolean enabled() { return enabled; }
    public static String status() { return status; }
    public static void settingsChanged() { setEnabled(false); LungeTestClient.saveTestPreferences(); }
    public static void setEnabled(boolean value) {
        var client = Minecraft.getInstance();
        if (armed && client.player != null && client.level == level && client.getConnection() == connection
                && ownedSlot >= 0 && client.player.getInventory().getSelectedSlot() == ownedSlot)
            client.player.getInventory().setSelectedSlot(originalSlot);
        if (enabled) CombatTestLog.event("ShieldDrainer", "trial_stopped", status);
        enabled = value;
        armed = false;
        originalSlot = ownedSlot = -1;
        maceNext = false;
        comboTarget = null;
        credit = 0;
        status = value ? "Close the menu to start the trial" : "Disabled";
        CombatTestLog.flush();
    }
    private static void stop(String reason) {
        CombatTestLog.event("ShieldDrainer", "stop_reason", reason);
        setEnabled(false); status = reason;
    }
    public static void tick(Minecraft client) {
        tick++;
        if (!enabled) return;
        if (client.player == null || client.level == null || client.getConnection() == null || client.gameMode == null
                || !client.player.isAlive() || client.player.isSpectator()) { stop("Stopped: no live player"); return; }
        if (client.screen != null || !client.isWindowActive() || client.isPaused()) {
            if (armed) stop("Stopped: menu or focus change");
            return;
        }
        if (!armed) {
            armed = true;
            level = client.level;
            connection = client.getConnection();
            started = tick;
            nextAction = tick + prefs.reactionTicks;
            originalSlot = client.player.getInventory().getSelectedSlot();
            CombatTestLog.begin("ShieldDrainer");
            CombatTestLog.event("ShieldDrainer", "trial_settings", new com.google.gson.Gson().toJson(prefs));
        }
        if (client.level != level || client.getConnection() != connection) { stop("Stopped: world changed"); return; }
        if (tick - started >= prefs.durationSeconds * 20L) { stop("Trial complete; trace saved"); return; }
        if (ownedSlot >= 0 && client.player.getInventory().getSelectedSlot() != ownedSlot) {
            stop("Stopped: manual hotbar change"); return;
        }
        if (AutoClutchModule.enabled() || CombatAssistModule.enabled(CombatAssistModule.Feature.MACE)
                || CombatAssistModule.enabled(CombatAssistModule.Feature.STUNSLAM)) {
            status = "Pause other combat/bridge modules first";
            credit = 0;
            return;
        }
        if (client.player.isUsingItem() || tick < nextAction) return;
        if (!(client.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity target)
                || target == client.player || !target.isAlive() || target.isRemoved()
                || prefs.shieldOnly && !target.isBlocking() && !maceNext || !client.player.hasLineOfSight(target)) {
            status = prefs.shieldOnly ? "Aim at a blocking shield target" : "Aim at a living test target";
            credit = 0;
            return;
        }
        if (maceNext && !target.getUUID().equals(comboTarget)) {
            stop("Stopped: combo target changed"); return;
        }
        credit = Math.min(2, credit + prefs.clicksPerSecond / 20.0);
        while (credit + 1e-9 >= 1 && enabled) {
            int slot = findSlot(client, prefs.scenario == Scenario.AXE_TO_MACE && !maceNext);
            if (slot < 0) { stop("Stopped: required tool missing from hotbar"); return; }
            client.player.getInventory().setSelectedSlot(slot);
            ownedSlot = slot;
            ItemStack stack = client.player.getMainHandItem();
            if (!client.player.isWithinAttackRange(target.getBoundingBox(), 0)
                    || client.player.getCooldowns().isOnCooldown(stack)) {
                status = "Waiting for normal reach/item cooldown";
                credit = 0;
                return;
            }
            float strength = client.player.getAttackStrengthScale(.5f);
            client.gameMode.attack(client.player, target);
            client.player.swing(InteractionHand.MAIN_HAND);
            CombatTestLog.event("ShieldDrainer", "attack_dispatched", "target=" + target.getUUID()
                    + ",slot=" + slot + ",item=" + stack.getItem() + ",strength=" + strength
                    + ",blocking=" + target.isBlocking());
            credit -= 1;
            status = "Running: " + prefs.scenario + " · " + prefs.clicksPerSecond + " requested CPS";
            if (prefs.scenario == Scenario.AXE_TO_MACE) {
                maceNext = !maceNext;
                comboTarget = maceNext ? target.getUUID() : null;
                nextAction = tick + prefs.switchTicks;
                credit = 0;
                break;
            }
        }
    }
    private static int findSlot(Minecraft client, boolean axe) {
        for (int slot = 0; slot < 9; slot++) {
            var stack = client.player.getInventory().getItem(slot);
            if (axe ? stack.getItem() instanceof AxeItem : stack.is(Items.MACE)) return slot;
        }
        return -1;
    }
}
