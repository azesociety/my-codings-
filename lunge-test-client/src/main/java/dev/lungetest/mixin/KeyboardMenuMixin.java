package dev.lungetest.mixin;

import dev.lungetest.LungeTestClient;
import dev.lungetest.LungeTestScreen;
import dev.lungetest.AzeiumKeybinds;
import dev.lungetest.ClientSettingsScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMenuMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void spearControls$screenMenu(long window, int action, KeyEvent key, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (window != client.getWindow().handle()) return;
        if (action == GLFW.GLFW_PRESS && client.screen instanceof ClientSettingsScreen settings
                && settings.capturingKeybind() && settings.handleCapturedKey(key)) {
            ci.cancel();
            return;
        }
        if (action == GLFW.GLFW_PRESS && client.screen instanceof LungeTestScreen screen
                && screen.capturingKeybind() && screen.handleCapturedKey(key)) {
            ci.cancel();
            return;
        }
        if (dev.lungetest.MenuMovement.movementKey(client, key)) {
            ci.cancel(); return;
        }
        if (AzeiumKeybinds.handleModuleKey(client, action, key)) {
            ci.cancel();
            return;
        }
        if (action != GLFW.GLFW_PRESS || !LungeTestClient.menuMatches(key)) return;
        // Vanilla does not forward ordinary keybindings while these screens are open.
        if (client.screen instanceof TitleScreen || client.screen instanceof LungeTestScreen
                || client.screen instanceof ClientSettingsScreen) {
            client.setScreen(client.screen instanceof TitleScreen ? new LungeTestScreen() : null);
            ci.cancel();
        }
    }
}
