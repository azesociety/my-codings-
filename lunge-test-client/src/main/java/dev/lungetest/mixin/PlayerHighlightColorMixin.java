package dev.lungetest.mixin;

import dev.lungetest.PlayerHighlightModule;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class PlayerHighlightColorMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void azeium$playerOutline(Entity entity, EntityRenderState state, float partialTick,
                                      CallbackInfo ci) {
        if (PlayerHighlightModule.highlights(entity)) {
            state.outlineColor = PlayerHighlightModule.color();
        }
    }
}
