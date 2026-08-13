package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={DamageSource.class})
public abstract class MixinDamageSource {
    @ModifyVariable(method={"<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)V"}, at=@At(value="HEAD"), argsOnly=true, ordinal=0)
    private static Entity projectDirectEntity(Entity entity, Holder<DamageType> type, Entity directEntity, Entity causingEntity, Vec3 position) {
        return EchoPlayerManager.getLogicalDamageEntity(entity);
    }

    @ModifyVariable(method={"<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;)V"}, at=@At(value="HEAD"), argsOnly=true, ordinal=1)
    private static Entity projectCausingEntity(Entity entity, Holder<DamageType> type, Entity directEntity, Entity causingEntity, Vec3 position) {
        return EchoPlayerManager.getLogicalDamageEntity(entity);
    }
}

