package dev.lungetest;

import net.minecraft.client.Minecraft;

/** Holds the actual selected hotbar weapon briefly; restoration keeps each helper's ownership checks. */
public final class VisibleSwapReturn {
    private static Runnable restore;
    private static String owner;
    private static int ticksLeft;
    private VisibleSwapReturn() {}
    public static boolean pending(String key) { return restore != null && key.equals(owner); }
    public static void defer(String key, Runnable action) {
        if (pending(key)) return;
        finishNow();
        owner = key; restore = action; ticksLeft = 4;
    }
    public static void finishNow() {
        Runnable action = restore;
        restore = null; owner = null; ticksLeft = 0;
        if (action != null) action.run();
    }
    public static void tick(Minecraft client) {
        if (restore == null) return;
        if (!LungeTestClient.legitMode() || client.player == null || !client.player.isAlive()
                || client.screen != null || !client.isWindowActive() || --ticksLeft <= 0) finishNow();
    }
}
