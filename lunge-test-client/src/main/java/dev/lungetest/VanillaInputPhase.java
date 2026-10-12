package dev.lungetest;

import net.minecraft.client.Minecraft;

/** Uses Minecraft's own input phase, before player movement and the client tick-end packet. */
public final class VanillaInputPhase {
    private static boolean active;
    private VanillaInputPhase() {}
    public static boolean active() { return active; }
    public static void beforeInput(Minecraft client) {
        active = true;
        try { VisibleSwapReturn.tick(client); }
        finally { active = false; }
    }
    public static void afterInput(Minecraft client) {
        active = true;
        try { SpearQuickLungeModule.tick(client); }
        finally { active = false; }
    }
}
