package dev.lungetest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

/** Local render preferences; never changes entity metadata or sends packets. */
public final class PlayerHighlightModule {
    public static final int DEFAULT_COLOR = 0xFF53DBFF;
    public static final int DEFAULT_RANGE = 256;
    public static final int MIN_RANGE = 16;
    public static final int MAX_RANGE = 1024;
    private static boolean enabled;
    private static int color = DEFAULT_COLOR;
    private static int range = DEFAULT_RANGE;

    private PlayerHighlightModule() {}

    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static int color() { return color; }
    public static int range() { return range; }
    public static void setRange(int blocks) { range = Math.clamp(blocks, MIN_RANGE, MAX_RANGE); }

    /** Outlines are opaque so black remains a valid color rather than NO_OUTLINE. */
    public static void setColor(int argb) { color = 0xFF000000 | (argb & 0xFFFFFF); }

    public static boolean highlights(Entity entity) {
        return enabled && eligiblePlayer(entity) && withinRange(entity, range);
    }

    /** Shared player scope for render modules; all positions must already be loaded by the server. */
    public static boolean eligiblePlayer(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.level != null && client.player != null
                && entity instanceof AbstractClientPlayer && entity != client.player
                && entity.level() == client.level && !entity.isRemoved()
                && client.level.getEntity(entity.getId()) == entity;
    }

    public static boolean withinRange(Entity entity, int blocks) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || entity == null) return false;
        double dx = entity.getX() - client.player.getX();
        double dy = entity.getY() - client.player.getY();
        double dz = entity.getZ() - client.player.getZ();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        return Double.isFinite(distanceSquared) && distanceSquared <= (double) blocks * blocks;
    }
}
