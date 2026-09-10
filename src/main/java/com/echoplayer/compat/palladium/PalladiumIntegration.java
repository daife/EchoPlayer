package com.echoplayer.compat.palladium;

import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityConfiguration;
import net.threetag.palladium.util.property.PalladiumProperties;

/** Server-side lifetime and authority for Palladium's data-driven content. */
public final class PalladiumIntegration {
    // Object identity matters during death/respawn, even when UUIDs are equal.
    private static final Map<ServerPlayer, EchoServerPlayer> ACTIVE = new WeakHashMap<>();
    private static final Map<ServerPlayer, PalladiumState> PARKED = new WeakHashMap<>();
    private static final Map<ServerPlayer, PalladiumSync.Snapshot> MIRRORS = new WeakHashMap<>();
    private static final Map<ServerPlayer, PalladiumSync.Snapshot> SELF_MIRRORS = new WeakHashMap<>();

    private PalladiumIntegration() {}

    public static void validate(ServerPlayer player) {
        PalladiumState.of(player).validate();
    }

    public static void moveToShell(ServerPlayer player, EchoServerPlayer shell) {
        releaseInput(player);
        PalladiumState.swap(player, shell);
    }

    public static void enter(ServerPlayer player, EchoServerPlayer echo) {
        if (ACTIVE.containsKey(player)) throw new IllegalStateException("Palladium possession already active");
        releaseInput(echo);
        PalladiumState state = PalladiumState.of(echo);
        state.validate();
        PARKED.put(player, PalladiumState.of(player));
        // Keep the visible Echo's fields pointing at this same live state.
        // Addons may call the extension getters directly, bypassing public APIs.
        state.bind(player);
        ACTIVE.put(player, echo);
        MIRRORS.remove(player);
        SELF_MIRRORS.remove(player);
    }

    public static void leave(ServerPlayer player, EchoServerPlayer echo) {
        if (ACTIVE.get(player) != echo) return;
        releaseInput(player);
        PalladiumState.of(player).bind(echo);
        PARKED.remove(player).bind(player);
        ACTIVE.remove(player);
        MIRRORS.remove(player);
        SELF_MIRRORS.remove(player);
        PalladiumSync.full(echo, false);
    }

    public static void restore(ServerPlayer player, EchoServerPlayer shell) {
        PalladiumState.swap(player, shell);
    }

    /** Use the same live handler for commands, damage and external addon queries. */
    public static ServerPlayer controller(Entity entity) {
        if (entity instanceof EchoServerPlayer echo) {
            ServerPlayer player = EchoPlayerManager.getController(echo);
            if (player != null && ACTIVE.get(player) == echo) return player;
        }
        return null;
    }

    public static boolean isMirror(Entity entity) {
        return controller(entity) != null;
    }

    public static void synchronize(ServerPlayer player) {
        PalladiumSync.full(player, !(player instanceof EchoServerPlayer));
        EchoServerPlayer echo = ACTIVE.get(player);
        if (echo != null) {
            PalladiumSync.full(echo, false);
            MIRRORS.put(player, PalladiumSync.capture(player, false));
            SELF_MIRRORS.put(player, PalladiumSync.capture(player, true));
        }
    }

    public static void tick(ServerPlayer player, EchoServerPlayer echo) {
        if (ACTIVE.get(player) != echo) return;
        PalladiumSync.Snapshot current = PalladiumSync.capture(player, false);
        PalladiumSync.diff(echo, MIRRORS.put(player, current), current, null);
        PalladiumSync.Snapshot self = PalladiumSync.capture(player, true);
        PalladiumSync.diff(player, SELF_MIRRORS.put(player, self), self, player);
    }

    /** Native save mixins use fields directly, so explicitly select the character being saved. */
    public static void save(ServerPlayer player, CompoundTag tag) {
        // Palladium's own mixin serializes the fields attached to this entity.
        // During possession the authenticated player has been rebound to the
        // controlled character and the hidden shell retains the original one;
        // choosing through controller() here would serialize the wrong character
        // whenever the shell is saved.
        PalladiumState state = PalladiumState.of(player);
        CompoundTag palladium = tag.getCompound("Palladium");
        palladium.put("Powers", state.powers.toNBT().copy());
        palladium.put("Properties", state.properties.toNBT(true).copy());
        palladium.put("Accessories", state.accessories.toNBT().copy());
        tag.put("Palladium", palladium);
        tag.put("ForgeData", state.persistentData.copy());
    }

    public static void restoreBackup(ServerPlayer player, CompoundTag backup) {
        // Old backups without Palladium must clear any powers acquired by the proxy.
        PalladiumState state = PalladiumState.of(player);
        for (var id : java.util.List.copyOf(state.powers.getPowerHolders().keySet())) {
            state.powers.removePowerHolder(id);
        }
        CompoundTag tag = backup.getCompound("Palladium");
        state.properties.fromNBT(tag.getCompound("Properties").copy());
        state.accessories.fromNBT(tag.getCompound("Accessories").copy());
        state.powers.fromNBT(tag.getCompound("Powers").copy());
        for (String key : java.util.Set.copyOf(state.persistentData.getAllKeys())) state.persistentData.remove(key);
        state.persistentData.merge(backup.getCompound("ForgeData").copy());
        releaseInput(player);
        state.powers.tick();
        synchronize(player);
    }

    private static void releaseInput(ServerPlayer player) {
        PalladiumState state = PalladiumState.of(player);
        for (var holder : state.powers.getPowerHolders().values()) {
            for (AbilityInstance ability : holder.getAbilities().values()) {
                if (ability.getConfiguration().getKeyPressType() == AbilityConfiguration.KeyPressType.HOLD) {
                    ability.keyPressed(player, false);
                }
            }
        }
        // Clear held keys without changing toggle states, cooldowns or flight type.
        state.properties.set(PalladiumProperties.JUMP_KEY_DOWN, false);
        state.properties.set(PalladiumProperties.LEFT_KEY_DOWN, false);
        state.properties.set(PalladiumProperties.RIGHT_KEY_DOWN, false);
        state.properties.set(PalladiumProperties.FORWARD_KEY_DOWN, false);
        state.properties.set(PalladiumProperties.BACKWARDS_KEY_DOWN, false);
    }
}
