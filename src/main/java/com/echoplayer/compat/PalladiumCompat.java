package com.echoplayer.compat;

import com.echoplayer.compat.palladium.PalladiumIntegration;
import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.platform.Services;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** The only entry point common code loads when Palladium is not installed. */
public final class PalladiumCompat {
    private PalladiumCompat() {}

    public static String validatePossession(ServerPlayer player, EchoServerPlayer echo) {
        if (!isLoaded()) return null;
        try {
            PalladiumIntegration.validate(player);
            PalladiumIntegration.validate(echo);
            return null;
        } catch (RuntimeException exception) {
            com.echoplayer.Constants.LOG.error("Cannot transfer Palladium character state", exception);
            return "Cannot transfer Palladium character state: " + exception.getMessage();
        }
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (isLoaded() && event.getEntity() instanceof ServerPlayer viewer
            && event.getTarget() instanceof EchoServerPlayer target) {
            com.echoplayer.compat.palladium.PalladiumSync.tracking(target, viewer);
        }
    }

    public static void moveToShell(ServerPlayer player, EchoServerPlayer shell) {
        if (isLoaded()) PalladiumIntegration.moveToShell(player, shell);
    }

    public static void enter(ServerPlayer player, EchoServerPlayer echo) {
        if (isLoaded()) PalladiumIntegration.enter(player, echo);
    }

    public static void leave(ServerPlayer player, EchoServerPlayer echo) {
        if (isLoaded()) PalladiumIntegration.leave(player, echo);
    }

    public static void restore(ServerPlayer player, EchoServerPlayer shell) {
        if (isLoaded()) PalladiumIntegration.restore(player, shell);
    }

    public static void synchronize(ServerPlayer player) {
        if (isLoaded()) PalladiumIntegration.synchronize(player);
    }

    public static void tick(ServerPlayer player, EchoServerPlayer echo) {
        if (isLoaded()) PalladiumIntegration.tick(player, echo);
    }

    public static void restoreBackup(ServerPlayer player, CompoundTag backup) {
        if (isLoaded()) PalladiumIntegration.restoreBackup(player, backup);
    }

    public static boolean isLoaded() {
        return Services.PLATFORM.isModLoaded("palladium");
    }
}
