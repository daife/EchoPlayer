package com.echoplayer.network;

import net.minecraft.resources.ResourceLocation;

public class NetworkPackets {
    public static final ResourceLocation POSSESS_PACKET = new ResourceLocation("echoplayer", "possess");
    public static final ResourceLocation UNPOSSESS_PACKET = new ResourceLocation("echoplayer", "unpossess");
    public static final ResourceLocation VIEW_ROTATION_PACKET = new ResourceLocation("echoplayer", "view_rotation");
    public static final ResourceLocation POSSESSION_WHEEL_REQUEST_PACKET = new ResourceLocation("echoplayer", "possession_wheel_request");
    public static final ResourceLocation POSSESSION_WHEEL_DATA_PACKET = new ResourceLocation("echoplayer", "possession_wheel_data");
}
