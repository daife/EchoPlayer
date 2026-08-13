package com.echoplayer.mixin;

import com.echoplayer.entity.EchoServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Player.class})
public class MixinPlayerAttack {
    @Inject(method={"attack"}, at={@At(value="RETURN")})
    private void afterAttack(Entity pTarget, CallbackInfo ci) {
        if (pTarget instanceof EchoServerPlayer) {
            EchoServerPlayer echoPlayer = (EchoServerPlayer)pTarget;
            echoPlayer.restoreStoredKnockback();
        }
    }
}

