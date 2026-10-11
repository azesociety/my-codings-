package dev.lungetest.mixin;

import dev.lungetest.PlayerHighlightModule;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class PlayerHighlightVisibilityMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow protected abstract boolean shouldShowEntityOutlines();
    @Shadow private EntityRenderState extractEntity(Entity entity, float partialTick) {
        throw new AssertionError("Mixin shadow");
    }

    @Unique private final Set<Entity> azeium$extractedPlayers =
            Collections.newSetFromMap(new IdentityHashMap<>());

    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void azeium$beginPlayerPass(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
                                        LevelRenderState state, CallbackInfo ci) {
        azeium$extractedPlayers.clear();
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void azeium$rememberPlayer(Entity entity, float partialTick,
                                       CallbackInfoReturnable<EntityRenderState> cir) {
        if (PlayerHighlightModule.highlights(entity)) azeium$extractedPlayers.add(entity);
    }

    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void azeium$includeOccludedPlayers(Camera camera, Frustum frustum,
                                               DeltaTracker deltaTracker, LevelRenderState state,
                                               CallbackInfo ci) {
        if (!PlayerHighlightModule.enabled() || minecraft.level == null
                || !shouldShowEntityOutlines()) {
            azeium$extractedPlayers.clear();
            return;
        }
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (!PlayerHighlightModule.highlights(player)
                    || azeium$extractedPlayers.contains(player)
                    || !frustum.isVisible(player.getBoundingBox().inflate(0.5))) continue;
            // Vanilla skips sections hidden by the terrain occlusion graph before extraction.
            // Include highlighted players within the selected range even beyond vanilla's
            // entity-distance gate; retain camera-frustum checks to avoid off-screen models.
            if (player.tickCount == 0) {
                player.xOld = player.getX();
                player.yOld = player.getY();
                player.zOld = player.getZ();
            }
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(
                    !minecraft.level.tickRateManager().isEntityFrozen(player));
            EntityRenderState playerState = extractEntity(player, partialTick);
            state.entityRenderStates.add(playerState);
            if (playerState.appearsGlowing()) state.haveGlowingEntities = true;
        }
        azeium$extractedPlayers.clear();
    }
}
