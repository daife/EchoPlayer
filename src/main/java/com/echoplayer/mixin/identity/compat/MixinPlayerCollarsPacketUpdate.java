package com.echoplayer.mixin.identity.compat;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Stores the visible, logical player as a newly assigned collar owner. */
@Pseudo
@Mixin(targets = "org.jlortiz.playercollars.PacketUpdateCollar", remap = false)
public abstract class MixinPlayerCollarsPacketUpdate {
    @ModifyArg(
        method = "lambda$handle$0(Ljava/util/function/Supplier;)V",
        at = @At(value = "INVOKE", target = "Lorg/jlortiz/playercollars/item/CollarItem;setOwner(Lnet/minecraft/world/item/ItemStack;Ljava/util/UUID;Ljava/lang/String;)V", remap = false),
        index = 1,
        remap = false
    )
    private UUID echoplayer$storeLogicalOwnerId(UUID ownerId) {
        return ownerId != null ? EchoPlayerManager.getLogicalOwnerUUID(ownerId) : null;
    }

    @ModifyArg(
        method = "lambda$handle$0(Ljava/util/function/Supplier;)V",
        at = @At(value = "INVOKE", target = "Lorg/jlortiz/playercollars/item/CollarItem;setOwner(Lnet/minecraft/world/item/ItemStack;Ljava/util/UUID;Ljava/lang/String;)V", remap = false),
        index = 2,
        remap = false
    )
    private String echoplayer$storeLogicalOwnerName(String ownerName) {
        return ownerName != null ? EchoPlayerManager.getLogicalOwnerName(ownerName) : null;
    }
}
