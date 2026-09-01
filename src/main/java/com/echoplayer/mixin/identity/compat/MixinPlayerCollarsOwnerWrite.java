package com.echoplayer.mixin.identity.compat;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Stores the visible, logical player whenever PlayerCollars assigns an owner. */
@Pseudo
@Mixin(targets = "org.jlortiz.playercollars.item.CollarItem", remap = false)
public abstract class MixinPlayerCollarsOwnerWrite {
    @ModifyVariable(
        method = "setOwner(Lnet/minecraft/world/item/ItemStack;Ljava/util/UUID;Ljava/lang/String;)V",
        at = @At("HEAD"),
        argsOnly = true,
        remap = false
    )
    private UUID echoplayer$storeLogicalOwnerId(UUID ownerId) {
        return ownerId != null ? EchoPlayerManager.getLogicalOwnerUUID(ownerId) : null;
    }

    @ModifyVariable(
        method = "setOwner(Lnet/minecraft/world/item/ItemStack;Ljava/util/UUID;Ljava/lang/String;)V",
        at = @At("HEAD"),
        argsOnly = true,
        remap = false
    )
    private String echoplayer$storeLogicalOwnerName(String ownerName) {
        return ownerName != null ? EchoPlayerManager.getLogicalOwnerName(ownerName) : null;
    }
}
