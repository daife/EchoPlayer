package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={ForgeHooks.class}, remap=false)
public class MixinForgeHooks {
    @ModifyVariable(method={"onServerChatSubmittedEvent"}, at=@At(value="HEAD"), argsOnly=true, ordinal=0)
    private static ServerPlayer projectChatSender(ServerPlayer player) {
        return EchoPlayerManager.getLogicalPlayer(player);
    }
}

