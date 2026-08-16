package com.echoplayer.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={Player.class})
public interface PlayerAccessor {
    @Accessor("takeXpDelay")
    int echoplayer$getTakeXpDelay();

    @Accessor("takeXpDelay")
    void echoplayer$setTakeXpDelay(int takeXpDelay);

    @Accessor("sleepCounter")
    int echoplayer$getSleepCounter();

    @Accessor("sleepCounter")
    void echoplayer$setSleepCounter(int sleepCounter);
}
