package com.echoplayer.network;

import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import java.util.Set;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public class EchoServerGamePacketListenerImpl
extends ServerGamePacketListenerImpl {
    public EchoServerGamePacketListenerImpl(MinecraftServer pServer, Connection pConnection, ServerPlayer pPlayer) {
        super(pServer, pConnection, pPlayer);
    }

    @Override
    public void tick() {
    }

    @Override
    public void disconnect(Component pTextComponent) {
    }

    private static final Set<Class<? extends Packet<?>>> PROXIED_PACKET_TYPES = Set.of(
        ClientboundContainerSetSlotPacket.class,
        ClientboundContainerSetContentPacket.class,
        ClientboundContainerSetDataPacket.class,
        ClientboundSetCarriedItemPacket.class,
        ClientboundSetHealthPacket.class,
        ClientboundSetExperiencePacket.class,
        ClientboundPlayerAbilitiesPacket.class,
        ClientboundOpenScreenPacket.class,
        ClientboundContainerClosePacket.class,
        ClientboundRespawnPacket.class,
        ClientboundSetEntityDataPacket.class,
        ClientboundRemoveMobEffectPacket.class,
        ClientboundUpdateMobEffectPacket.class,
        ClientboundCooldownPacket.class
    );

    private boolean shouldProxy(Packet<?> packet) {
        if (!PROXIED_PACKET_TYPES.contains(packet.getClass())) {
            return false;
        }
        if (packet instanceof ClientboundEntityEventPacket eventPacket) {
            return eventPacket.getEntity(this.player.level()) == this.player;
        }
        return true;
    }

    @Override
    public void send(Packet<?> pPacket) {
        ServerPlayer realPlayer = EchoPlayerManager.getController(this.player);
        if (realPlayer == null || realPlayer.connection == null) {
            return;
        }
        // A respawn packet produced by the EchoPlayer belongs to its fake listener.
        // The real player's dimension transfer below will produce the matching packet.
        if (pPacket instanceof ClientboundRespawnPacket) {
            return;
        }
        if (pPacket instanceof ClientboundPlayerPositionPacket positionPacket) {
            ServerLevel echoLevel = this.player.serverLevel();
            if (realPlayer.serverLevel() != echoLevel) {
                realPlayer.teleportTo(echoLevel, this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot());
            } else {
                // Generate the position packet on the authenticated listener so that
                // its teleport id and pending position match the client's acknowledgement.
                realPlayer.connection.teleport(this.player.getX(), this.player.getY(), this.player.getZ(), this.player.getYRot(), this.player.getXRot(), positionPacket.getRelativeArguments());
            }
            return;
        }
        if (this.shouldProxy(pPacket)) {
            realPlayer.connection.send(pPacket);
        }
    }

    @Override
    public void onDisconnect(Component reason) {
        if (!(this.player instanceof EchoServerPlayer)) {
            super.onDisconnect(reason);
        }
    }
}
