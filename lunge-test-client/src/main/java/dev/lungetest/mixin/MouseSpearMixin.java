package dev.lungetest.mixin;

import dev.lungetest.LungeTestScreen;
import dev.lungetest.SpearQuickLungeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseSpearMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void azeium$spearMouse(long window, MouseButtonInfo button, int action, CallbackInfo info) {
        Minecraft client = Minecraft.getInstance();
        if (window != client.getWindow().handle()) return;
        if (action == GLFW.GLFW_PRESS && client.screen instanceof LungeTestScreen screen
                && screen.handleCapturedMouse(button.button())) {
            info.cancel();
        } else if (SpearQuickLungeModule.handleMouse(client, action, button.button())) info.cancel();
    }
}
