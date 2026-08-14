package com.echoplayer.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={FoodData.class})
public interface FoodDataAccessor {
    @Accessor("tickTimer")
    int echoplayer$getTickTimer();

    @Accessor("tickTimer")
    void echoplayer$setTickTimer(int tickTimer);

    @Accessor("lastFoodLevel")
    int echoplayer$getLastFoodLevel();

    @Accessor("lastFoodLevel")
    void echoplayer$setLastFoodLevel(int lastFoodLevel);
}
