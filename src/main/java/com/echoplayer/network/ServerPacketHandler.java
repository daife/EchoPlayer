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

    public static void handleControlInputPacket(ServerPlayer sender, FriendlyByteBuf buf) {
        if (sender == null || sender.getServer() == null) {
            return;
        }
        long sequence = buf.readVarLong();
        short inputMask = buf.readUnsignedByte();
        EchoPlayerManager.markControllerInput(sender, sequence, inputMask);
    }
}

