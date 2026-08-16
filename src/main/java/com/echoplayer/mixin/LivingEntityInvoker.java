package com.echoplayer.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={LivingEntity.class})
public interface LivingEntityInvoker {
    @Invoker(value="setLivingEntityFlag")
    void echoplayer$setLivingEntityFlag(int flag, boolean value);

    @Invoker(value="detectEquipmentUpdates")
    public void echoplayer$detectEquipmentUpdates();

    @Invoker(value="tickEffects")
    public void echoplayer$tickEffects();
}
