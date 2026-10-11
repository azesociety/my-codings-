package dev.lungetest.mixin;

import dev.lungetest.AutoClutchModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Plans bridge catches before vanilla moves the player's feet below the bridge edge. */
@Mixin(LocalPlayer.class)
public abstract class ClutchMoveMixin {
    @Inject(method = "move", at = @At("HEAD"))
    private void azeium$beforeMovement(MoverType type, Vec3 movement, CallbackInfo info) {
        Minecraft client = Minecraft.getInstance();
        if (type == MoverType.SELF && client.player == (Object) this)
            AutoClutchModule.beforeMove(client, movement);
    }
}
