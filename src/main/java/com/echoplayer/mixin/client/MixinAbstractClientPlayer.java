package com.echoplayer.mixin.client;

import com.echoplayer.client.ClientPossessionData;
import java.util.function.Function;
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
        overridePlayerInfo(cir, PlayerInfo::getSkinLocation);
    }

    @Inject(method={"getModelName"}, at={@At(value="HEAD")}, cancellable=true)
    private void overrideModelName(CallbackInfoReturnable<String> cir) {
        overridePlayerInfo(cir, PlayerInfo::getModelName);
    }

    private <T> void overridePlayerInfo(CallbackInfoReturnable<T> cir, Function<PlayerInfo, T> extractor) {
        if (ClientPossessionData.possessedUUID == null) {
            return;
        }
        AbstractClientPlayer self = (AbstractClientPlayer)((Object)this);
        if (self == Minecraft.getInstance().player) {
            PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(ClientPossessionData.possessedUUID);
            if (info != null) {
                cir.setReturnValue(extractor.apply(info));
            }
        } else if (self.getId() == ClientPossessionData.shellEntityId) {
            PlayerInfo myInfo = Minecraft.getInstance().getConnection().getPlayerInfo(Minecraft.getInstance().player.getUUID());
            if (myInfo != null) {
                cir.setReturnValue(extractor.apply(myInfo));
            }
        }
    }
}
