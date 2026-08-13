package com.echoplayer.mixin;

import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value={AttributeMap.class})
public interface AttributeMapAccessor {
    @Accessor(value="attributes")
    public Map<Attribute, AttributeInstance> echoplayer$getAttributes();

    @Accessor(value="dirtyAttributes")
    public Set<AttributeInstance> echoplayer$getDirtyAttributes();

    @Invoker(value="onAttributeModified")
    public void echoplayer$onAttributeModified(AttributeInstance var1);
}

