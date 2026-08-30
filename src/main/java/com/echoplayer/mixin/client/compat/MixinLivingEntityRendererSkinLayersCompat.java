package com.echoplayer.mixin.client.compat;

import com.echoplayer.client.ClientPossessionData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Filters 3D Skin Layers out of the synthetic shell's render-layer pass. */
@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRendererSkinLayersCompat {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"
        )
    )
    private void echoplayer$filterShellRenderLayer(RenderLayer layer, PoseStack poseStack,
                                                    MultiBufferSource buffer, int packedLight, Entity entity,
                                                    float limbSwing, float limbSwingAmount, float partialTick,
                                                    float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.getId() == ClientPossessionData.shellEntityId
                && layer.getClass().getName().startsWith("dev.tr7zw.skinlayers.")) {
            return;
        }
        layer.render(poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount,
            partialTick, ageInTicks, netHeadYaw, headPitch);
    }
}
