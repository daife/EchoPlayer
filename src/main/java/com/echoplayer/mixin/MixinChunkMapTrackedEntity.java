package com.echoplayer.mixin;

import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets={"net.minecraft.server.level.ChunkMap$TrackedEntity"})
public class MixinChunkMapTrackedEntity {
    @Shadow
    @Final
    Entity entity;

    @Inject(method={"updatePlayer"}, at={@At(value="HEAD")}, cancellable=true)
    private void echoplayer$cullControllerEntity(ServerPlayer viewer, CallbackInfo ci) {
        ServerPlayer controller;
        Entity entity = this.entity;
        if (entity instanceof ServerPlayer && EchoPlayerManager.isPossessing(controller = (ServerPlayer)entity) && controller != viewer) {
            ci.cancel();
            return;
        }
        entity = this.entity;
        if (entity instanceof EchoServerPlayer) {
            EchoServerPlayer echoPlayer = (EchoServerPlayer)entity;
            if (EchoPlayerManager.getPossessed(viewer) == echoPlayer) {
                ci.cancel();
            }
        }
    }
}

