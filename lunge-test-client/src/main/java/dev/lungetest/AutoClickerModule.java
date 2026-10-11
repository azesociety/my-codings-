package dev.lungetest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Tick-paced vanilla clicks; does not synthesize held global key states or catch up after pauses. */
public final class AutoClickerModule {
    public enum Mode { TOGGLE, HOLD }
    public enum Button { ATTACK, USE }
    public static final class Preferences {
        public Mode mode = Mode.TOGGLE;
        public Button button = Button.ATTACK;
        public int cps = 8;
    }
    private static final int[] RATES = {1, 2, 4, 6, 8, 10, 12, 16, 20};
    private static Preferences prefs = new Preferences();
    private static boolean enabled;
    private static double credit;
    private static ClientLevel level;
    private static ClientPacketListener connection;
    private static String status = "Disabled";
    private AutoClickerModule() {}
    public static void initialize(Preferences saved) {
        prefs = saved == null ? new Preferences() : saved;
        if (prefs.mode == null) prefs.mode = Mode.TOGGLE;
        if (prefs.button == null) prefs.button = Button.ATTACK;
        prefs.cps = Math.clamp(prefs.cps, 1, 20);
        setEnabled(false);
    }
    public static Preferences preferences() { return prefs; }
    public static boolean enabled() { return enabled; }
    public static String status() { return status; }
    public static String modeLabel() { return "Mode: " + (prefs.mode == Mode.TOGGLE ? "Toggle" : "Hold mouse"); }
    public static String buttonLabel() { return "Click: " + (prefs.button == Button.ATTACK ? "Left / attack" : "Right / use"); }
    public static String rateLabel() { return "Rate: " + prefs.cps + " CPS"; }
    public static void cycleMode() { prefs.mode = prefs.mode == Mode.TOGGLE ? Mode.HOLD : Mode.TOGGLE; changed(); }
    public static void cycleButton() { prefs.button = prefs.button == Button.ATTACK ? Button.USE : Button.ATTACK; changed(); }
    public static void cycleRate() {
        int next = RATES[0];
        for (int rate : RATES) if (rate > prefs.cps) { next = rate; break; }
        prefs.cps = next; changed();
    }
    private static void changed() { credit = 0; LungeTestClient.saveClickerPreferences(); }
    public static void setEnabled(boolean value) {
        enabled = value; credit = 0; level = null; connection = null;
        status = value ? "Close the menu to click" : "Disabled";
    }
    public static void tick(Minecraft client) {
        if (!enabled) return;
        if (client.player == null || client.level == null || client.gameMode == null || client.getConnection() == null
                || !client.player.isAlive() || client.player.isSpectator()
                || level != null && (level != client.level || connection != client.getConnection())) {
            setEnabled(false); return;
        }
        if (level == null) { level = client.level; connection = client.getConnection(); }
        if (client.screen != null || client.isPaused() || !client.isWindowActive() || client.player.isUsingItem()) {
            credit = 0; status = "Paused: screen, item use or focus"; return;
        }
        if (prefs.mode == Mode.HOLD && !(prefs.button == Button.ATTACK
                ? client.options.keyAttack.isDown() : client.options.keyUse.isDown())) {
            credit = 0; status = "Hold " + (prefs.button == Button.ATTACK ? "left" : "right") + " mouse to click"; return;
        }
        if (CombatAssistModule.busy() || AutoStunSlamModule.busy() || AutoClutchModule.actedThisTick() || TellyPilotModule.active()
                || ShieldDrainerModule.enabled()) {
            credit = 0; status = "Paused: another action is running"; return;
        }
        credit = Math.min(2, credit + prefs.cps / 20.0);
        if (credit + 1e-9 < 1) return;
        credit -= 1;
        if (prefs.button == Button.ATTACK) ((SpearAttackAccess) client).azeium$startAttack();
        else ((ClickActionAccess) client).azeium$startUseItem();
        status = "Running: " + prefs.cps + " requested CPS";
    }
}
