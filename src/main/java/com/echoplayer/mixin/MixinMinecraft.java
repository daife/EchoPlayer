package com.echoplayer.mixin;

import com.echoplayer.client.ClientPossessionData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Minecraft.class})
public class MixinMinecraft {
    @Inject(method={"clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V"}, at={@At(value="HEAD")})
    private void onClearLevel(Screen pScreen, CallbackInfo ci) {
        ClientPossessionData.reset();
    }
}
