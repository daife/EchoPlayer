package com.echoplayer.compat.palladium;

import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Character identity, deliberately separate from authentication and supporter entitlements. */
public final class PantheonIdentity {
    private PantheonIdentity() {}

    public static UUID id(Entity entity) {
        if (entity.level().isClientSide) return com.echoplayer.client.PalladiumClientCompat.identity(entity);
        if (entity instanceof EchoServerPlayer echo && echo.linkedRealPlayer != null) return echo.linkedRealPlayer.getUUID();
        if (entity instanceof ServerPlayer player) return EchoPlayerManager.getLogicalOwnerUUID(player);
        return entity.getUUID();
    }

    public static Player find(Level level, UUID id) {
        if (id == null) return null;
        if (level.isClientSide) return com.echoplayer.client.PalladiumClientCompat.findCharacter(level, id);
        ServerPlayer shell = EchoPlayerManager.getIdentityAvatar(level, id);
        if (shell != null) return shell;
        Player player = level.getPlayerByUUID(id);
        // A controller with an original body in another dimension is not that body.
        if (player instanceof ServerPlayer real && EchoPlayerManager.isPossessing(real)) return null;
        return player != null && !player.isRemoved() ? player : null;
    }
}
