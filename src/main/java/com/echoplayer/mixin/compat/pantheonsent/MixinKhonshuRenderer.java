package com.echoplayer.mixin.compat.pantheonsent;

import com.echoplayer.client.PalladiumClientCompat;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.threetag.pantheonsent.client.renderer.entity.KhonshuRenderer", remap = false)
public abstract class MixinKhonshuRenderer {
    @Redirect(method = {"shouldRender", "m_5523_"}, at = @At(value = "INVOKE", target = "Ljava/util/UUID;equals(Ljava/lang/Object;)Z"), require = 0)
    private boolean echoplayer$viewingCharacter(UUID avatarId, Object ignored) {
        var player = Minecraft.getInstance().player;
        return player != null && avatarId.equals(PalladiumClientCompat.identity(player));
    }
}
