package com.echoplayer.compat.palladium;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.threetag.palladium.accessory.Accessory;
import net.threetag.palladium.entity.PalladiumPlayerExtension;
import net.threetag.palladium.network.SetEnergyBarMessage;
import net.threetag.palladium.network.SyncAbilityEntryPropertyMessage;
import net.threetag.palladium.network.SyncAbilityStateMessage;
import net.threetag.palladium.network.SyncAccessoriesMessage;
import net.threetag.palladium.network.SyncFlightStateMessage;
import net.threetag.palladium.network.SyncPropertyMessage;
import net.threetag.palladium.network.UpdatePowersMessage;
import net.threetag.palladium.power.PowerManager;
import net.threetag.palladium.power.Power;
import net.threetag.palladium.power.ability.AbilityReference;
import net.threetag.palladium.power.energybar.EnergyBarReference;
import net.threetag.palladium.util.property.EntityPropertyHandler;
import net.threetag.palladium.util.property.PalladiumProperty;
import net.threetag.palladium.util.property.PropertyManager;
import net.threetag.palladium.util.property.SyncType;
import net.threetag.palladiumcore.network.MessageS2C;

/** Mirrors only changed public data to the visible body using Palladium's native protocol. */
public final class PalladiumSync {
    private PalladiumSync() {}

    static Snapshot capture(ServerPlayer player, boolean self) {
        Map<ResourceLocation, Map<String, AbilityState>> powers = new LinkedHashMap<>();
        Map<ResourceLocation, Power> definitions = new LinkedHashMap<>();
        Map<EnergyBarReference, EnergyState> energies = new LinkedHashMap<>();
        PowerManager.getPowerHandler(player).ifPresent(handler -> handler.getPowerHolders().forEach((id, holder) -> {
            definitions.put(id, holder.getPower());
            Map<String, AbilityState> abilities = new LinkedHashMap<>();
            holder.getAbilities().forEach((name, ability) -> abilities.put(name, new AbilityState(
                ability.isUnlocked(), ability.isEnabled(), ability.maxCooldown, ability.cooldown,
                ability.maxActivationTimer, ability.activationTimer, properties(ability.getPropertyManager(), self))));
            powers.put(id, abilities);
            holder.getEnergyBars().forEach((name, bar) -> energies.put(new EnergyBarReference(id, name), new EnergyState(bar.get(), bar.getMax())));
        }));
        CompoundTag properties = EntityPropertyHandler.getHandler(player).map(handler -> properties(handler, self)).orElseGet(CompoundTag::new);
        CompoundTag accessories = Accessory.getPlayerData(player).map(data -> data.toNBT().copy()).orElseGet(CompoundTag::new);
        boolean flying = ((PalladiumPlayerExtension) player).palladium$getFlightHandler().getFlightType().isNotNull();
        return new Snapshot(powers, definitions, energies, properties, accessories, flying);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CompoundTag properties(PropertyManager manager, boolean self) {
        CompoundTag tag = new CompoundTag();
        manager.values().forEach((property, value) -> {
            if (property.getSyncType() == SyncType.EVERYONE || self && property.getSyncType() == SyncType.SELF) {
                Tag encoded = value == null ? StringTag.valueOf("null") : ((PalladiumProperty) property).toNBT(value);
                tag.put(property.getKey(), encoded.copy());
            }
        });
        return tag;
    }

    static void full(ServerPlayer player, boolean self) {
        if (self) diff(player, null, capture(player, true), player);
        diff(player, null, capture(player, false), null);
    }

    /** Also used when a viewer starts tracking after a switch or dimension transfer. */
    public static void tracking(ServerPlayer player, ServerPlayer viewer) {
        diff(player, null, capture(player, false), viewer);
    }

    static void diff(ServerPlayer target, Snapshot previous, Snapshot current, ServerPlayer recipient) {
        List<ResourceLocation> removed = new ArrayList<>();
        List<ResourceLocation> added = new ArrayList<>();
        if (previous == null) {
            // A controller keeps its entity ID while changing characters. Clear
            // old holders, including ones that also exist in the new character.
            removed.addAll(PowerManager.getInstance(target.level()).getIds());
            added.addAll(current.powers.keySet());
        } else {
            previous.powers.keySet().stream().filter(id -> current.definitions.get(id) != previous.definitions.get(id)).forEach(removed::add);
            current.powers.keySet().stream().filter(id -> current.definitions.get(id) != previous.definitions.get(id)).forEach(added::add);
        }
        if (!removed.isEmpty() || !added.isEmpty()) {
            send(new UpdatePowersMessage(target, removed, added), target, recipient);
        }
        current.powers.forEach((id, abilities) -> abilities.forEach((name, state) -> {
            AbilityState old = previous == null || added.contains(id) ? null : previous.powers.getOrDefault(id, Map.of()).get(name);
            AbilityReference reference = new AbilityReference(id, name);
            if (old == null || !state.sameState(old)) {
                send(new SyncAbilityStateMessage(target.getId(), reference, state.unlocked, state.enabled,
                    state.maxCooldown, state.cooldown, state.maxActivation, state.activation), target, recipient);
            }
            for (String key : state.properties.getAllKeys()) {
                if (old == null || !Objects.equals(old.properties.get(key), state.properties.get(key))) {
                    CompoundTag tag = new CompoundTag();
                    tag.put(key, state.properties.get(key).copy());
                    send(new SyncAbilityEntryPropertyMessage(target.getId(), reference, key, tag), target, recipient);
                }
            }
        }));
        current.energies.forEach((reference, value) -> {
            if (previous == null || !value.equals(previous.energies.get(reference))) {
                send(new SetEnergyBarMessage(target.getId(), reference, value.value, value.max), target, recipient);
            }
        });
        EntityPropertyHandler.getHandler(target).ifPresent(handler -> {
            for (String key : current.properties.getAllKeys()) {
                if (previous == null || !Objects.equals(previous.properties.get(key), current.properties.get(key))) {
                    PalladiumProperty<?> property = handler.getPropertyByName(key);
                    if (property != null) send(new SyncPropertyMessage(target.getId(), property, handler.get(property)), target, recipient);
                }
            }
        });
        if (previous == null || !previous.accessories.equals(current.accessories)) {
            Accessory.getPlayerData(target).ifPresent(data -> send(new SyncAccessoriesMessage(target.getId(), data.getSlots()), target, recipient));
        }
        if (previous == null || previous.flying != current.flying) {
            send(new SyncFlightStateMessage(target.getId(), current.flying), target, recipient);
        }
    }

    private static void send(MessageS2C message, ServerPlayer target, ServerPlayer recipient) {
        if (recipient != null) message.send(recipient);
        else message.sendToTracking(target);
    }

    record Snapshot(Map<ResourceLocation, Map<String, AbilityState>> powers,
                    Map<ResourceLocation, Power> definitions,
                    Map<EnergyBarReference, EnergyState> energies,
                    CompoundTag properties, CompoundTag accessories, boolean flying) {}

    record EnergyState(int value, int max) {}

    record AbilityState(boolean unlocked, boolean enabled, int maxCooldown, int cooldown,
                        int maxActivation, int activation, CompoundTag properties) {
        boolean sameState(AbilityState other) {
            return unlocked == other.unlocked && enabled == other.enabled && maxCooldown == other.maxCooldown
                && cooldown == other.cooldown && maxActivation == other.maxActivation && activation == other.activation;
        }
    }
}
