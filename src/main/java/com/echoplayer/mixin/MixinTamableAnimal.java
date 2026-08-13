package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.UUID;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TamableAnimal.class)
public abstract class MixinTamableAnimal {
    @Redirect(method={"tame"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/entity/player/Player;getUUID()Ljava/util/UUID;"))
    private UUID echoplayer$storeLogicalOwner(Player player) {
        return EchoPlayerManager.getLogicalOwnerUUID(player);
    }
}
