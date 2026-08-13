package com.echoplayer.mixin;

import java.util.Set;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={AttributeInstance.class})
public interface AttributeInstanceAccessor {
    @Accessor(value="permanentModifiers")
    public Set<AttributeModifier> echoplayer$getPermanentModifiers();

    @Invoker(value="setDirty")
    public void echoplayer$setDirty();
}

