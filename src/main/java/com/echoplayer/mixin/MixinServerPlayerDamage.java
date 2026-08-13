package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ServerPlayer.class})
public class MixinServerPlayerDamage {
    @Inject(method={"hurt"}, at={@At(value="HEAD")}, cancellable=true)
    private void onHurt(DamageSource pSource, float pAmount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer player = (ServerPlayer)((Object)this);
        if (EchoPlayerManager.isPossessing(player)) {
            cir.setReturnValue(EchoPlayerManager.handlePossessedDamage(player, pSource, pAmount));
        }
    }
}
