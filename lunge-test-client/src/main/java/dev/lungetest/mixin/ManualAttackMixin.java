package dev.lungetest.mixin;

import dev.lungetest.LungeTestClient;
import dev.lungetest.CombatAssistModule;
import dev.lungetest.SpearQuickLungeModule;
import dev.lungetest.SpearAttackAccess;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prepares a falling mace before held-item attack checks and records ordinary manual attacks. */
@Mixin(Minecraft.class)
public abstract class ManualAttackMixin implements SpearAttackAccess, dev.lungetest.ClickActionAccess {
    @Invoker("startAttack") public abstract boolean azeium$startAttack();
    @Invoker("startUseItem") public abstract void azeium$startUseItem();
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void azeium$prepareFallingMace(CallbackInfoReturnable<Boolean> info) {
        Minecraft client = (Minecraft) (Object) this;
        if (!SpearQuickLungeModule.dispatching() && client.hitResult instanceof EntityHitResult hit)
            CombatAssistModule.prepareManualAttack(client, hit.getEntity());
    }

    @Inject(method = "startAttack", at = @At("RETURN"))
    private void lungeTest$recordManualAttack(CallbackInfoReturnable<Boolean> info) {
        CombatAssistModule.finishManualAttack((Minecraft) (Object) this);
        LungeTestClient.recordManualAttack((Minecraft) (Object) this, info.getReturnValue());
    }
}
