package com.echoplayer.mixin.identity.compat;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies possession identity to owner-sensitive collar tick behaviour. */
@Pseudo
@Mixin(targets = "org.jlortiz.playercollars.item.CollarItem", remap = false)
public abstract class MixinPlayerCollarsCollarItem {
    @Redirect(
        method = "lambda$curioTick$*",
        at = @At(value = "INVOKE", target = "Ljava/util/UUID;equals(Ljava/lang/Object;)Z", remap = false),
        require = 0,
        remap = false
    )
    private boolean echoplayer$acceptPossessionIdentity(UUID storedOwner, Object wearerId) {
        return EchoPlayerManager.arePossessionIdentitiesEquivalent(storedOwner, wearerId);
    }
}
