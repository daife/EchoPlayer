package com.echoplayer.mixin.compat.pantheonsent;

import com.echoplayer.compat.palladium.PantheonIdentity;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.threetag.pantheonsent.util.PantheonSentProperties", remap = false)
public abstract class MixinPantheonProperties {
    @Redirect(method = "lambda$init$2", at = @At(value = "INVOKE", target = "Ljava/util/UUID;equals(Ljava/lang/Object;)Z"), require = 0)
    private static boolean echoplayer$recruitingIdentity(UUID avatarId, Object ignored, LivingEntity entity) {
        return avatarId.equals(PantheonIdentity.id(entity));
    }
}
