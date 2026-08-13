package com.echoplayer.mixin;

import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import com.echoplayer.mixin.ExperienceOrbInvoker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ExperienceOrb.class})
public class MixinExperienceOrb {
    @Unique
    private EchoServerPlayer echoplayer$mutatedEchoPlayer;

    @Inject(method={"playerTouch"}, at={@At(value="HEAD")}, cancellable=true)
    private void echoplayer$ignoreControllerObserver(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer && EchoPlayerManager.isPossessing((ServerPlayer)player)) {
            ci.cancel();
        }
    }

    @Unique
    private EchoServerPlayer echoplayer$getPossessedXpTarget(Player player) {
        EchoServerPlayer echoPlayer;
        ServerPlayer serverPlayer;
        if (player instanceof ServerPlayer && EchoPlayerManager.isPossessing(serverPlayer = (ServerPlayer)player) && (echoPlayer = EchoPlayerManager.getPossessed(serverPlayer)) != null && !echoPlayer.isRemoved() && !echoPlayer.isDeadOrDying()) {
            return echoPlayer;
        }
        return null;
    }

    @Redirect(method={"playerTouch"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/ExperienceOrb;repairPlayerItems(Lnet/minecraft/world/entity/player/Player;I)I"))
    private int echoplayer$redirectMendingRepair(ExperienceOrb orb, Player player, int value) {
        EchoServerPlayer echoPlayer = this.echoplayer$getPossessedXpTarget(player);
        if (echoPlayer != null) {
            this.echoplayer$mutatedEchoPlayer = echoPlayer;
            return ((ExperienceOrbInvoker)((Object)orb)).echoplayer$repairPlayerItems(echoPlayer, value);
        }
        return ((ExperienceOrbInvoker)((Object)orb)).echoplayer$repairPlayerItems(player, value);
    }

    @Redirect(method={"playerTouch"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/player/Player;giveExperiencePoints(I)V"))
    private void echoplayer$redirectGiveExperience(Player player, int amount) {
        EchoServerPlayer echoPlayer = this.echoplayer$getPossessedXpTarget(player);
        if (echoPlayer != null) {
            this.echoplayer$mutatedEchoPlayer = echoPlayer;
            echoPlayer.giveExperiencePoints(amount);
            return;
        }
        player.giveExperiencePoints(amount);
    }

    @Inject(method={"playerTouch"}, at={@At(value="RETURN")})
    private void echoplayer$afterExperienceTouch(Player player, CallbackInfo ci) {
        if (this.echoplayer$mutatedEchoPlayer != null) {
            EchoPlayerManager.syncPossessedEchoSharedState(this.echoplayer$mutatedEchoPlayer);
            this.echoplayer$mutatedEchoPlayer = null;
        }
    }
}
