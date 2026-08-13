package com.echoplayer.mixin;

import com.mojang.datafixers.util.Either;
import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerPlayer.class})
public class MixinServerPlayer {
    @Inject(method={"startSleepInBed"}, at={@At(value="HEAD")}, cancellable=true)
    private void onStartSleepInBed(BlockPos bedPos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir) {
        ServerPlayer realPlayer = (ServerPlayer)((Object)this);
        EchoServerPlayer echoPlayer = EchoPlayerManager.getSleepTarget(realPlayer);
        if (echoPlayer == null) {
            return;
        }
        if (!EchoPlayerManager.isAuthoritativeControllerForPossession(realPlayer)) {
            cir.setReturnValue(Either.left(Player.BedSleepingProblem.OTHER_PROBLEM));
            return;
        }
        Either<Player.BedSleepingProblem, Unit> result = echoPlayer.startSleepInBed(bedPos);
        result.ifRight(unit -> EchoPlayerManager.mirrorPossessedSleep(realPlayer, echoPlayer));
        cir.setReturnValue(result);
    }

    @Inject(method={"stopSleepInBed"}, at={@At(value="HEAD")}, cancellable=true)
    private void onStopSleepInBed(boolean wakeImmediately, boolean updateLevel, CallbackInfo ci) {
        ServerPlayer realPlayer = (ServerPlayer)((Object)this);
        if (EchoPlayerManager.stopPossessedSleep(realPlayer, wakeImmediately, updateLevel)) {
            ci.cancel();
        }
    }

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

    @Inject(method={"isSpectator"}, at={@At(value="HEAD")}, cancellable=true)
    private void echoplayer$makeControllerBodyNonInteractive(CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer player = (ServerPlayer)((Object)this);
        if (EchoPlayerManager.isPossessing(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method={"startRiding(Lnet/minecraft/world/entity/Entity;Z)Z"}, at={@At(value="HEAD")}, cancellable=true)
    private void onStartRiding(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
        EchoServerPlayer possessed;
        ServerPlayer player = (ServerPlayer)((Object)this);
        if (EchoPlayerManager.isPossessing(player) && (possessed = EchoPlayerManager.getPossessed(player)) != null) {
            cir.setReturnValue(possessed.startRiding(vehicle, force));
        }
    }
}
