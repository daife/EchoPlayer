package com.echoplayer.mixin;

import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={ItemCooldowns.class})
public interface ItemCooldownsAccessor {
    @Accessor("cooldowns")
    Map<Item, Object> echoplayer$getCooldowns();

    @Accessor("tickCount")
    int echoplayer$getTickCount();

    @Accessor("tickCount")
    void echoplayer$setTickCount(int tickCount);
}
