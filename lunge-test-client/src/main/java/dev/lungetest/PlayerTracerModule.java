package dev.lungetest;

import net.minecraft.world.entity.Entity;

/** Independent local world-render module; never alters entity or network state. */
public final class PlayerTracerModule {
    public static final int DEFAULT_COLOR = 0xFF8FA8FF;
    public static final int DEFAULT_RANGE = 256;
    public static final int MIN_RANGE = 16;
    public static final int MAX_RANGE = 1024;
    private static boolean enabled;
    private static int color = DEFAULT_COLOR;
    private static int range = DEFAULT_RANGE;
    private static boolean registered;

    private PlayerTracerModule() {}

    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static int color() { return color; }
    public static void setColor(int argb) { color = 0xFF000000 | (argb & 0xFFFFFF); }
    public static int range() { return range; }
    public static void setRange(int blocks) { range = Math.clamp(blocks, MIN_RANGE, MAX_RANGE); }
    public static boolean traces(Entity entity) {
        return enabled && PlayerHighlightModule.eligiblePlayer(entity)
                && PlayerHighlightModule.withinRange(entity, range);
    }
    public static void register() {
        if (registered) return;
        registered = true;
        PlayerTracerRenderer.register();
    }
}
