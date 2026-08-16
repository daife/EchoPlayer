package com.echoplayer.client;

import com.echoplayer.client.wheel.PossessionWheelScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;

public class ClientPacketHandler {
    private static final int MAX_WHEEL_ENTRIES = 1024;

    public static void handlePossessPacket(FriendlyByteBuf buf) {
        UUID echoUUID = buf.readUUID();
        int shellId = buf.readInt();
        applyLocalPlayerRotation(buf);
        ClientPossessionData.beginPossession(echoUUID, shellId);
        queueEntityRotations(buf);
    }

    public static void handleUnpossessPacket(FriendlyByteBuf buf) {
        applyLocalPlayerRotation(buf);
        ClientPossessionData.reset();
        queueEntityRotations(buf);
    }

    public static void handlePossessionWheelData(FriendlyByteBuf buf) {
        if (buf.readableBytes() < Integer.BYTES + 1) {
            return;
        }
        int requestId = buf.readInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_WHEEL_ENTRIES) {
            return;
        }
        List<String> names = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            names.add(buf.readUtf(16));
        }
        PossessionWheelScreen.acceptEntries(requestId, names);
    }

    private static void queueEntityRotations(FriendlyByteBuf buf) {
        int entityViewCount = buf.readVarInt();
        for (int i = 0; i < entityViewCount; i++) {
            int entityId = buf.readInt();
            float yRot = buf.readFloat();
            float xRot = buf.readFloat();
            float yHeadRot = buf.readFloat();
            float yBodyRot = buf.readFloat();
            ClientPossessionData.queueEntityRotation(entityId, yRot, xRot, yHeadRot, yBodyRot);
        }
    }

    /**
     * A player-position packet updates the local camera yaw/pitch only.  It does
     * not reliably update the local player model's independent head and body
     * yaw, because the local player is not updated through entity tracking.
     */
    private static void applyLocalPlayerRotation(FriendlyByteBuf buf) {
        float yRot = buf.readFloat();
        float xRot = buf.readFloat();
        float yHeadRot = buf.readFloat();
        float yBodyRot = buf.readFloat();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        applyEntityRotation(player, yRot, xRot, yHeadRot, yBodyRot);
    }

    static void applyEntityRotation(LivingEntity entity, float yRot, float xRot, float yHeadRot, float yBodyRot) {
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.setYHeadRot(yHeadRot);
        entity.yBodyRot = yBodyRot;
        // Reset render interpolation too, otherwise a stale client-side pose is
        // blended into the first frames after switching bodies.
        entity.yRotO = yRot;
        entity.xRotO = xRot;
        entity.yHeadRotO = yHeadRot;
        entity.yBodyRotO = yBodyRot;
    }

}
