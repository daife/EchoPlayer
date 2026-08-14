package com.echoplayer.network;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class ServerPacketHandler {
    public static void handleUnpossessPacket(ServerPlayer sender, FriendlyByteBuf buf) {
        if (sender == null || sender.getServer() == null) {
            return;
        }
        sender.getServer().execute(() -> EchoPlayerManager.revertPossession(sender));
    }

    public static void handleViewRotationPacket(ServerPlayer sender, FriendlyByteBuf buf) {
        if (sender == null || sender.getServer() == null || buf.readableBytes() < Float.BYTES * 4) {
            return;
        }
        float yRot = buf.readFloat();
        float xRot = buf.readFloat();
        float yHeadRot = buf.readFloat();
        float yBodyRot = buf.readFloat();
        sender.getServer().execute(() -> EchoPlayerManager.updatePossessedClientView(sender, yRot, xRot, yHeadRot, yBodyRot));
    }

}
