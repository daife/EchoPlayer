package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerEntity.class})
public class MixinServerEntity {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method={"addPairing"}, at={@At(value="TAIL")})
    private void afterAddPairing(ServerPlayer viewer, CallbackInfo ci) {
        Entity entity = this.entity;
        if (entity instanceof ServerPlayer) {
            ServerPlayer controller = (ServerPlayer)entity;
            EchoPlayerManager.hideControllerFromViewer(controller, viewer);
        }
    }
}

