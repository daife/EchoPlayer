package com.echoplayer.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;

public class ClientPacketHandler {
    public static void handlePossessPacket(FriendlyByteBuf buf) {
        UUID echoUUID = buf.readUUID();
        int shellId = buf.readInt();
        applyLocalPlayerRotation(buf);
        ClientPossessionData.beginPossession(echoUUID, shellId);
    }

    public static void handleUnpossessPacket(FriendlyByteBuf buf) {
        applyLocalPlayerRotation(buf);
        ClientPossessionData.reset();
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
        player.setYRot(yRot);
        player.setXRot(xRot);
        player.setYHeadRot(yHeadRot);
        player.yBodyRot = yBodyRot;
        // Reset render interpolation too, otherwise a stale client-side pose is
        // blended into the first frames after switching bodies.
        player.yRotO = yRot;
        player.xRotO = xRot;
        player.yHeadRotO = yHeadRot;
        player.yBodyRotO = yBodyRot;
    }

}
