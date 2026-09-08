package com.echoplayer.compat;

import com.echoplayer.Constants;
import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.mixin.CapabilityProviderAccessor;
import com.echoplayer.platform.Services;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Optional state bridge for Yes Steve Model player model selections. */
public final class YesSteveModelCompat {
    private static final List<String> MODEL_CAPABILITY_KEYS = List.of(
        "yes_steve_model:model_id",
        "ysm:model_id"
    );

    private YesSteveModelCompat() {
    }

    /**
     * Copies only YSM's persistent model-selection capability. Other player
     * capabilities remain owned by their original entities.
     */
    public static boolean copyModelSelection(ServerPlayer source, ServerPlayer target) {
        if (!isLoaded() || source == null || target == null || source == target) {
            return false;
        }
        try {
            CompoundTag sourceCaps = ((CapabilityProviderAccessor)(Object)source).echoplayer$serializeCaps();
            if (sourceCaps == null) {
                return false;
            }
            CompoundTag ysmCaps = new CompoundTag();
            CompoundTag targetCaps = ((CapabilityProviderAccessor)(Object)target).echoplayer$serializeCaps();
            for (String key : MODEL_CAPABILITY_KEYS) {
                if (sourceCaps.contains(key)) {
                    ysmCaps.put(key, sourceCaps.get(key).copy());
                }
            }
            if (ysmCaps.isEmpty() || containsSameSelections(ysmCaps, targetCaps)) {
                return false;
            }
            normalizeDevelopmentSelectionTag(ysmCaps);
            ((CapabilityProviderAccessor)(Object)target).echoplayer$deserializeCaps(ysmCaps);
            return true;
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to copy Yes Steve Model selection from {} to {}",
                source.getGameProfile().getName(), target.getGameProfile().getName(), exception);
            return false;
        }
    }

    public static void synchronizeModelSelection(ServerPlayer source, ServerPlayer target) {
        if (copyModelSelection(source, target)) {
            synchronizeModelSelection(target);
        }
    }

    /** Makes YSM resend the target's authoritative model after an identity swap. */
    public static void synchronizeModelSelection(ServerPlayer target) {
        if (!isLoaded() || target == null || target.server == null) {
            return;
        }
        CompoundTag originalSelection = null;
        try {
            if (target instanceof EchoServerPlayer) {
                originalSelection = readModelSelection(target);
                CompoundTag forcedSelection = originalSelection.copy();
                setMandatory(forcedSelection, true);
                ((CapabilityProviderAccessor)(Object)target).echoplayer$deserializeCaps(forcedSelection);
            }
            for (ServerPlayer viewer : target.server.getPlayerList().getPlayers()) {
                if (!(viewer instanceof EchoServerPlayer)
                    && viewer.level().dimension() == target.level().dimension()) {
                    MinecraftForge.EVENT_BUS.post(new PlayerEvent.StartTracking(viewer, target));
                }
            }
        } catch (RuntimeException exception) {
            Constants.LOG.error("Failed to synchronize Yes Steve Model selection for {}",
                target.getGameProfile().getName(), exception);
        } finally {
            if (originalSelection != null) {
                ((CapabilityProviderAccessor)(Object)target).echoplayer$deserializeCaps(originalSelection);
            }
        }
    }

    private static boolean isLoaded() {
        return Services.PLATFORM.isModLoaded("yes_steve_model") || Services.PLATFORM.isModLoaded("ysm");
    }

    private static boolean containsSameSelections(CompoundTag source, CompoundTag target) {
        if (target == null) {
            return false;
        }
        for (String key : MODEL_CAPABILITY_KEYS) {
            if (source.contains(key) && !source.get(key).equals(target.get(key))) {
                return false;
            }
        }
        return true;
    }

    private static void normalizeDevelopmentSelectionTag(CompoundTag caps) {
        CompoundTag selection = caps.getCompound("ysm:model_id");
        if (selection.contains("model_hash") && !selection.contains("model_id")) {
            selection.putString("model_id", selection.getString("model_hash"));
        }
    }

    private static CompoundTag readModelSelection(ServerPlayer player) {
        CompoundTag allCaps = ((CapabilityProviderAccessor)(Object)player).echoplayer$serializeCaps();
        CompoundTag selection = new CompoundTag();
        if (allCaps != null) {
            for (String key : MODEL_CAPABILITY_KEYS) {
                if (allCaps.contains(key)) {
                    selection.put(key, allCaps.get(key).copy());
                }
            }
        }
        normalizeDevelopmentSelectionTag(selection);
        return selection;
    }

    private static void setMandatory(CompoundTag caps, boolean mandatory) {
        for (String key : MODEL_CAPABILITY_KEYS) {
            if (caps.contains(key)) {
                caps.getCompound(key).putBoolean("mandatory", mandatory);
            }
        }
    }
}
