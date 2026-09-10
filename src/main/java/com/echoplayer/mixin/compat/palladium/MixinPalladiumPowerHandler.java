package com.echoplayer.mixin.compat.palladium;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The controller and visible Echo resolve one handler, but tick it only once. */
@Pseudo
@Mixin(targets = "net.threetag.palladium.power.PowerHandler", remap = false)
public abstract class MixinPalladiumPowerHandler {
    @Shadow(remap = false) @Final private LivingEntity entity;
    @Unique private long echoplayer$lastTick = Long.MIN_VALUE;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void echoplayer$singleTick(CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            long tick = player.server.getTickCount();
            if (echoplayer$lastTick == tick) ci.cancel();
            else echoplayer$lastTick = tick;
        }
    }
}
