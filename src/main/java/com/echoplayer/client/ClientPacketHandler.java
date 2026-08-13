package com.echoplayer.client;

import com.echoplayer.client.ClientPossessionData;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

public class ClientPacketHandler {
    public static void handlePossessPacket(FriendlyByteBuf buf) {
        UUID echoUUID = buf.readUUID();
        int shellId = buf.readInt();
        ClientPossessionData.beginPossession(echoUUID, shellId);
    }

    public static void handleUnpossessPacket(FriendlyByteBuf buf) {
        ClientPossessionData.reset();
    }

}
