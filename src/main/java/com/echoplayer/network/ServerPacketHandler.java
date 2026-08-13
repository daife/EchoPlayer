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

}
