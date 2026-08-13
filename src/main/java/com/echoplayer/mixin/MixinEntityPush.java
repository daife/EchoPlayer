package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Entity.class})
public class MixinEntityPush {
    @Inject(method={"push"}, at={@At(value="HEAD")}, cancellable=true)
    private void onPush(Entity entity, CallbackInfo ci) {
        if (EchoPlayerManager.shouldDisableCollision((Entity)((Object)this), entity)) {
            ci.cancel();
        }
    }
}

