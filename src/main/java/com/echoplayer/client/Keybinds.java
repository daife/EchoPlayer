package com.echoplayer.client;

import com.echoplayer.network.NetworkPackets;
import com.echoplayer.platform.Services;
import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.Unpooled;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public class Keybinds {
    public static final KeyMapping UNPOSSESS_KEY = new KeyMapping("key.echoplayer.unpossess", InputConstants.Type.KEYSYM, 79, "category.echoplayer");

    public static void clientTick(Minecraft mc) {
        while (UNPOSSESS_KEY.consumeClick()) {
            if (mc.player == null) continue;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            Services.PLATFORM.sendToServer(NetworkPackets.UNPOSSESS_PACKET, buf);
        }
    }
}

