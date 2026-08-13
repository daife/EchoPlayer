package com.echoplayer.mixin;

import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import com.echoplayer.network.EchoConnection;
import com.echoplayer.network.EchoServerGamePacketListenerImpl;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={PlayerList.class})
public class MixinPlayerList {
    private static final ThreadLocal<Boolean> IS_ECHO_PLAYER_RESPAWN = ThreadLocal.withInitial(() -> false);

    @Inject(method={"respawn"}, at={@At(value="HEAD")})
    private void beforeRespawn(ServerPlayer pPlayer, boolean pKeepEverything, CallbackInfoReturnable<ServerPlayer> cir) {
        if (pPlayer instanceof EchoServerPlayer) {
            IS_ECHO_PLAYER_RESPAWN.set(true);
        } else {
            IS_ECHO_PLAYER_RESPAWN.set(false);
        }
    }

    @Redirect(method={"respawn"}, at=@At(value="NEW", target="(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/server/level/ServerPlayer;"))
    private ServerPlayer createPlayerInstance(MinecraftServer server, ServerLevel level, GameProfile profile) {
        if (IS_ECHO_PLAYER_RESPAWN.get().booleanValue()) {
            return new EchoServerPlayer(server, level, profile);
        }
        return new ServerPlayer(server, level, profile);
    }

    @Inject(method={"placeNewPlayer"}, at={@At(value="RETURN")})
    private void afterPlaceNewPlayer(Connection connection, ServerPlayer player, CallbackInfo ci) {
        EchoPlayerManager.restoreCrashBackup(player);
        EchoPlayerManager.hidePossessingControllersFromViewer(player);
        EchoPlayerManager.completePendingEchoReshow(player);
    }

    @Inject(method={"respawn"}, at={@At(value="RETURN")})
    private void afterRespawn(ServerPlayer pPlayer, boolean pKeepEverything, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer newPlayer = (ServerPlayer)cir.getReturnValue();
        if (IS_ECHO_PLAYER_RESPAWN.get().booleanValue() && newPlayer instanceof EchoServerPlayer) {
            EchoServerPlayer echoNewPlayer = (EchoServerPlayer)newPlayer;
            if (pPlayer instanceof EchoServerPlayer) {
                EchoServerPlayer echoOldPlayer = (EchoServerPlayer)pPlayer;
                PacketFlow flow = PacketFlow.SERVERBOUND;
                EchoConnection connection = new EchoConnection(flow);
                EchoServerGamePacketListenerImpl listener = new EchoServerGamePacketListenerImpl(echoNewPlayer.server, connection, echoNewPlayer);
                echoNewPlayer.connection = listener;
                connection.setListener(listener);
            }
        } else {
            EchoPlayerManager.completePendingEchoReshow(newPlayer);
        }
        IS_ECHO_PLAYER_RESPAWN.set(false);
    }

    @Redirect(method={"broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V"}, at=@At(value="INVOKE", target="Lnet/minecraft/network/chat/OutgoingChatMessage;create(Lnet/minecraft/network/chat/PlayerChatMessage;)Lnet/minecraft/network/chat/OutgoingChatMessage;"))
    private OutgoingChatMessage createLogicalOutgoingMessage(PlayerChatMessage message) {
        return EchoPlayerManager.createOutgoingChatMessage(message);
    }
}
