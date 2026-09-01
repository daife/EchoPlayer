package com.echoplayer.mixin.identity.compat;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes fake player avatars react when their collar owner uses an enchanted clicker. */
@Pseudo
@Mixin(targets = "org.jlortiz.playercollars.item.ClickerItem", remap = false)
public abstract class MixinPlayerCollarsClickerItem {
    @Redirect(
        method = "lambda$use$2",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/network/simple/SimpleChannel;send(Lnet/minecraftforge/network/PacketDistributor$PacketTarget;Ljava/lang/Object;)V",
            remap = false
        ),
        require = 0,
        remap = false
    )
    private static void echoplayer$reactToOwnerClicker(SimpleChannel channel,
                                                        PacketDistributor.PacketTarget target,
                                                        Object packet,
                                                        Player owner,
                                                        ServerPlayer wearer,
                                                        @Coerce Object stacks) {
        EchoPlayerManager.applyPlayerCollarsClickerLook(wearer, owner);
        channel.send(target, packet);
    }
}
