package dev.lungetest.mixin;

import dev.lungetest.TellyPilotModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Changes this local player's next input without pressing or releasing any global key bindings. */
@Mixin(KeyboardInput.class)
public abstract class TellyInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void azeium$pilotInput(CallbackInfo ci) {
        TellyPilotModule.InputOverride override = TellyPilotModule.applyInput(
                Minecraft.getInstance(), this, this.moveVector);
        var menu = dev.lungetest.MenuMovement.read(Minecraft.getInstance());
        if (menu != null) override = menu;
        if (override != null) {
            this.keyPresses = override.keyPresses();
            this.moveVector = override.moveVector();
        }
    }
}
