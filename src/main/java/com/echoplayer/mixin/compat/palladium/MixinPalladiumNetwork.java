package com.echoplayer.mixin.compat.palladium;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.threetag.palladiumcore.network.MessageS2C;
import net.threetag.palladiumcore.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Deliver addon screens and effects to the connection controlling the addressed character. */
@Pseudo
@Mixin(targets = "net.threetag.palladiumcore.network.forge.NetworkManagerImpl", remap = false)
public abstract class MixinPalladiumNetwork {
    @ModifyVariable(method = "sendToPlayer", at = @At("HEAD"), argsOnly = true)
    private ServerPlayer echoplayer$recipient(ServerPlayer player) {
        return EchoPlayerManager.getAuthenticatedPlayer(player);
    }

    @Inject(method = "sendToTrackingAndSelf", at = @At("TAIL"))
    private void echoplayer$self(ServerPlayer player, MessageS2C message, CallbackInfo ci) {
        ServerPlayer recipient = EchoPlayerManager.getAuthenticatedPlayer(player);
        if (recipient != player) ((NetworkManager) (Object) this).sendToPlayer(recipient, message);
    }
}
