package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Entity.class})
public class MixinEntity {
    @Inject(method={"isPickable"}, at={@At(value="HEAD")}, cancellable=true)
    private void onIsPickable(CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer realPlayer;
        Object self = this;
        if (self instanceof ServerPlayer && EchoPlayerManager.isPossessing(realPlayer = (ServerPlayer)self)) {
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
