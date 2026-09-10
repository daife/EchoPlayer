package com.echoplayer.mixin.compat.palladium;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.power.PowerManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.threetag.palladium.network.UpdatePowersMessage", remap = false)
public abstract class MixinPalladiumClientPowers {
    @Shadow(remap = false) @Final private int entityId;

    @Inject(method = "handleClient", at = @At("HEAD"))
    private void echoplayer$removeInvalidDefinitions(CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(entityId) instanceof LivingEntity entity) {
            PowerManager.getPowerHandler(entity).ifPresent(handler -> {
                // Native removal resolves IDs through the new registry. A power
                // deleted by /reload is absent there, but can still have a live holder.
                for (var holder : List.copyOf(handler.getPowerHolders().values())) {
                    if (holder.getPower().isInvalid()) handler.removePowerHolder(holder.getPower().getId());
                }
            });
        }
    }
}
