package com.echoplayer.mixin.sync;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lets vanilla's "interact again to unleash" identity check see the Echo holder. */
@Mixin(Mob.class)
public class MixinMobLeashInteraction {
    @Redirect(
        method = "checkAndHandleImportantInteractions",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getLeashHolder()Lnet/minecraft/world/entity/Entity;")
    )
    private Entity echoplayer$matchVisibleLeashHolder(Mob mob, Player player, InteractionHand hand) {
        Entity holder = mob.getLeashHolder();
        Entity visible = EchoPlayerManager.getVisibleRelationshipOwner(player);
        return holder == visible ? player : holder;
    }
}
