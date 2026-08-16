package com.echoplayer.client;

import com.echoplayer.network.NetworkPackets;
import com.echoplayer.platform.Services;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class ClientPossessionData {
    public static UUID possessedUUID = null;
    public static int shellEntityId = -1;
    private static int pendingRotationEntityId = -1;
    private static float pendingYRot;
    private static float pendingXRot;
    private static float pendingYHeadRot;
    private static float pendingYBodyRot;
    private static float lastSentYRot;
    private static float lastSentXRot;
    private static float lastSentYHeadRot;
    private static float lastSentYBodyRot;
    private static boolean hasSentViewRotation;

    public static void beginPossession(UUID echoUUID, int shellId, float yRot, float xRot, float yHeadRot, float yBodyRot) {
        ClientPossessionData.reset();
        possessedUUID = echoUUID;
        shellEntityId = shellId;
        queueEntityRotation(shellId, yRot, xRot, yHeadRot, yBodyRot);
    }

    public static void queueEntityRotation(int entityId, float yRot, float xRot, float yHeadRot, float yBodyRot) {
        pendingRotationEntityId = entityId;
        pendingYRot = yRot;
        pendingXRot = xRot;
        pendingYHeadRot = yHeadRot;
        pendingYBodyRot = yBodyRot;
        applyPendingEntityRotation(Minecraft.getInstance());
    }

    public static void clientTick(Minecraft minecraft) {
        applyPendingEntityRotation(minecraft);
        sendViewRotationIfChanged(minecraft.player);
    }

    private static void sendViewRotationIfChanged(LocalPlayer player) {
        if (player == null) {
            return;
        }
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        float yHeadRot = player.getYHeadRot();
        float yBodyRot = player.yBodyRot;
        if (hasSentViewRotation
            && Math.abs(Mth.wrapDegrees(yRot - lastSentYRot)) < 0.01f
            && Math.abs(xRot - lastSentXRot) < 0.01f
            && Math.abs(Mth.wrapDegrees(yHeadRot - lastSentYHeadRot)) < 0.01f
            && Math.abs(Mth.wrapDegrees(yBodyRot - lastSentYBodyRot)) < 0.01f) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeFloat(yRot);
        buf.writeFloat(xRot);
        buf.writeFloat(yHeadRot);
        buf.writeFloat(yBodyRot);
        Services.PLATFORM.sendToServer(NetworkPackets.VIEW_ROTATION_PACKET, buf);
        lastSentYRot = yRot;
        lastSentXRot = xRot;
        lastSentYHeadRot = yHeadRot;
        lastSentYBodyRot = yBodyRot;
        hasSentViewRotation = true;
    }

    private static void applyPendingEntityRotation(Minecraft minecraft) {
        if (pendingRotationEntityId == -1 || minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(pendingRotationEntityId);
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }
        ClientPacketHandler.applyEntityRotation(livingEntity, pendingYRot, pendingXRot, pendingYHeadRot, pendingYBodyRot);
        pendingRotationEntityId = -1;
    }

    public static void reset() {
        possessedUUID = null;
        shellEntityId = -1;
        pendingRotationEntityId = -1;
        hasSentViewRotation = false;
    }
}
