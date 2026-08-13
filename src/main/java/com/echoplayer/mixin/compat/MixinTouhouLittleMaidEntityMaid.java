package com.echoplayer.mixin.compat;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets={"com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid"}, remap=false)
public abstract class MixinTouhouLittleMaidEntityMaid extends TamableAnimal {
    private MixinTouhouLittleMaidEntityMaid() {
        super(null, null);
    }

    @Inject(method={"getOwner()Lnet/minecraft/world/entity/LivingEntity;"}, at={@At(value="HEAD")}, cancellable=true, remap=true)
    private void echoplayer$resolveIdentityAvatar(CallbackInfoReturnable<LivingEntity> cir) {
        UUID ownerId = this.getOwnerUUID();
        if (ownerId == null) {
            return;
        }
        ServerPlayer avatar = EchoPlayerManager.getIdentityAvatar(ownerId);
        if (avatar != null) {
            cir.setReturnValue(avatar);
        }
    }
}
