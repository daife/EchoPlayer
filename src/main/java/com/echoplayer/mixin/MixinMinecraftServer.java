package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={MinecraftServer.class}, priority=800)
public class MixinMinecraftServer {
    @Inject(method={"tickChildren"}, at={@At(value="TAIL")})
    private void onTick(BooleanSupplier pHasTimeLeft, CallbackInfo ci) {
        EchoPlayerManager.tick();
    }

    @Inject(method={"getChatDecorator"}, at={@At(value="RETURN")}, cancellable=true)
    private void onGetChatDecorator(CallbackInfoReturnable<ChatDecorator> cir) {
        ChatDecorator original = (ChatDecorator)cir.getReturnValue();
        cir.setReturnValue((sender, message) -> original.decorate(sender != null ? EchoPlayerManager.getLogicalPlayer(sender) : null, message));
    }
}

