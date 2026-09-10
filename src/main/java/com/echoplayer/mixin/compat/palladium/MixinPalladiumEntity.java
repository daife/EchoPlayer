package com.echoplayer.mixin.compat.palladium;

import com.echoplayer.compat.palladium.PalladiumIntegration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Entity.class, priority = 900)
public abstract class MixinPalladiumEntity {
    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void echoplayer$saveCharacter(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if ((Object) this instanceof ServerPlayer player) PalladiumIntegration.save(player, cir.getReturnValue());
    }
}
