package com.echoplayer.mixin;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={CommandSourceStack.class})
public interface CommandSourceStackAccessor {
    @Accessor(value="source")
    public CommandSource echoplayer$getSource();
}

