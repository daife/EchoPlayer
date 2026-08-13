package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
@Implements(@Interface(iface=EntityGetter.class, prefix="echoplayer$entityGetter$", remap=Interface.Remap.ALL))
public abstract class MixinServerLevel {
    @Redirect(method={"updateSleepingPlayerList"}, at=@At(value="INVOKE", target="Ljava/util/List;isEmpty()Z"))
    private boolean echoplayer$hasNoLogicalSleepPlayers(List<ServerPlayer> players) {
        return EchoPlayerManager.projectSleepStatusPlayers((ServerLevel)(Object)this, players).isEmpty();
    }

    @ModifyArg(method={"updateSleepingPlayerList"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/players/SleepStatus;update(Ljava/util/List;)Z"), index=0)
    private List<ServerPlayer> echoplayer$projectSleepStatusUpdatePlayers(List<ServerPlayer> players) {
        return EchoPlayerManager.projectSleepStatusPlayers((ServerLevel)(Object)this, players);
    }

    @ModifyArg(method={"tick"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/players/SleepStatus;areEnoughDeepSleeping(ILjava/util/List;)Z"), index=1)
    private List<ServerPlayer> echoplayer$projectDeepSleepingPlayers(List<ServerPlayer> players) {
        return EchoPlayerManager.projectSleepStatusPlayers((ServerLevel)(Object)this, players);
    }

    @Inject(method={"wakeUpAllPlayers"}, at={@At(value="TAIL")})
    private void echoplayer$wakeSleepingShells(CallbackInfo ci) {
        EchoPlayerManager.wakeSleepingShells((ServerLevel)(Object)this);
    }

    public Player echoplayer$entityGetter$getPlayerByUUID(UUID playerId) {
        EntityGetter level = (EntityGetter)(Object)this;
        for (Player player : level.players()) {
            if (playerId.equals(player.getUUID())) {
                return player;
            }
        }

        ServerPlayer avatar = EchoPlayerManager.getIdentityAvatar(level, playerId);
        return avatar;
    }
}
