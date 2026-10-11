package dev.lungetest.mixin;
import dev.lungetest.LungeTestClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Player.class)
public abstract class PlayerCooldownMixin {
    @Inject(method = "cannotAttackWithItem", at = @At("HEAD"), cancellable = true)
    private void lungeTest$localGate(ItemStack stack, int ticks, CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        if (player == Minecraft.getInstance().player && LungeTestClient.skipLocalGate()
                && LungeTestClient.isSpear(stack)) cir.setReturnValue(false);
    }
    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void lungeTest$localCharge(float partialTick, CallbackInfoReturnable<Float> cir) {
        Player player = (Player) (Object) this;
        if (player == Minecraft.getInstance().player && LungeTestClient.skipLocalGate()
                && LungeTestClient.isSpear(player.getMainHandItem()))
            cir.setReturnValue(1.0F);
    }
}
