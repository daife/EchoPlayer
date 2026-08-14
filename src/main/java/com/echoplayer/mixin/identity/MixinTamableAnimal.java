package com.echoplayer.mixin.identity;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Projects possession identity into vanilla tame ownership.  Follow-owner AI
 * and interaction checks consume these methods directly, with no unified Forge
 * event that can substitute the logical owner.
 */
@Mixin(TamableAnimal.class)
public abstract class MixinTamableAnimal {
    @Shadow
    public abstract UUID getOwnerUUID();

    @Redirect(method={"tame"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/player/Player;getUUID()Ljava/util/UUID;"))
    private UUID echoplayer$storeLogicalOwner(Player player) {
        return EchoPlayerManager.getLogicalOwnerUUID(player);
    }

    @Inject(method={"isOwnedBy"}, at={@At(value="HEAD")}, cancellable=true)
    private void echoplayer$recognizeLogicalOwner(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ServerPlayer) {
            ServerPlayer logicalPlayer = EchoPlayerManager.getLogicalPlayer((ServerPlayer)entity);
            UUID ownerId = this.getOwnerUUID();
            if (logicalPlayer != entity && ownerId != null && ownerId.equals(logicalPlayer.getUUID())) {
                cir.setReturnValue(true);
            }
        }
    }
}
