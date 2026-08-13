package com.echoplayer.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={ExperienceOrb.class})
public interface ExperienceOrbInvoker {
    @Invoker(value="repairPlayerItems")
    public int echoplayer$repairPlayerItems(Player var1, int var2);
}

