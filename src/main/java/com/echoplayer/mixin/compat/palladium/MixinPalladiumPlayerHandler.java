package com.echoplayer.mixin.compat.palladium;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shared flight and offhand handlers must not advance twice per server tick. */
@Pseudo
@Mixin(targets = {
    "net.threetag.palladium.entity.FlightHandler",
    "net.threetag.palladium.entity.DualWieldingPlayerHandler"
}, remap = false)
public abstract class MixinPalladiumPlayerHandler {
    @Shadow(remap = false) @Final private Player player;
    @Unique private long echoplayer$lastTick = Long.MIN_VALUE;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void echoplayer$singleTick(CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            long tick = serverPlayer.server.getTickCount();
            if (echoplayer$lastTick == tick) ci.cancel();
            else echoplayer$lastTick = tick;
        }
    }
}
