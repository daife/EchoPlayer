package com.echoplayer.mixin.client;

import com.echoplayer.client.ClientPossessionData;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets the collar screen edit ownership assigned to the possessed EchoPlayer. */
@Pseudo
@Mixin(targets = "org.jlortiz.playercollars.client.CollarDyeScreen", remap = false)
public abstract class MixinPlayerCollarsDyeScreen {
    @Redirect(
        method = "init()V",
        at = @At(value = "INVOKE", target = "Ljava/util/UUID;equals(Ljava/lang/Object;)Z", remap = false),
        remap = false
    )
    private boolean echoplayer$acceptPossessionIdentity(UUID ownerId, Object playerId) {
        return ClientPossessionData.arePossessionIdentitiesEquivalent(ownerId, playerId);
    }
}
