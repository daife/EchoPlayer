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
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerGamePacketListenerImpl.class})
public abstract class MixinServerGamePacketListenerImpl {
    @Shadow
    public ServerPlayer player;

    @Unique
    private boolean echoplayer$shouldBlockInventoryAction() {
        return EchoPlayerManager.isPossessing(this.player) && !EchoPlayerManager.isAuthoritativeControllerForPossession(this.player);
    }

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
            EchoServerPlayer possessed;
            ClientboundSetPassengersPacket passengerPacket = (ClientboundSetPassengersPacket)packet;
            if (EchoPlayerManager.isPossessing(this.player) && (possessed = EchoPlayerManager.getPossessed(this.player)) != null) {
                int[] originalPassengers = passengerPacket.getPassengers();
                boolean containsEcho = false;
                for (int id : originalPassengers) {
                    if (id != possessed.getId()) continue;
                    containsEcho = true;
                    break;
                }
                if (containsEcho) {
                    int[] spoofedPassengers = new int[originalPassengers.length];
                    for (int i = 0; i < originalPassengers.length; ++i) {
                        spoofedPassengers[i] = originalPassengers[i] == possessed.getId() ? this.player.getId() : originalPassengers[i];
                    }
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeVarInt(passengerPacket.getVehicle());
                    buf.writeVarIntArray(spoofedPassengers);
                    ClientboundSetPassengersPacket spoofedPacket = new ClientboundSetPassengersPacket(buf);
                    ci.cancel();
                    this.send(spoofedPacket, listener);
                }
            }
        }
    }

    @Inject(method={"handlePlayerInput"}, at={@At(value="HEAD")}, cancellable=true)
    private void onHandlePlayerInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        EchoServerPlayer possessed;
        if (EchoPlayerManager.isPossessing(this.player) && (possessed = EchoPlayerManager.getPossessed(this.player)) != null) {
            possessed.setPlayerInput(packet.getXxa(), packet.getZza(), packet.isJumping(), packet.isShiftKeyDown());
            ci.cancel();
        }
    }

    @Inject(method={"handleMoveVehicle"}, at={@At(value="HEAD")}, cancellable=true)
    private void onHandleMoveVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        Entity vehicle;
        EchoServerPlayer possessed;
        if (EchoPlayerManager.isPossessing(this.player) && (possessed = EchoPlayerManager.getPossessed(this.player)) != null && (vehicle = possessed.getVehicle()) != null && vehicle.getControllingPassenger() == possessed) {
            double x = packet.getX();
            double y = packet.getY();
            double z = packet.getZ();
            float yRot = packet.getYRot();
            float xRot = packet.getXRot();
            vehicle.absMoveTo(x, y, z, yRot, xRot);
            possessed.absMoveTo(x, y, z, yRot, xRot);
            ci.cancel();
        }
    }
}

