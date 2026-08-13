package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={SleepStatus.class})
public class MixinSleepStatus {
    @ModifyVariable(method={"update", "areEnoughDeepSleeping"}, at=@At(value="HEAD"), argsOnly=true)
    private List<ServerPlayer> projectLogicalSleepers(List<ServerPlayer> players) {
        return EchoPlayerManager.projectSleepStatusPlayers(players);
    }
}
