package com.echoplayer.client;

import com.echoplayer.network.NetworkPackets;
import com.echoplayer.platform.Services;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
    private static final Map<Integer, PendingRotation> PENDING_ENTITY_ROTATIONS = new HashMap<Integer, PendingRotation>();
    private static float lastSentYRot;
    private static float lastSentXRot;
    private static float lastSentYHeadRot;
    private static float lastSentYBodyRot;
    private static boolean hasSentViewRotation;

    public static void beginPossession(UUID echoUUID, int shellId) {
        ClientPossessionData.reset();
        possessedUUID = echoUUID;
        shellEntityId = shellId;
    }

    public static void queueEntityRotation(int entityId, float yRot, float xRot, float yHeadRot, float yBodyRot) {
        PENDING_ENTITY_ROTATIONS.put(entityId, new PendingRotation(yRot, xRot, yHeadRot, yBodyRot));
        applyPendingEntityRotations(Minecraft.getInstance());
    }

    public static void clientTick(Minecraft minecraft) {
        applyPendingEntityRotations(minecraft);
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

    private static void applyPendingEntityRotations(Minecraft minecraft) {
        if (PENDING_ENTITY_ROTATIONS.isEmpty() || minecraft.level == null) {
            return;
        }
        Iterator<Map.Entry<Integer, PendingRotation>> iterator = PENDING_ENTITY_ROTATIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, PendingRotation> entry = iterator.next();
            Entity entity = minecraft.level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity livingEntity)) {
                continue;
            }
            PendingRotation rotation = entry.getValue();
            ClientPacketHandler.applyEntityRotation(livingEntity, rotation.yRot, rotation.xRot, rotation.yHeadRot, rotation.yBodyRot);
            iterator.remove();
        }
    }

    public static void reset() {
        possessedUUID = null;
        shellEntityId = -1;
        PENDING_ENTITY_ROTATIONS.clear();
        hasSentViewRotation = false;
    }

    private static final class PendingRotation {
        final float yRot;
        final float xRot;
        final float yHeadRot;
        final float yBodyRot;

        PendingRotation(float yRot, float xRot, float yHeadRot, float yBodyRot) {
            this.yRot = yRot;
            this.xRot = xRot;
            this.yHeadRot = yHeadRot;
            this.yBodyRot = yBodyRot;
        }
    }
}
