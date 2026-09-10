package com.echoplayer.mixin.compat.pantheonsent;

import com.echoplayer.compat.palladium.PantheonIdentity;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.threetag.pantheonsent.entity.Khonshu", remap = false)
public abstract class MixinKhonshu {
    @Shadow(remap = false) public UUID avatarId;
    @Shadow(remap = false) public Player avatar;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)V", at = @At("RETURN"))
    private void echoplayer$bindCharacter(Level level, Player player, CallbackInfo ci) {
        avatarId = PantheonIdentity.id(player);
        avatar = PantheonIdentity.find(level, avatarId);
    }

    @Inject(method = "getAvatar", at = @At("HEAD"), cancellable = true, require = 0)
    private void echoplayer$resolveCharacter(CallbackInfoReturnable<Player> cir) {
        if (avatarId != null) {
            // Refresh the cached reference on every lookup, including after death,
            // switching characters, disconnect and original-body shell removal.
            avatar = PantheonIdentity.find(((Entity) (Object) this).level(), avatarId);
            cir.setReturnValue(avatar);
        }
    }

    @Inject(method = {"isInvisibleTo", "m_20177_"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void echoplayer$visibility(Player player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(avatarId == null || !avatarId.equals(PantheonIdentity.id(player)));
    }
}
