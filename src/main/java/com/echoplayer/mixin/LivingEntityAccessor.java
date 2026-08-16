package com.echoplayer.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={LivingEntity.class})
public interface LivingEntityAccessor {
    @Accessor("autoSpinAttackTicks")
    int echoplayer$getAutoSpinAttackTicks();

    @Accessor("autoSpinAttackTicks")
    void echoplayer$setAutoSpinAttackTicks(int autoSpinAttackTicks);

    @Accessor("attackStrengthTicker")
    int echoplayer$getAttackStrengthTicker();

    @Accessor("attackStrengthTicker")
    void echoplayer$setAttackStrengthTicker(int attackStrengthTicker);

    @Accessor("useItemRemaining")
    void echoplayer$setUseItemRemaining(int useItemRemaining);

    @Accessor("fallFlyTicks")
    void echoplayer$setFallFlyTicks(int fallFlyTicks);
}
