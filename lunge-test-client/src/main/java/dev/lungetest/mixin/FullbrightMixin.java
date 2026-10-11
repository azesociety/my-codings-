package dev.lungetest.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.lungetest.FullbrightModule;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
public abstract class FullbrightMixin {
    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/dimension/DimensionType;ambientLight()F"))
    private float azeium$ambient(float original) { return FullbrightModule.ambient(original); }
    @Inject(method = "getBrightness(FI)F", at = @At("HEAD"), cancellable = true)
    private static void azeium$brightness(float ambient, int light, CallbackInfoReturnable<Float> result) {
        if (FullbrightModule.ambient(ambient) == 1) result.setReturnValue(1F);
    }
}
