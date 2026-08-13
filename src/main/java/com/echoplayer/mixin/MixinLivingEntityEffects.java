package com.echoplayer.mixin;

import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={LivingEntity.class})
public class MixinLivingEntityEffects {
    @Inject(method={"tickEffects"}, at={@At(value="HEAD")}, cancellable=true)
    private void tickPossessedEffects(CallbackInfo ci) {
        ServerPlayer player;
        EchoServerPlayer echoPlayer;
        Object self = this;
        if (self instanceof EchoServerPlayer && EchoPlayerManager.shouldCancelPossessedEchoEffectTick(echoPlayer = (EchoServerPlayer)self)) {
            ci.cancel();
            return;
        }
        if (self instanceof ServerPlayer && EchoPlayerManager.isPossessing(player = (ServerPlayer)self)) {
            EchoPlayerManager.tickPossessedEffects(player);
            ci.cancel();
        }
    }
}
