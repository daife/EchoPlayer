package com.echoplayer.mixin.sync;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes vanilla's right-click-to-unleash logic possession-aware without
 * redirecting a fragile internal invocation inside Mob.
 */
@Mixin(Mob.class)
public abstract class MixinMobLeashInteraction {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void echoplayer$unleashFromVisibleHolder(Player player, InteractionHand hand,
                                                      CallbackInfoReturnable<InteractionResult> cir) {
        Entity visibleHolder = EchoPlayerManager.getVisibleRelationshipOwner(player);
        if (visibleHolder == player) {
            return;
        }
        Mob mob = (Mob)(Object)this;
        if (!mob.isLeashed() || mob.getLeashHolder() != visibleHolder) {
            return;
        }

        // Mirrors vanilla's leash-detach branch. This path only runs server-side
        // because possession mappings exist only for ServerPlayer controllers.
        mob.dropLeash(true, !player.getAbilities().instabuild);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
