package dev.lungetest.verify;

import dev.lungetest.LungeTestClient;
import dev.lungetest.PlayerHighlightModule;
import com.mojang.blaze3d.platform.DepthTestFunction;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import sun.misc.Unsafe;

/** Uses transformed vanilla extraction with world/render fixtures and no network transport. */
public final class HighlightVerification {
    private static int checks;

    public static void verify(Minecraft client) {
        checks = 0;
        var previousLevel = client.level;
        var previousPlayer = client.player;
        boolean previousEnabled = PlayerHighlightModule.enabled();
        int previousColor = PlayerHighlightModule.color();
        int previousRange = PlayerHighlightModule.range();
        Object previousRendererLevel = null;
        Object previousDispatcher = null;
        Object previousViewArea = null;
        Field rendererLevel = null;
        Field rendererDispatcher = null;
        Field rendererViewArea = null;
        try {
            require(RenderPipelines.OUTLINE_CULL.getDepthTestFunction() == DepthTestFunction.NO_DEPTH_TEST
                            && RenderPipelines.OUTLINE_NO_CULL.getDepthTestFunction() == DepthTestFunction.NO_DEPTH_TEST,
                    "Vanilla outline pipelines draw independently of terrain depth");
            require(!RenderPipelines.OUTLINE_CULL.isWriteDepth() && !RenderPipelines.OUTLINE_NO_CULL.isWriteDepth(),
                    "Outline pass does not overwrite scene depth");
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (Unsafe) unsafeField.get(null);
            var level = (FixtureLevel) unsafe.allocateInstance(FixtureLevel.class);
            level.loaded = new HashMap<>();
            level.players = new ArrayList<>();
            level.rendering = new ArrayList<>();
            level.tickRate = new TickRateManager();
            var local = (FixtureLocalPlayer) unsafe.allocateInstance(FixtureLocalPlayer.class);
            set(Entity.class, local, "position", Vec3.ZERO);
            var remote = (FixtureRemotePlayer) unsafe.allocateInstance(FixtureRemotePlayer.class);
            remote.fixtureLevel = level;
            remote.distanceVisible = true;
            remote.tickCount = 1;
            set(Entity.class, remote, "position", Vec3.ZERO);
            set(Entity.class, remote, "blockPosition", BlockPos.ZERO);
            set(Entity.class, remote, "bb", new AABB(-0.3, 0, -0.3, 0.3, 1.8, 0.3));
            set(Entity.class, remote, "dimensions", EntityDimensions.scalable(0.6F, 1.8F));
            set(Entity.class, remote, "id", 73);
            level.loaded.put(73, remote);
            level.players.add(remote);
            level.rendering.add(remote);
            client.level = level;
            client.player = local;

            var dispatcher = (FixtureDispatcher) unsafe.allocateInstance(FixtureDispatcher.class);
            var entityRenderer = (FixtureRenderer) unsafe.allocateInstance(FixtureRenderer.class);
            dispatcher.renderer = entityRenderer;
            set(EntityRenderer.class, entityRenderer, "entityRenderDispatcher", dispatcher);
            rendererLevel = field(LevelRenderer.class, "level");
            rendererDispatcher = field(LevelRenderer.class, "entityRenderDispatcher");
            rendererViewArea = field(LevelRenderer.class, "viewArea");
            previousRendererLevel = rendererLevel.get(client.levelRenderer);
            previousDispatcher = rendererDispatcher.get(client.levelRenderer);
            previousViewArea = rendererViewArea.get(client.levelRenderer);
            rendererLevel.set(client.levelRenderer, level);
            rendererDispatcher.set(client.levelRenderer, dispatcher);
            rendererViewArea.set(client.levelRenderer, unsafe.allocateInstance(FixtureViewArea.class));
            var camera = new FixtureCamera(local);
            var frustum = new FixtureFrustum();
            var extract = LevelRenderer.class.getDeclaredMethod("extractVisibleEntities", Camera.class,
                    Frustum.class, DeltaTracker.class, LevelRenderState.class);
            extract.setAccessible(true);

            PlayerHighlightModule.setEnabled(false);
            require(!PlayerHighlightModule.highlights(remote), "OFF leaves remote players unmodified");
            require(dispatcher.extractEntity(remote, 0).outlineColor == 0,
                    "Actual vanilla render-state extraction retains NO_OUTLINE while OFF");
            var state = new LevelRenderState();
            extract.invoke(client.levelRenderer, camera, frustum, DeltaTracker.ZERO, state);
            require(state.entityRenderStates.isEmpty(), "OFF does not append hidden players");

            PlayerHighlightModule.setEnabled(true);
            PlayerHighlightModule.setRange(-1);
            require(PlayerHighlightModule.range() == 16, "Highlight range clamps to sixteen blocks");
            PlayerHighlightModule.setRange(2048);
            require(PlayerHighlightModule.range() == 1024, "Highlight range clamps to 1024 blocks");
            PlayerHighlightModule.setRange(256);
            require(PlayerHighlightModule.highlights(remote), "ON selects a loaded remote player");
            require(!PlayerHighlightModule.highlights(local), "Local player is excluded");
            for (int rgb : new int[]{0x53DBFF, 0xFF4F9A, 0x000000}) {
                PlayerHighlightModule.setColor(rgb);
                require(PlayerHighlightModule.color() == (0xFF000000 | rgb),
                        "Configured color always has opaque alpha");
                var extracted = dispatcher.extractEntity(remote, 0);
                require(extracted.outlineColor == (0xFF000000 | rgb) && extracted.appearsGlowing(),
                        "Transformed vanilla extraction uses the chosen outline, including black");
            }
            require(!remote.isCurrentlyGlowing(), "Module never changes entity glow metadata");
            state = new LevelRenderState();
            extract.invoke(client.levelRenderer, camera, frustum, DeltaTracker.ZERO, state);
            require(state.entityRenderStates.size() == 1 && state.haveGlowingEntities,
                    "Occluded loaded player reaches the vanilla outline pass");
            require(state.entityRenderStates.getFirst().outlineColor == PlayerHighlightModule.color(),
                    "Occluded player retains the selected color");

            level.bypassSections = true;
            state = new LevelRenderState();
            extract.invoke(client.levelRenderer, camera, frustum, DeltaTracker.ZERO, state);
            require(state.entityRenderStates.size() == 1,
                    "Already visible player is extracted once without a duplicate model");
            level.bypassSections = false;
            frustum.visible = false;
            state = new LevelRenderState();
            extract.invoke(client.levelRenderer, camera, frustum, DeltaTracker.ZERO, state);
            require(state.entityRenderStates.isEmpty(), "Vanilla frustum culling still applies");
            frustum.visible = true;
            remote.distanceVisible = false;
            state = new LevelRenderState();
            extract.invoke(client.levelRenderer, camera, frustum, DeltaTracker.ZERO, state);
            require(state.entityRenderStates.size() == 1,
                    "Own selected range bypasses vanilla entity-distance culling");
            set(Entity.class, remote, "position", new Vec3(512, 0, 0));
            remote.xOld = 512;
            state = new LevelRenderState();
            extract.invoke(client.levelRenderer, camera, frustum, DeltaTracker.ZERO, state);
            require(state.entityRenderStates.isEmpty(), "Player outside selected range is excluded");
            PlayerHighlightModule.setRange(1024);
            state = new LevelRenderState();
            extract.invoke(client.levelRenderer, camera, frustum, DeltaTracker.ZERO, state);
            require(state.entityRenderStates.size() == 1,
                    "Extended range includes the loaded player at 512 blocks");
            set(Entity.class, remote, "position", Vec3.ZERO);
            remote.xOld = 0;
            PlayerHighlightModule.setRange(256);
            remote.distanceVisible = true;

            level.loaded.clear();
            require(!PlayerHighlightModule.highlights(remote), "Unloaded players are excluded");
            require(dispatcher.extractEntity(remote, 0).outlineColor == 0,
                    "Unloaded player does not receive a custom outline");
            level.loaded.put(73, remote);
            set(Entity.class, remote, "removalReason", Entity.RemovalReason.DISCARDED);
            require(!PlayerHighlightModule.highlights(remote), "Removed players are excluded");
            set(Entity.class, remote, "removalReason", null);
            var otherLevel = (FixtureLevel) unsafe.allocateInstance(FixtureLevel.class);
            remote.fixtureLevel = otherLevel;
            require(!PlayerHighlightModule.highlights(remote), "Players from another world are excluded");
            remote.fixtureLevel = level;
            PlayerHighlightModule.setEnabled(false);
            remote.baselineGlow = true;
            require(dispatcher.extractEntity(remote, 0).outlineColor == 0xFF112233,
                    "OFF preserves vanilla glowing/team color");
            remote.baselineGlow = false;
            require(dispatcher.extractEntity(remote, 0).outlineColor == 0,
                    "OFF restores NO_OUTLINE on the next extraction");
            LungeTestClient.LOG.info("PLAYER_HIGHLIGHT_VERIFIED: {} transformed vanilla extraction checks; colors, extended range, hidden-player inclusion, no duplicates, loaded-player scope and frustum culling", checks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not verify transformed player highlight rendering", e);
        } finally {
            PlayerHighlightModule.setEnabled(previousEnabled);
            PlayerHighlightModule.setColor(previousColor);
            PlayerHighlightModule.setRange(previousRange);
            client.level = previousLevel;
            client.player = previousPlayer;
            try {
                if (rendererLevel != null) rendererLevel.set(client.levelRenderer, previousRendererLevel);
                if (rendererDispatcher != null) rendererDispatcher.set(client.levelRenderer, previousDispatcher);
                if (rendererViewArea != null) rendererViewArea.set(client.levelRenderer, previousViewArea);
            } catch (IllegalAccessException e) {
                throw new AssertionError("Could not restore renderer fixture fields", e);
            }
        }
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void set(Class<?> owner, Object instance, String name, Object value)
            throws ReflectiveOperationException { field(owner, name).set(instance, value); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public static final class FixtureLevel extends ClientLevel {
        Map<Integer, Entity> loaded;
        List<AbstractClientPlayer> players;
        List<Entity> rendering;
        TickRateManager tickRate;
        boolean bypassSections;
        private FixtureLevel() { super(null, null, null, null, 0, 0, null, false, 0, 0); }
        @Override public Entity getEntity(int id) { return loaded.get(id); }
        @Override public List<AbstractClientPlayer> players() { return players; }
        @Override public Iterable<Entity> entitiesForRendering() { return rendering; }
        @Override public TickRateManager tickRateManager() { return tickRate; }
        @Override public boolean isOutsideBuildHeight(int y) { return bypassSections; }
    }
    public static final class FixtureLocalPlayer extends LocalPlayer {
        private FixtureLocalPlayer() { super(null, null, null, null, null, null, false); }
        @Override public boolean isSpectator() { return false; }
    }
    public static final class FixtureRemotePlayer extends RemotePlayer {
        ClientLevel fixtureLevel;
        boolean distanceVisible;
        boolean baselineGlow;
        private FixtureRemotePlayer() { super(null, null); }
        @Override public Level level() { return fixtureLevel; }
        @Override public EntityType<?> getType() { return EntityType.PLAYER; }
        @Override public boolean shouldRender(double x, double y, double z) { return distanceVisible; }
        @Override public boolean isInvisible() { return false; }
        @Override public boolean isDiscrete() { return false; }
        @Override public boolean isPassenger() { return false; }
        @Override public boolean hasIndirectPassenger(Entity entity) { return false; }
        @Override public boolean displayFireAnimation() { return false; }
        @Override public boolean isCurrentlyGlowing() { return baselineGlow; }
        @Override public int getTeamColor() { return 0x112233; }
        @Override public Vec3 getLightProbePosition(float partialTick) { return Vec3.ZERO; }
    }
    public static final class FixtureRenderer extends EntityRenderer<Entity, EntityRenderState> {
        private FixtureRenderer() { super(null); }
        @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }
        @Override protected int getSkyLightLevel(Entity entity, BlockPos pos) { return 15; }
        @Override protected int getBlockLightLevel(Entity entity, BlockPos pos) { return 0; }
    }
    public static final class FixtureDispatcher extends EntityRenderDispatcher {
        FixtureRenderer renderer;
        private FixtureDispatcher() { super(null, null, null, null, null, null, null, null, null, null, null); }
        @Override public <E extends Entity> boolean shouldRender(E entity, Frustum frustum,
                double x, double y, double z) { return renderer.shouldRender(entity, frustum, x, y, z); }
        @Override public <E extends Entity> EntityRenderState extractEntity(E entity, float partialTick) {
            var state = new EntityRenderState();
            renderer.extractRenderState(entity, state, partialTick);
            return state;
        }
    }
    public static final class FixtureFrustum extends Frustum {
        boolean visible = true;
        FixtureFrustum() { super(new Matrix4f(), new Matrix4f()); }
        @Override public boolean isVisible(AABB box) { return visible; }
    }
    public static final class FixtureCamera extends Camera {
        private final Entity fixtureEntity;
        FixtureCamera(Entity fixtureEntity) { this.fixtureEntity = fixtureEntity; }
        @Override public Vec3 position() { return Vec3.ZERO; }
        @Override public Entity entity() { return fixtureEntity; }
        @Override public boolean isDetached() { return false; }
    }
    public static final class FixtureViewArea extends ViewArea {
        private FixtureViewArea() { super(null, null, 0, null); }
        @Override protected SectionRenderDispatcher.RenderSection getRenderSectionAt(BlockPos position) {
            return null;
        }
    }
}
