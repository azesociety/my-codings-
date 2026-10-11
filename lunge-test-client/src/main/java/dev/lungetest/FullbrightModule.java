package dev.lungetest;

import net.minecraft.client.Minecraft;

/** A view-only vanilla lightmap override. Stored gamma and world lighting remain unchanged. */
public final class FullbrightModule {
    private static boolean enabled;
    private FullbrightModule() {}
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) {
        enabled = value;
        var renderer = Minecraft.getInstance().gameRenderer;
        if (renderer != null) renderer.lightTexture().tick();
    }
    public static float ambient(float original) {
        return enabled && Minecraft.getInstance().level != null ? 1 : original;
    }
}
