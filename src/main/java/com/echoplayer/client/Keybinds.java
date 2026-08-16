package com.echoplayer.client;

import com.echoplayer.client.wheel.PossessionWheelScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class Keybinds {
    public static final KeyMapping UNPOSSESS_KEY = new KeyMapping("key.echoplayer.unpossess", InputConstants.Type.KEYSYM, 79, "category.echoplayer");
    public static final KeyMapping POSSESSION_WHEEL_KEY = new KeyMapping(
        "key.echoplayer.possession_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "category.echoplayer");

    public static void clientTick(Minecraft mc) {
        while (UNPOSSESS_KEY.consumeClick()) {
            if (mc.player == null) continue;
            ClientCommands.execute("echoplayer unpossess");
        }
        while (POSSESSION_WHEEL_KEY.consumeClick()) {
            if (mc.player == null || mc.level == null || mc.screen != null) continue;
            PossessionWheelScreen.open();
        }
    }
}
