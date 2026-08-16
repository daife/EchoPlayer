package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={LivingEntity.class})
public class MixinLivingEntity {
    @Inject(method={"isPickable"}, at={@At(value="HEAD")}, cancellable=true)
    private void onIsPickable(CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof ServerPlayer realPlayer && EchoPlayerManager.isPossessing(realPlayer)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"push"}, at={@At(value="HEAD")}, cancellable=true)
    private void echoplayer$disableControllerEchoPush(Entity entity, CallbackInfo ci) {
        if (EchoPlayerManager.shouldDisableCollision((Entity)((Object)this), entity)) {
            ci.cancel();
        }
    }
}
