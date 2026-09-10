package com.echoplayer.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Client-only identity projection for PantheonSent's local avatar visibility. */
public final class PalladiumClientCompat {
    private PalladiumClientCompat() {}

    public static UUID identity(Entity entity) {
        var local = Minecraft.getInstance().player;
        if (local != null && ClientPossessionData.possessedUUID != null) {
            if (entity == local) return ClientPossessionData.possessedUUID;
            if (entity.getId() == ClientPossessionData.shellEntityId) return local.getUUID();
        }
        return entity.getUUID();
    }

    public static Player findCharacter(Level level, UUID id) {
        var local = Minecraft.getInstance().player;
        if (local != null && ClientPossessionData.possessedUUID != null && id.equals(local.getUUID())) {
            Entity shell = level.getEntity(ClientPossessionData.shellEntityId);
            return shell instanceof Player player ? player : null;
        }
        return level.getPlayerByUUID(id);
    }
}
