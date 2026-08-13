package com.echoplayer.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={MobEffectInstance.class})
public interface MobEffectInstanceAccessor {
    @Accessor(value="duration")
    public void echoplayer$setDuration(int var1);
}

