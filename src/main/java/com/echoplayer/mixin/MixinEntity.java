package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method={"isPushable"}, at={@At(value="HEAD")}, cancellable=true)
    private void echoplayer$disableControllerPushability(CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof ServerPlayer && EchoPlayerManager.isPossessing((ServerPlayer)self)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"push(Lnet/minecraft/world/entity/Entity;)V"}, at={@At(value="HEAD")}, cancellable=true)
    private void echoplayer$disableControllerPushing(Entity other, CallbackInfo ci) {
        Entity self = (Entity)((Object)this);
        if (EchoPlayerManager.isControllerObserver(self) || EchoPlayerManager.isControllerObserver(other)) {
            ci.cancel();
        }
    }
}
