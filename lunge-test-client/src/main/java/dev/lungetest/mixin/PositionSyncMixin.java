package dev.lungetest.mixin;

import dev.lungetest.LungeTestClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Excludes server position jumps from the local travel meter; leaves packets untouched. */
@Mixin(ClientPacketListener.class)
public abstract class PositionSyncMixin {
    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void lungeTest$observePositionSync(CallbackInfo info) {
        LungeTestClient.recordPositionSync((ClientPacketListener) (Object) this);
    }
}
