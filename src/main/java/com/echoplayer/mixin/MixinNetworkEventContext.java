package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NetworkEvent.Context.class, remap = false)
public class MixinNetworkEventContext {
    @Inject(method = "getSender", at = @At("RETURN"), cancellable = true)
    private void echoplayer$routeModPacketSender(CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer sender = cir.getReturnValue();
        if (sender != null) {
            cir.setReturnValue(EchoPlayerManager.getGameplayPlayer(sender));
        }
    }
}
