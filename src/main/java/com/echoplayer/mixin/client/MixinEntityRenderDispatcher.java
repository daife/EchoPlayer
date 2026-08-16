package com.echoplayer.mixin.client;

import com.echoplayer.client.ClientPossessionData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EntityRenderDispatcher.class})
public class MixinEntityRenderDispatcher {
    @Inject(method={"render"}, at={@At(value="HEAD")}, cancellable=true)
    private <E extends Entity> void echoplayer$hidePossessedEcho(E entity, double x, double y, double z,
                                                                 float yRot, float partialTicks, PoseStack poseStack,
                                                                 MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (ClientPossessionData.isPossessedEcho(entity)) {
            ci.cancel();
        }
    }
}
