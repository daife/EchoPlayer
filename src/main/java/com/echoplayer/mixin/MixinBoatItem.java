package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value={BoatItem.class})
public class MixinBoatItem {
    @WrapOperation(method={"use"}, at={@At(value="INVOKE", target="Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;")})
    private List<Entity> echoplayer$filterControlledBoatPlacementEntities(Level level, Entity source, AABB box, Predicate<? super Entity> predicate, Operation<List<Entity>> original) {
        return EchoPlayerManager.filterControlledBoatPlacementEntities(source, (List)original.call(new Object[]{level, source, box, predicate}));
    }
}

