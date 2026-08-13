package com.echoplayer.mixin;

import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerGamePacketListenerImpl.class})
public abstract class MixinServerGamePacketListenerImpl {
    @Shadow
    public ServerPlayer player;

    /*
     * Enabled aggressive block sorting
     */
    @Inject(method={"onDisconnect"}, at={@At(value="HEAD")})
    private void beforeDisconnect(Component pReason, CallbackInfo ci) {
        ServerPlayer serverPlayer = this.player;
        if (serverPlayer instanceof EchoServerPlayer) {
            EchoServerPlayer echoPlayer = (EchoServerPlayer)serverPlayer;
            if (echoPlayer.linkedRealPlayer == null) {
                EchoPlayerManager.revertAllPossessions(echoPlayer);
                return;
            }
        }
        if (!EchoPlayerManager.isPossessing(this.player)) return;
        EchoPlayerManager.revertPossession(this.player);
    }

    @Redirect(method={"parseCommand"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/level/ServerPlayer;createCommandSourceStack()Lnet/minecraft/commands/CommandSourceStack;"))
    private CommandSourceStack redirectCommandSource(ServerPlayer player) {
        return EchoPlayerManager.createPossessedCommandSource(player);
    }

    @Inject(method={"performChatCommand"}, at={@At(value="RETURN")})
    private void afterPerformChatCommand(ServerboundChatCommandPacket packet, LastSeenMessages lastSeenMessages, CallbackInfo ci) {
        EchoPlayerManager.syncPossessedAfterCommand(this.player);
    }

    @ModifyArg(method={"broadcastChatMessage"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/players/PlayerList;broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V"), index=1)
    private ServerPlayer redirectChatSender(ServerPlayer sender) {
        EchoServerPlayer echoPlayer = EchoPlayerManager.getPossessed(this.player);
        return echoPlayer != null && !echoPlayer.isRemoved() && !echoPlayer.isDeadOrDying() ? echoPlayer : sender;
    }

    @ModifyArg(method={"broadcastChatMessage"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/players/PlayerList;broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V"), index=2)
    private ChatType.Bound redirectChatType(ChatType.Bound bound) {
        EchoServerPlayer echoPlayer = EchoPlayerManager.getPossessed(this.player);
        return echoPlayer != null && !echoPlayer.isRemoved() && !echoPlayer.isDeadOrDying() ? ChatType.bind(ChatType.CHAT, echoPlayer) : bound;
    }

    @Shadow
    public abstract void send(Packet<?> var1, PacketSendListener var2);

    @Inject(method={"send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V"}, at={@At(value="HEAD")}, cancellable=true)
    private void onSendPacket(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (packet instanceof ClientboundSetPassengersPacket) {
            ClientboundSetPassengersPacket passengerPacket = (ClientboundSetPassengersPacket)packet;
            int[] projectedPassengers = EchoPlayerManager.projectPassengerIdsForViewer(this.player, passengerPacket.getPassengers());
            if (projectedPassengers != null) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                buf.writeVarInt(passengerPacket.getVehicle());
                buf.writeVarIntArray(projectedPassengers);
                ClientboundSetPassengersPacket projectedPacket = new ClientboundSetPassengersPacket(buf);
                ci.cancel();
                this.send(projectedPacket, listener);
            }
        }
    }
}

