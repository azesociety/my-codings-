package dev.lungetest.mixin;

import dev.lungetest.CombatAssistModule;
import dev.lungetest.HotbarSyncAccess;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Handles manual-hit mace selection and mob combos without recursively reacting to combo attacks. */
@Mixin(MultiPlayerGameMode.class)
public abstract class CombatAttackMixin implements HotbarSyncAccess {
    @Invoker("ensureHasSentCarriedItem") public abstract void azeium$syncCarriedItem();
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void azeium$beginCombo(Player player, Entity target, CallbackInfo ci) {
        if (CombatAssistModule.beginManualAttack(Minecraft.getInstance(), target)) ci.cancel();
    }
    @Inject(method = "attack", at = @At("TAIL"))
    private void azeium$prepareMace(Player player, Entity target, CallbackInfo ci) {
        CombatAssistModule.onManualAttack(Minecraft.getInstance(), target);
    }
}
