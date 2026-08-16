package com.echoplayer.network;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class ServerPacketHandler {
    private static final int MAX_ENTITY_VIEWS = 1024;

    public static void handleUnpossessPacket(ServerPlayer sender, FriendlyByteBuf buf) {
        if (sender == null || sender.getServer() == null) {
            return;
        }
        sender.getServer().execute(() -> EchoPlayerManager.revertPossession(sender));
    }

    public static void handleViewRotationPacket(ServerPlayer sender, FriendlyByteBuf buf) {
        if (sender == null || sender.getServer() == null || buf.readableBytes() < Float.BYTES * 4 + 1) {
            return;
        }
        float yRot = buf.readFloat();
        float xRot = buf.readFloat();
        float yHeadRot = buf.readFloat();
        float yBodyRot = buf.readFloat();
        int entityViewCount = buf.readVarInt();
        if (entityViewCount < 0 || entityViewCount > MAX_ENTITY_VIEWS
            || buf.readableBytes() < entityViewCount * (Integer.BYTES + Float.BYTES * 4)) {
            return;
        }
        Map<Integer, float[]> entityViews = new HashMap<Integer, float[]>(entityViewCount);
        for (int i = 0; i < entityViewCount; i++) {
            int entityId = buf.readInt();
            entityViews.put(entityId, new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()});
        }
        sender.getServer().execute(() -> EchoPlayerManager.updateClientViews(
            sender, yRot, xRot, yHeadRot, yBodyRot, entityViews));
    }

}
