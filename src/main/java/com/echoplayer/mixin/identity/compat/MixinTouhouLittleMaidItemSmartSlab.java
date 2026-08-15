package com.echoplayer.mixin.identity.compat;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets a possessed controller use Soul Spells owned by either side of the
 * active possession without changing the ownership stored in the item.
 */
@Pseudo
@Mixin(targets={"com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab"}, remap=false)
public abstract class MixinTouhouLittleMaidItemSmartSlab {
    @Redirect(
        method={
            "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            "m_6225_(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"
        },
        at=@At(value="INVOKE", target="Ljava/util/UUID;equals(Ljava/lang/Object;)Z", remap=false),
        remap=false
    )
    private boolean echoplayer$acceptPossessionIdentityForInitialSlab(UUID playerId, Object ownerId) {
        return EchoPlayerManager.arePossessionIdentitiesEquivalent(playerId, ownerId);
    }

    @Redirect(
        method={"spawnFromStore(Lnet/minecraft/world/item/context/UseOnContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Lnet/minecraft/world/InteractionResult;"},
        at=@At(value="INVOKE", target="Ljava/util/UUID;equals(Ljava/lang/Object;)Z", remap=false),
        remap=false
    )
    private boolean echoplayer$acceptPossessionIdentityForStoredMaid(UUID playerId, Object ownerId) {
        return EchoPlayerManager.arePossessionIdentitiesEquivalent(playerId, ownerId);
    }
}
