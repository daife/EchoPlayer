package com.echoplayer.client;

import com.echoplayer.network.NetworkPackets;
import com.echoplayer.platform.Services;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class ClientPossessionData {
    public static UUID possessedUUID = null;
    public static int shellEntityId = -1;
    private static boolean followingSharedControl;
    private static double targetX;
    private static double targetY;
    private static double targetZ;
    private static float targetYRot;
    private static float targetXRot;
    private static double targetVelocityX;
    private static double targetVelocityY;
    private static double targetVelocityZ;
    private static boolean targetSnap;
    private static long serverRevision;
    private static long nextInputSequence;
    private static long lastSentInputSequence;
    private static long lastAcknowledgedInputSequence;
    private static float inputBaselineYRot;
    private static float inputBaselineXRot;
    private static boolean inputBaselineInitialized;
    private static boolean pendingCameraInput;

    public static void beginPossession(UUID echoUUID, int shellId) {
        ClientPossessionData.reset();
        possessedUUID = echoUUID;
        shellEntityId = shellId;
    }

    public static void updateSharedControl(long revision, long acknowledgedSequence, boolean authoritative, boolean snap, double x, double y, double z, float yRot, float xRot, double velocityX, double velocityY, double velocityZ) {
        if (revision < serverRevision) {
            return;
        }
        serverRevision = revision;
        lastAcknowledgedInputSequence = Math.max(lastAcknowledgedInputSequence, acknowledgedSequence);
        followingSharedControl = !authoritative;
        targetSnap = snap;
        targetX = x;
        targetY = y;
        targetZ = z;
        targetYRot = yRot;
        targetXRot = xRot;
        targetVelocityX = velocityX;
        targetVelocityY = velocityY;
        targetVelocityZ = velocityZ;
    }

    public static void clientTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (possessedUUID == null || player == null) {
            return;
        }
        if (inputBaselineInitialized && (Math.abs(Mth.wrapDegrees(player.getYRot() - inputBaselineYRot)) > 0.01f || Math.abs(Mth.wrapDegrees(player.getXRot() - inputBaselineXRot)) > 0.01f)) {
            pendingCameraInput = true;
        }
        inputBaselineYRot = player.getYRot();
        inputBaselineXRot = player.getXRot();
        inputBaselineInitialized = true;
    }

    public static void afterClientTick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (possessedUUID == null || player == null) {
            return;
        }
        boolean movementInput = player.input.leftImpulse != 0.0f || player.input.forwardImpulse != 0.0f || player.input.jumping || player.input.shiftKeyDown;
        boolean cameraInput = pendingCameraInput || Math.abs(Mth.wrapDegrees(player.getYRot() - inputBaselineYRot)) > 0.01f || Math.abs(Mth.wrapDegrees(player.getXRot() - inputBaselineXRot)) > 0.01f;
        int inputMask = 0;
        if (movementInput) {
            inputMask |= 1;
        }
        if (cameraInput) {
            inputMask |= 2;
        }
        if (inputMask != 0) {
            long sequence = ++nextInputSequence;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeVarLong(sequence);
            buf.writeByte(inputMask);
            Services.PLATFORM.sendToServer(NetworkPackets.CONTROL_INPUT_PACKET, buf);
            lastSentInputSequence = sequence;
        }
        if (followingSharedControl && lastAcknowledgedInputSequence >= lastSentInputSequence) {
            double distanceSqr = player.distanceToSqr(targetX, targetY, targetZ);
            if (targetSnap || distanceSqr > 16.0) {
                player.setPos(targetX, targetY, targetZ);
                player.setYRot(Mth.wrapDegrees(targetYRot));
                player.setXRot(Mth.clamp(targetXRot, -90.0f, 90.0f));
            } else {
                if (distanceSqr > 1.0E-6) {
                    player.setPos(Mth.lerp(0.45, player.getX(), targetX), Mth.lerp(0.45, player.getY(), targetY), Mth.lerp(0.45, player.getZ(), targetZ));
                }
                player.setYRot(Mth.wrapDegrees(player.getYRot() + Mth.wrapDegrees(targetYRot - player.getYRot()) * 0.5f));
                player.setXRot(Mth.clamp(player.getXRot() + Mth.wrapDegrees(targetXRot - player.getXRot()) * 0.5f, -90.0f, 90.0f));
            }
            player.yHeadRot = player.getYRot();
            player.yBodyRot = player.getYRot();
            player.setDeltaMovement(player.getDeltaMovement().lerp(new Vec3(targetVelocityX, targetVelocityY, targetVelocityZ), 0.45));
            targetSnap = false;
        }
        inputBaselineYRot = player.getYRot();
        inputBaselineXRot = player.getXRot();
        inputBaselineInitialized = true;
        pendingCameraInput = false;
    }

    public static void reset() {
        possessedUUID = null;
        shellEntityId = -1;
        followingSharedControl = false;
        targetSnap = false;
        serverRevision = Long.MIN_VALUE;
        nextInputSequence = 0L;
        lastSentInputSequence = 0L;
        lastAcknowledgedInputSequence = 0L;
        inputBaselineInitialized = false;
        pendingCameraInput = false;
    }

    static {
        serverRevision = Long.MIN_VALUE;
    }
}

