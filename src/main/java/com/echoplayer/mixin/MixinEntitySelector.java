package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={EntitySelector.class})
public class MixinEntitySelector {
    @Redirect(method={"findPlayers"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/players/PlayerList;getPlayerByName(Ljava/lang/String;)Lnet/minecraft/server/level/ServerPlayer;"))
    private ServerPlayer projectNamedPlayer(PlayerList playerList, String name) {
        ServerPlayer player = playerList.getPlayerByName(name);
        return player != null ? EchoPlayerManager.projectSelectorPlayer(player) : null;
    }

    @Redirect(method={"findPlayers"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/players/PlayerList;getPlayer(Ljava/util/UUID;)Lnet/minecraft/server/level/ServerPlayer;"))
    private ServerPlayer projectUuidPlayer(PlayerList playerList, UUID uuid) {
        ServerPlayer player = playerList.getPlayer(uuid);
        return player != null ? EchoPlayerManager.projectSelectorPlayer(player) : null;
    }

    @Redirect(method={"findPlayers"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
    private List<ServerPlayer> projectGlobalPlayers(PlayerList playerList) {
        return EchoPlayerManager.projectSelectorPlayers(playerList.getPlayers());
    }

    @Redirect(method={"findPlayers"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/level/ServerLevel;getPlayers(Ljava/util/function/Predicate;I)Ljava/util/List;"))
    private List<ServerPlayer> projectLevelPlayers(ServerLevel level, Predicate<? super ServerPlayer> predicate, int limit) {
        return EchoPlayerManager.getProjectedLevelPlayers(level, predicate, limit);
    }

    @Inject(method={"findEntities"}, at={@At(value="RETURN")}, cancellable=true)
    private void filterControllerEntities(CommandSourceStack source, CallbackInfoReturnable<List<? extends Entity>> cir) {
        cir.setReturnValue(EchoPlayerManager.filterLogicalSelectorEntities((List)cir.getReturnValue()));
    }
}

