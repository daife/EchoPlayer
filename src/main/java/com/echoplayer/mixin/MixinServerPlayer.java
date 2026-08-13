package com.echoplayer.mixin;

import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ServerPlayer.class})
public class MixinServerPlayer {
    @Inject(method={"getPermissionLevel"}, at={@At(value="HEAD")}, cancellable=true)
    private void onGetPermissionLevel(CallbackInfoReturnable<Integer> cir) {
        int permissionLevel;
        EchoServerPlayer echoPlayer;
        ServerPlayer player = (ServerPlayer)((Object)this);
        if (player instanceof EchoServerPlayer && EchoPlayerManager.isPossessed(echoPlayer = (EchoServerPlayer)player) && (permissionLevel = EchoPlayerManager.getInheritedPermissionLevel(echoPlayer)) >= 0) {
            cir.setReturnValue(permissionLevel);
        }
    }

    @Inject(method={"getAdvancements"}, at={@At(value="HEAD")}, cancellable=true)
    private void onGetAdvancements(CallbackInfoReturnable<PlayerAdvancements> cir) {
        EchoServerPlayer echoPlayer;
        ServerPlayer player = (ServerPlayer)((Object)this);
        if (EchoPlayerManager.isPossessing(player) && (echoPlayer = EchoPlayerManager.getPossessed(player)) != null) {
            cir.setReturnValue(echoPlayer.getAdvancements());
        }
    }

    @Inject(method={"getStats"}, at={@At(value="HEAD")}, cancellable=true)
    private void onGetStats(CallbackInfoReturnable<ServerStatsCounter> cir) {
        EchoServerPlayer echoPlayer;
        ServerPlayer player = (ServerPlayer)((Object)this);
        if (EchoPlayerManager.isPossessing(player) && (echoPlayer = EchoPlayerManager.getPossessed(player)) != null) {
            cir.setReturnValue(echoPlayer.getStats());
        }
    }

}
