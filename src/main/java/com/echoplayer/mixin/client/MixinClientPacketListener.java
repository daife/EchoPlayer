package com.echoplayer.mixin.client;

import com.echoplayer.client.ClientPossessionData;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ClientPacketListener.class})
public class MixinClientPacketListener {
    @Inject(method={"onDisconnect"}, at={@At(value="HEAD")})
    private void beforeDisconnect(Component pReason, CallbackInfo ci) {
        ClientPossessionData.reset();
    }
}

