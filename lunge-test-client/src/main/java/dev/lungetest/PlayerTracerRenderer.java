package dev.lungetest;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Extracts immutable render data, then draws camera-relative world lines above terrain. */
public final class PlayerTracerRenderer {
    private static final RenderStateDataKey<Frame> FRAME =
            RenderStateDataKey.create(() -> "azeium player tracers");
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("azeium", "pipeline/player_tracers"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("azeium", "core/player_tracers"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false).build());
    private static final RenderType RENDER_TYPE = RenderType.create("azeium_player_tracers",
            RenderSetup.builder(PIPELINE).createRenderSetup());

    private PlayerTracerRenderer() {}

    static void register() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            var forward = context.camera().forwardVector();
            float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
            ((FabricRenderState) context.worldState()).setData(FRAME,
                    extract(Minecraft.getInstance(), context.camera().position(),
                            new Vec3(forward.x(), forward.y(), forward.z()), partialTick));
        });
        WorldRenderEvents.END_MAIN.register(context -> {
            Frame frame = ((FabricRenderState) context.worldState()).getDataOrDefault(FRAME, Frame.EMPTY);
            if (frame.lines().isEmpty()) return;
            emit(context.matrices(), context.consumers().getBuffer(RENDER_TYPE), frame);
            // Fabric invokes END_MAIN immediately before vanilla flushes this buffer source.
        });
    }

    /** Kept public for the no-network renderer fixture. Contains no entity references. */
    public static Frame extract(Minecraft client, Vec3 cameraPosition, Vec3 cameraForward,
                                float partialTick) {
        if (!PlayerTracerModule.enabled() || client == null || client.level == null || client.player == null
                || !finite(cameraPosition) || !finite(cameraForward) || cameraForward.lengthSqr() < 1.0E-12)
            return Frame.EMPTY;
        List<Line> lines = new ArrayList<>();
        Vec3 origin = cameraForward.normalize().scale(0.2);
        for (var player : client.level.players()) {
            if (!PlayerTracerModule.traces(player)) continue;
            Vec3 end = player.getPosition(partialTick)
                    .add(0, player.getBbHeight() * 0.5, 0).subtract(cameraPosition);
            if (!finite(end) || end.distanceToSqr(origin) < 1.0E-8) continue;
            lines.add(new Line((float) origin.x, (float) origin.y, (float) origin.z,
                    (float) end.x, (float) end.y, (float) end.z));
        }
        return new Frame(PlayerTracerModule.color(), lines);
    }

    public static void emit(PoseStack matrices, VertexConsumer vertices, Frame frame) {
        var pose = matrices.last();
        for (Line line : frame.lines()) {
            float dx = line.endX - line.startX;
            float dy = line.endY - line.startY;
            float dz = line.endZ - line.startZ;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!Float.isFinite(length) || length <= 1.0E-4F) continue;
            float nx = dx / length, ny = dy / length, nz = dz / length;
            vertices.addVertex(pose, line.startX, line.startY, line.startZ)
                    .setColor(frame.color()).setNormal(pose, nx, ny, nz).setLineWidth(1.5F);
            vertices.addVertex(pose, line.endX, line.endY, line.endZ)
                    .setColor(frame.color()).setNormal(pose, nx, ny, nz).setLineWidth(1.5F);
        }
    }

    private static boolean finite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
    public static RenderType renderType() { return RENDER_TYPE; }
    public record Line(float startX, float startY, float startZ,
                       float endX, float endY, float endZ) {}
    public record Frame(int color, List<Line> lines) {
        public static final Frame EMPTY = new Frame(0, List.of());
        public Frame { lines = List.copyOf(lines); }
    }
}
