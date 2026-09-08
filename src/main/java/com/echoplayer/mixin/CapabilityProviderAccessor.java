package com.echoplayer.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CapabilityProvider.class)
public interface CapabilityProviderAccessor {
    @Invoker("serializeCaps")
    CompoundTag echoplayer$serializeCaps();

    @Invoker("deserializeCaps")
    void echoplayer$deserializeCaps(CompoundTag tag);
}
