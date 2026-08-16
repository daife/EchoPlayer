package com.echoplayer.mixin.client;

import com.echoplayer.client.ClientPossessionData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Entity.class})
public class MixinEntityClient {
    @Inject(method={"isInvisible"}, at={@At(value="HEAD")}, cancellable=true)
    private void overrideInvisibility(CallbackInfoReturnable<Boolean> cir) {
        if (ClientPossessionData.possessedUUID != null && (Object)this == Minecraft.getInstance().player) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"isPickable"}, at={@At(value="HEAD")}, cancellable=true)
    private void onIsPickable(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)((Object)this);
        if ((ClientPossessionData.shellEntityId != -1 && self.getId() == ClientPossessionData.shellEntityId)
            || ClientPossessionData.isPossessedEcho(self)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method={"push"}, at={@At(value="HEAD")}, cancellable=true)
    private void echoplayer$disableLocalPossessionPush(Entity entity, CallbackInfo ci) {
        if (ClientPossessionData.shouldDisablePossessionPush((Entity)((Object)this), entity)) {
            ci.cancel();
        }
    }
}
