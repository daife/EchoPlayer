package com.echoplayer.mixin.sync;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keeps newly-created leash links attached to the visible controlled avatar. */
@Mixin(Mob.class)
public class MixinMobLeash {
    @ModifyVariable(method = "setLeashedTo", at = @At("HEAD"), argsOnly = true)
    private Entity echoplayer$useVisibleLeashHolder(Entity holder) {
        return EchoPlayerManager.getVisibleRelationshipOwner(holder);
    }
}
