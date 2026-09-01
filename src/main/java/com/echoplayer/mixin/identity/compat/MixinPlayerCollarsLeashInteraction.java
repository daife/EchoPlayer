package com.echoplayer.mixin.identity.compat;

import com.echoplayer.compat.PlayerCollarsCompat;
import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes PlayerCollars 1.2.6's direct holder == player detach check possession-aware. */
@Mixin(Player.class)
public abstract class MixinPlayerCollarsLeashInteraction {
    @Inject(method = "interactOn", at = @At("HEAD"))
    private void echoplayer$preparePlayerCollarsDetach(Entity target, InteractionHand hand,
                                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (!((Object)this instanceof ServerPlayer controller)) {
            return;
        }
        EchoServerPlayer echoPlayer = EchoPlayerManager.getPossessed(controller);
        if (echoPlayer == null || echoPlayer.isRemoved() || echoPlayer.isDeadOrDying()) {
            return;
        }
        if (target instanceof Player targetPlayer) {
            PlayerCollarsCompat.prepareDetachInteraction(targetPlayer, controller, echoPlayer);
        }
    }
}
