package com.echoplayer.mixin.compat.pantheonsent;

import com.echoplayer.compat.palladium.PantheonIdentity;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.threetag.pantheonsent.block.entity.UshabtiBlockEntity", remap = false)
public abstract class MixinUshabtiBlockEntity {
    @Redirect(method = {"serverTick", "clientTick"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getPlayerByUUID(Ljava/util/UUID;)Lnet/minecraft/world/entity/player/Player;", remap = true))
    private static Player echoplayer$findOwner(Level level, UUID id) {
        return PantheonIdentity.find(level, id);
    }
}
