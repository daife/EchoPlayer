package com.echoplayer.mixin.client.compat;

import com.echoplayer.client.ClientPossessionData;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after 3D Skin Layers and restores the vanilla shell outer layer. */
@Mixin(value = PlayerRenderer.class, priority = 900)
public abstract class MixinPlayerRendererSkinLayersCompat
        extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    protected MixinPlayerRendererSkinLayersCompat(EntityRendererProvider.Context context,
                                                   PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void echoplayer$restoreShellOuterLayer(AbstractClientPlayer player, CallbackInfo ci) {
        if (player.getId() != ClientPossessionData.shellEntityId) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = getModel();
        if (player.isSpectator()) {
            model.hat.visible = true;
            return;
        }
        model.hat.visible = player.isModelPartShown(PlayerModelPart.HAT);
        model.jacket.visible = player.isModelPartShown(PlayerModelPart.JACKET);
        model.leftSleeve.visible = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        model.leftPants.visible = player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
    }
}
