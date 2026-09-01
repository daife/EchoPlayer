package com.echoplayer.mixin.identity.compat;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps collar ownership stable while accepting either side of an active
 * possession. The UUID stored by PlayerCollars is deliberately not rewritten
 * when the Curios inventory moves between the controller and its EchoPlayer.
 */
@Pseudo
@Mixin(targets = "org.jlortiz.playercollars.PlayerCollarsMod", remap = false)
public abstract class MixinPlayerCollarsOwnerLookup {
    @Redirect(
        method = "filterStacksByOwner(Ltop/theillusivec4/curios/api/type/inventory/IDynamicStackHandler;Ljava/util/UUID;)Lnet/minecraft/world/item/ItemStack;",
        at = @At(value = "INVOKE", target = "Ljava/util/UUID;equals(Ljava/lang/Object;)Z", remap = false),
        remap = false
    )
    private static boolean echoplayer$acceptPossessionIdentity(UUID storedOwner, Object requestedOwner) {
        return EchoPlayerManager.arePossessionIdentitiesEquivalent(storedOwner, requestedOwner);
    }
}
