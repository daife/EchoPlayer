package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import javax.annotation.Nullable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MixinLivingEntityEffectMutations {
    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("TAIL"))
    private void syncAddedEffect(MobEffectInstance effect, @Nullable Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            EchoPlayerManager.syncPossessedEffectMutation((LivingEntity)(Object)this);
        }
    }

    @Inject(method = "removeEffect(Lnet/minecraft/world/effect/MobEffect;)Z", at = @At("TAIL"))
    private void syncRemovedEffect(MobEffect effect, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            EchoPlayerManager.syncPossessedEffectMutation((LivingEntity)(Object)this);
        }
    }

    @Inject(method = "removeAllEffects()Z", at = @At("TAIL"))
    private void syncRemovedAllEffects(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            EchoPlayerManager.syncPossessedEffectMutation((LivingEntity)(Object)this);
        }
    }
}
