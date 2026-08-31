package com.echoplayer.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FishingHook.class)
public interface FishingHookInvoker {
    @Invoker("setHookedEntity")
    void echoplayer$setHookedEntity(Entity entity);
}
