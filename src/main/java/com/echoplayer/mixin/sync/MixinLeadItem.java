package com.echoplayer.mixin.sync;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.LeadItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Makes fence binding search for mobs held by the visible possessed Echo. */
@Mixin(LeadItem.class)
public class MixinLeadItem {
    @ModifyVariable(method = "bindPlayerMobs", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Player echoplayer$useVisibleLeashHolder(Player player) {
        Entity visible = EchoPlayerManager.getVisibleRelationshipOwner(player);
        return visible instanceof Player visiblePlayer ? visiblePlayer : player;
    }
}
