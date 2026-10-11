package dev.lungetest.verify;

import dev.lungetest.AzeiumModules;
import dev.lungetest.CoordinatesModule;
import dev.lungetest.LungeTestClient;
import dev.lungetest.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;

/** Checks real transformed GameRenderer FOV calls and position display, without a network session. */
public final class UtilityModuleVerification {
    private static int checks;

    private UtilityModuleVerification() {}

    public static void verify(Minecraft client) {
        checks = 0;
        var previousPlayer = client.player;
        var previousLevel = client.level;
        var previousScreen = client.screen;
        boolean previousCoordinates = CoordinatesModule.enabled();
        boolean previousZoom = ZoomModule.enabled();
        int previousFactor = ZoomModule.factor();
        Field scale = null, lastFrame = null, oldFov = null, fov = null, storedFactor = null;
        Object settings = null;
        double previousScale = 1;
        long previousFrame = 0;
        float previousOldFov = 1, previousFov = 1;
        int previousStoredFactor = 4;
        try {
            var unsafeField = field(Unsafe.class, "theUnsafe");
            var unsafe = (Unsafe) unsafeField.get(null);
            var player = (ClutchPlacementVerification.PlacementPlayer)
                    unsafe.allocateInstance(ClutchPlacementVerification.PlacementPlayer.class);
            var level = (ClutchPlacementVerification.PlacementLevel)
                    unsafe.allocateInstance(ClutchPlacementVerification.PlacementLevel.class);
            player.fixtureLevel = level;
            player.alive = true;
            set(Entity.class, player, "position", new Vec3(-1.25, 64.75, -4.01));
            set(Entity.class, player, "blockPosition", new BlockPos(-2, 64, -5));
            player.setYRot(90);
            client.player = player;
            client.level = level;
            client.screen = null;

            CoordinatesModule.setEnabled(false);
            ZoomModule.setEnabled(false);
            var states = states();
            AzeiumModules.Module.COORDINATES.toggle(client);
            require(CoordinatesModule.enabled(), "The Coordinates module toggle enables its HUD");
            requireUnchangedExcept(states, AzeiumModules.Module.COORDINATES);
            require(CoordinatesModule.readout(player).equals("XYZ  -2 / 64 / -5  |  west"),
                    "Coordinates retain negative block positions and the actual west-facing direction");
            player.setYRot(-90);
            require(CoordinatesModule.readout(player).endsWith("  |  east"),
                    "Changing player yaw updates the direction in the readout");
            states = states();
            AzeiumModules.Module.ZOOM.toggle(client);
            require(ZoomModule.enabled() && CoordinatesModule.enabled(),
                    "Zoom can be enabled alongside Coordinates");
            requireUnchangedExcept(states, AzeiumModules.Module.ZOOM);

            scale = field(ZoomModule.class, "scale");
            lastFrame = field(ZoomModule.class, "lastFrame");
            previousScale = scale.getDouble(null);
            previousFrame = lastFrame.getLong(null);
            oldFov = field(GameRenderer.class, "oldFovModifier");
            fov = field(GameRenderer.class, "fovModifier");
            previousOldFov = oldFov.getFloat(client.gameRenderer);
            previousFov = fov.getFloat(client.gameRenderer);
            oldFov.setFloat(client.gameRenderer, 1);
            fov.setFloat(client.gameRenderer, 1);
            settings = field(LungeTestClient.class, "settings").get(null);
            storedFactor = field(settings.getClass(), "zoomFactor");
            previousStoredFactor = storedFactor.getInt(settings);
            Method getFov = GameRenderer.class.getDeclaredMethod("getFov", Camera.class, float.class, boolean.class);
            getFov.setAccessible(true);
            var camera = new DryCamera();

            ZoomModule.setEnabled(false);
            scale.setDouble(null, 1);
            lastFrame.setLong(null, 0);
            float baseline = actualFov(getFov, client, camera, true);
            require(close(baseline, client.options.fov().get()),
                    "The real GameRenderer retains the stored FOV while Zoom is off");
            int storedFov = client.options.fov().get();
            ZoomModule.setFactor(3);
            require(ZoomModule.factor() == 4, "Unsupported zoom factors use the default 4x setting");
            ZoomModule.setFactor(2);
            ZoomModule.cycleFactor();
            require(ZoomModule.factor() == 4 && storedFactor.getInt(settings) == 4,
                    "Cycling 2x to 4x persists the chosen zoom factor");
            ZoomModule.cycleFactor();
            require(ZoomModule.factor() == 8, "Cycling 4x reaches 8x");
            ZoomModule.cycleFactor();
            require(ZoomModule.factor() == 2, "Cycling 8x wraps to 2x");

            ZoomModule.setFactor(4);
            ZoomModule.setEnabled(true);
            scale.setDouble(null, 1);
            lastFrame.setLong(null, 0);
            require(close(actualFov(getFov, client, camera, true), baseline),
                    "Enabling Zoom starts from the current FOV without an instant jump");
            advanceFrame(lastFrame);
            float first = actualFov(getFov, client, camera, true);
            require(first < baseline && first > baseline / 4,
                    "The transformed GameRenderer animates its first frame toward 4x zoom");
            for (int i = 0; i < 16; i++) {
                advanceFrame(lastFrame);
                actualFov(getFov, client, camera, true);
            }
            require(close(actualFov(getFov, client, camera, true), baseline / 4),
                    "Actual renderer calls converge on the configured 4x world-view FOV");
            for (int factor : new int[]{2, 8}) {
                ZoomModule.setFactor(factor);
                for (int i = 0; i < 16; i++) {
                    advanceFrame(lastFrame);
                    actualFov(getFov, client, camera, true);
                }
                require(close(actualFov(getFov, client, camera, true), baseline / factor),
                        "Actual renderer calls converge on the configured " + factor + "x zoom");
            }
            ZoomModule.setFactor(4);
            for (int i = 0; i < 16; i++) {
                advanceFrame(lastFrame);
                actualFov(getFov, client, camera, true);
            }
            long worldFrame = lastFrame.getLong(null);
            require(close(actualFov(getFov, client, camera, false), 70)
                            && lastFrame.getLong(null) == worldFrame,
                    "Hand-view FOV remains vanilla and does not advance zoom animation");
            require(client.options.fov().get() == storedFov,
                    "Zoom leaves the stored Minecraft FOV preference unchanged");

            ZoomModule.setEnabled(false);
            advanceFrame(lastFrame);
            float releasing = actualFov(getFov, client, camera, true);
            require(releasing > baseline / 4 && releasing < baseline,
                    "Disabling Zoom smoothly returns toward the vanilla world FOV");
            for (int i = 0; i < 16; i++) {
                advanceFrame(lastFrame);
                actualFov(getFov, client, camera, true);
            }
            require(close(actualFov(getFov, client, camera, true), baseline),
                    "Disabling Zoom converges on the original FOV");

            ZoomModule.setEnabled(true);
            scale.setDouble(null, 0.25);
            client.screen = previousScreen;
            if (client.screen == null) client.screen = new net.minecraft.client.gui.screens.TitleScreen();
            require(close(actualFov(getFov, client, camera, true), baseline)
                            && scale.getDouble(null) == 1 && lastFrame.getLong(null) == 0,
                    "Opening a menu clears zoom interpolation and displays vanilla FOV");
            client.screen = null;
            client.player = null;
            scale.setDouble(null, 0.25);
            require(close(actualFov(getFov, client, camera, true), baseline),
                    "A missing local player immediately disables view zoom");
            client.player = player;
            client.level = null;
            scale.setDouble(null, 0.25);
            require(close(actualFov(getFov, client, camera, true), baseline),
                    "A missing world immediately disables view zoom");
            LungeTestClient.LOG.info("UTILITY_MODULES_VERIFIED: {} checks; independent Coordinates/Zoom toggles, negative positions and facing, actual transformed GameRenderer FOV calls, smooth 2x/4x/8x settings, menu/disconnect guards", checks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not verify utility modules with transformed renderer calls", e);
        } finally {
            CoordinatesModule.setEnabled(previousCoordinates);
            ZoomModule.setEnabled(previousZoom);
            ZoomModule.setFactor(previousFactor);
            client.player = previousPlayer;
            client.level = previousLevel;
            client.screen = previousScreen;
            try {
                if (scale != null) scale.setDouble(null, previousScale);
                if (lastFrame != null) lastFrame.setLong(null, previousFrame);
                if (oldFov != null) oldFov.setFloat(client.gameRenderer, previousOldFov);
                if (fov != null) fov.setFloat(client.gameRenderer, previousFov);
                if (storedFactor != null) storedFactor.setInt(settings, previousStoredFactor);
            } catch (IllegalAccessException e) {
                throw new AssertionError("Could not restore utility module fixture fields", e);
            }
        }
    }

    private static float actualFov(Method getFov, Minecraft client, Camera camera, boolean worldView)
            throws ReflectiveOperationException {
        return (float) getFov.invoke(client.gameRenderer, camera, 1F, worldView);
    }

    private static void advanceFrame(Field lastFrame) throws IllegalAccessException {
        // Advance the module's frame clock deterministically; no runtime sleeps or fake FOV result.
        lastFrame.setLong(null, System.nanoTime() - 100_000_000L);
    }

    private static boolean close(float a, float b) { return Math.abs(a - b) < 0.005F; }

    private static EnumMap<AzeiumModules.Module, Boolean> states() {
        var result = new EnumMap<AzeiumModules.Module, Boolean>(AzeiumModules.Module.class);
        for (var module : AzeiumModules.Module.values()) result.put(module, module.enabled());
        return result;
    }

    private static void requireUnchangedExcept(EnumMap<AzeiumModules.Module, Boolean> before,
                                               AzeiumModules.Module changed) {
        for (var module : AzeiumModules.Module.values()) if (module != changed)
            require(module.enabled() == before.get(module), "Toggling " + changed + " leaves " + module + " unchanged");
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        var result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void set(Class<?> owner, Object target, String name, Object value)
            throws ReflectiveOperationException { field(owner, name).set(target, value); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    /** Keeps vanilla FOV calculations independent of underwater/death effects in this fixture. */
    private static final class DryCamera extends Camera {
        @Override public Entity entity() { return null; }
        @Override public FogType getFluidInCamera() { return FogType.NONE; }
    }
}
