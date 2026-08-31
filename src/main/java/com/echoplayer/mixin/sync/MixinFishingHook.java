package com.echoplayer.mixin.sync;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bridges a possessed controller's fishing field to the visible EchoPlayer owner. */
@Mixin(FishingHook.class)
public class MixinFishingHook {
    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At("RETURN"))
    private void echoplayer$linkVisibleOwner(Player player, Level level, int luck, int lureSpeed, CallbackInfo ci) {
        EchoPlayerManager.linkPossessedFishingHook((FishingHook)(Object)this, player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void echoplayer$clearControllerMirror(Entity.RemovalReason reason, CallbackInfo ci) {
        EchoPlayerManager.unlinkPossessedFishingHook((FishingHook)(Object)this);
    }
}
