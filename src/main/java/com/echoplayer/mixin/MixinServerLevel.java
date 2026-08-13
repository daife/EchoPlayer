package com.echoplayer.mixin;

import com.echoplayer.manager.EchoPlayerManager;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EntityGetter;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerLevel.class)
@Implements(@Interface(iface=EntityGetter.class, prefix="echoplayer$", remap=Interface.Remap.ALL))
public abstract class MixinServerLevel {
    public Player echoplayer$getPlayerByUUID(UUID playerId) {
        EntityGetter level = (EntityGetter)(Object)this;
        for (Player player : level.players()) {
            if (playerId.equals(player.getUUID())) {
                return player;
            }
        }

        ServerPlayer avatar = EchoPlayerManager.getIdentityAvatar(level, playerId);
        return avatar;
    }
}
