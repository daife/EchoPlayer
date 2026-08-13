package com.echoplayer.mixin.client;

import com.echoplayer.client.ClientPossessionData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={AbstractClientPlayer.class})
public class MixinAbstractClientPlayer {
    @Inject(method={"getSkinTextureLocation"}, at={@At(value="HEAD")}, cancellable=true)
    private void overrideSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        if (ClientPossessionData.possessedUUID != null) {
            PlayerInfo myInfo;
            AbstractClientPlayer self = (AbstractClientPlayer)((Object)this);
            if (self == Minecraft.getInstance().player) {
                PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(ClientPossessionData.possessedUUID);
                if (info != null) {
                    cir.setReturnValue(info.getSkinLocation());
                }
            } else if (self.getId() == ClientPossessionData.shellEntityId && (myInfo = Minecraft.getInstance().getConnection().getPlayerInfo(Minecraft.getInstance().player.getUUID())) != null) {
                cir.setReturnValue(myInfo.getSkinLocation());
            }
        }
    }

    @Inject(method={"getModelName"}, at={@At(value="HEAD")}, cancellable=true)
    private void overrideModelName(CallbackInfoReturnable<String> cir) {
        if (ClientPossessionData.possessedUUID != null) {
            PlayerInfo myInfo;
            AbstractClientPlayer self = (AbstractClientPlayer)((Object)this);
            if (self == Minecraft.getInstance().player) {
                PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(ClientPossessionData.possessedUUID);
                if (info != null) {
                    cir.setReturnValue(info.getModelName());
                }
            } else if (self.getId() == ClientPossessionData.shellEntityId && (myInfo = Minecraft.getInstance().getConnection().getPlayerInfo(Minecraft.getInstance().player.getUUID())) != null) {
                cir.setReturnValue(myInfo.getModelName());
            }
        }
    }
}
