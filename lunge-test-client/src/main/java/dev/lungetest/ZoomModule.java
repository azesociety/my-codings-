package dev.lungetest;

import net.minecraft.client.Minecraft;

/** Smooth field-of-view zoom without changing stored FOV or mouse settings. */
public final class ZoomModule {
    private static boolean enabled;
    private static int factor = 4;
    private static double scale = 1;
    private static long lastFrame;
    private ZoomModule() {}
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static int factor() { return factor; }
    public static void setFactor(int value) { factor = value == 2 || value == 8 ? value : 4; }
    public static void cycleFactor() {
        factor = factor == 2 ? 4 : factor == 4 ? 8 : 2;
        LungeTestClient.saveZoomFactor();
    }
    public static float apply(float fov, boolean worldView) {
        if (!worldView) return fov;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.screen != null) {
            scale = 1;
            lastFrame = 0;
            return fov;
        }
        long now = System.nanoTime();
        double seconds = lastFrame == 0 ? 0 : Math.min(0.1, Math.max(0, (now - lastFrame) / 1.0e9));
        lastFrame = now;
        double target = enabled ? 1.0 / factor : 1;
        scale += (target - scale) * (1 - Math.exp(-14 * seconds));
        return (float) (fov * scale);
    }
}
