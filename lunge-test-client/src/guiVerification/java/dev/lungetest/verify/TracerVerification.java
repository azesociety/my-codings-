package dev.lungetest.verify;

import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import dev.lungetest.LungeTestClient;
import dev.lungetest.PlayerHighlightModule;
import dev.lungetest.PlayerTracerModule;
import dev.lungetest.PlayerTracerRenderer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;

/** Verifies actual line buffers and GPU shader compilation without a server connection. */
public final class TracerVerification {
    private static int checks;

    public static void verify(Minecraft client) {
        checks = 0;
        var previousLevel = client.level;
        var previousPlayer = client.player;
        boolean previousEnabled = PlayerTracerModule.enabled();
        int previousColor = PlayerTracerModule.color();
        int previousRange = PlayerTracerModule.range();
        boolean previousHighlightEnabled = PlayerHighlightModule.enabled();
        try {
            var renderType = PlayerTracerRenderer.renderType();
            var pipeline = renderType.pipeline();
            require(pipeline.getDepthTestFunction() == DepthTestFunction.NO_DEPTH_TEST,
                    "Tracer pipeline draws through terrain depth");
            require(!pipeline.isWriteDepth(), "Tracers do not alter scene depth");
            require(RenderSystem.getDevice().precompilePipeline(pipeline).isValid(),
                    "Actual graphics backend compiles the custom tracer shader");
            require(renderType.format().contains(VertexFormatElement.LINE_WIDTH),
                    "Tracer format includes required 1.21.11 per-vertex line width");

            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (Unsafe) unsafeField.get(null);
            var level = (HighlightVerification.FixtureLevel) unsafe.allocateInstance(HighlightVerification.FixtureLevel.class);
            level.loaded = new HashMap<>();
            level.players = new ArrayList<>();
            level.rendering = new ArrayList<>();
            level.tickRate = new TickRateManager();
            var local = (HighlightVerification.FixtureLocalPlayer) unsafe.allocateInstance(HighlightVerification.FixtureLocalPlayer.class);
            set(local, "position", Vec3.ZERO);
            client.level = level;
            client.player = local;
            var front = player(unsafe, level, 17, new Vec3(20, 0, -64));
            var behind = player(unsafe, level, 18, new Vec3(-12, 0, 64));
            var far = player(unsafe, level, 19, new Vec3(512, 0, -8));
            level.players.add(local);
            PlayerHighlightModule.setEnabled(false);
            PlayerTracerModule.setEnabled(false);
            require(PlayerTracerRenderer.extract(client, Vec3.ZERO, new Vec3(0, 0, -1), 1).lines().isEmpty(),
                    "OFF produces no tracer vertices");
            PlayerTracerModule.setRange(-1);
            require(PlayerTracerModule.range() == 16, "Tracer range clamps to sixteen blocks");
            PlayerTracerModule.setRange(2048);
            require(PlayerTracerModule.range() == 1024, "Tracer range clamps to 1024 blocks");
            PlayerTracerModule.setRange(256);
            PlayerTracerModule.setColor(0xAA44EE);
            require(PlayerTracerModule.color() == 0xFFAA44EE, "Tracer color uses opaque configurable RGB");
            PlayerTracerModule.setEnabled(true);
            var frame = PlayerTracerRenderer.extract(client, Vec3.ZERO, new Vec3(0, 0, -1), 1);
            require(frame.lines().size() == 2, "Selected range includes loaded front/behind players and excludes far player/self");
            require(!PlayerHighlightModule.enabled(), "Tracers operate independently of highlight module");
            require(frame.color() == 0xFFAA44EE, "Selected tracer color is captured in immutable frame");
            require(Math.abs(frame.lines().getFirst().startZ() + 0.2F) < 0.00001,
                    "World line starts just in front of the camera near plane");
            require(frame.lines().getFirst().endZ() < 0 && frame.lines().get(1).endZ() > 0,
                    "World endpoints retain forward and backward directions without HUD projection");
            try (var byteBuffer = new ByteBufferBuilder(512)) {
                var vertices = new BufferBuilder(byteBuffer, renderType.mode(), renderType.format());
                PlayerTracerRenderer.emit(new PoseStack(), vertices, frame);
                try (var mesh = vertices.buildOrThrow()) {
                    require(mesh.drawState().vertexCount() == 8,
                            "Two tracers emit two complete expanded line segments through actual BufferBuilder");
                    var bytes = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                    int colorOffset = renderType.format().getOffset(VertexFormatElement.COLOR);
                    require((bytes.get(colorOffset) & 255) == 0xAA
                                    && (bytes.get(colorOffset + 1) & 255) == 0x44
                                    && (bytes.get(colorOffset + 2) & 255) == 0xEE
                                    && (bytes.get(colorOffset + 3) & 255) == 255,
                            "Actual vertex bytes retain configured tracer color");
                    int widthOffset = renderType.format().getOffset(VertexFormatElement.LINE_WIDTH);
                    require(bytes.getFloat(widthOffset) == 1.5F,
                            "Actual vertex bytes supply required visible line width");
                }
            }
            PlayerTracerModule.setRange(1024);
            frame = PlayerTracerRenderer.extract(client, Vec3.ZERO, new Vec3(0, 0, -1), 1);
            require(frame.lines().size() == 3 && PlayerTracerModule.traces(far),
                    "Extended range adds loaded player at 512 blocks");
            level.loaded.remove(front.getId());
            require(!PlayerTracerModule.traces(front), "Unloaded players cannot receive tracers");
            set(behind, "removalReason", Entity.RemovalReason.DISCARDED);
            require(!PlayerTracerModule.traces(behind), "Removed players cannot receive tracers");
            require(PlayerTracerRenderer.extract(client, Vec3.ZERO, new Vec3(0, 0, -1), 1).lines().size() == 1,
                    "Only remaining loaded player is extracted after removal/unload");
            PlayerTracerModule.setEnabled(false);
            require(PlayerTracerRenderer.extract(client, Vec3.ZERO, new Vec3(0, 0, -1), 1).lines().isEmpty(),
                    "Turning tracer module OFF clears next-frame geometry");
            LungeTestClient.LOG.info("PLAYER_TRACERS_VERIFIED: {} graphics/selection checks; actual GPU shader compilation, actual line buffers, depth disabled, independent toggle/color, extended loaded-player range", checks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not initialize no-network tracer rendering fixture", e);
        } finally {
            PlayerTracerModule.setEnabled(previousEnabled);
            PlayerTracerModule.setColor(previousColor);
            PlayerTracerModule.setRange(previousRange);
            PlayerHighlightModule.setEnabled(previousHighlightEnabled);
            client.level = previousLevel;
            client.player = previousPlayer;
        }
    }

    private static HighlightVerification.FixtureRemotePlayer player(Unsafe unsafe,
            HighlightVerification.FixtureLevel level, int id, Vec3 position)
            throws ReflectiveOperationException {
        var player = (HighlightVerification.FixtureRemotePlayer) unsafe.allocateInstance(HighlightVerification.FixtureRemotePlayer.class);
        player.fixtureLevel = level;
        player.tickCount = 1;
        player.xOld = position.x;
        player.yOld = position.y;
        player.zOld = position.z;
        set(player, "id", id);
        set(player, "position", position);
        set(player, "dimensions", EntityDimensions.scalable(0.6F, 1.8F));
        level.loaded.put(id, player);
        level.players.add(player);
        return player;
    }
    private static void set(Object instance, String name, Object value) throws ReflectiveOperationException {
        var field = Entity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
