package com.echoplayer.network;

import net.minecraft.resources.ResourceLocation;

public class NetworkPackets {
    public static final ResourceLocation POSSESS_PACKET = new ResourceLocation("echoplayer", "possess");
    public static final ResourceLocation UNPOSSESS_PACKET = new ResourceLocation("echoplayer", "unpossess");
    public static final ResourceLocation CONTROL_SYNC_PACKET = new ResourceLocation("echoplayer", "control_sync");
    public static final ResourceLocation CONTROL_INPUT_PACKET = new ResourceLocation("echoplayer", "control_input");
}
