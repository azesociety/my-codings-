package dev.lungetest.mixin;

import dev.lungetest.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class ZoomMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void azeium$zoomView(Camera camera, float delta, boolean worldView,
                                  CallbackInfoReturnable<Float> info) {
        info.setReturnValue(ZoomModule.apply(info.getReturnValue(), worldView));
    }
}
