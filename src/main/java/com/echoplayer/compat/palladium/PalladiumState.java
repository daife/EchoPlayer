package com.echoplayer.compat.palladium;

import java.lang.reflect.Field;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.threetag.palladium.accessory.AccessoryPlayerData;
import net.threetag.palladium.entity.DualWieldingPlayerHandler;
import net.threetag.palladium.entity.FlightHandler;
import net.threetag.palladium.power.DefaultPowerHolder;
import net.threetag.palladium.power.PowerHandler;
import net.threetag.palladium.util.property.EntityPropertyHandler;

/**
 * Transfers live, entity-bound state. Palladium's disk NBT deliberately omits
 * cooldowns, enabled flags, condition runtime and non-persistent properties;
 * loading that NBT also does not replace already active power holders.
 *
 * These are Palladium's own unmapped field names, including fields introduced
 * by its mixins. Resolve them only after those mixins have transformed entities.
 */
final class PalladiumState {
    private static final Field POWERS = field(LivingEntity.class, "palladium$powerHandler");
    private static final Field PROPERTIES = field(Entity.class, "palladium$propertyHandler");
    private static final Field ACCESSORIES = field(Player.class, "palladium$accessories");
    private static final Field FLIGHT = field(Player.class, "palladium$flightHandler");
    private static final Field DUAL_WIELDING = field(Player.class, "palladium$dualWielding");
    private static final Field PERSISTENT_DATA = field(Entity.class, "persistentData");
    private static final Field POWER_OWNER = field(PowerHandler.class, "entity");
    private static final Field HOLDER_OWNER = field(DefaultPowerHolder.class, "entity");
    private static final Field PROPERTY_OWNER = field(EntityPropertyHandler.class, "entity");
    private static final Field FLIGHT_OWNER = field(FlightHandler.class, "player");
    private static final Field DUAL_OWNER = field(DualWieldingPlayerHandler.class, "player");

    final PowerHandler powers;
    final EntityPropertyHandler properties;
    final AccessoryPlayerData accessories;
    final FlightHandler flight;
    final DualWieldingPlayerHandler dualWielding;
    final CompoundTag persistentData;

    private PalladiumState(ServerPlayer player) {
        powers = (PowerHandler) get(POWERS, player);
        properties = (EntityPropertyHandler) get(PROPERTIES, player);
        accessories = (AccessoryPlayerData) get(ACCESSORIES, player);
        flight = (FlightHandler) get(FLIGHT, player);
        dualWielding = (DualWieldingPlayerHandler) get(DUAL_WIELDING, player);
        persistentData = player.getPersistentData();
    }

    static PalladiumState of(ServerPlayer player) {
        return new PalladiumState(player);
    }

    void validate() {
        for (var holder : powers.getPowerHolders().values()) {
            if (!(holder instanceof DefaultPowerHolder)) {
                throw new IllegalStateException("Unsupported custom Palladium power holder: " + holder.getClass().getName());
            }
        }
    }

    static void swap(ServerPlayer first, ServerPlayer second) {
        PalladiumState a = of(first);
        PalladiumState b = of(second);
        a.validate();
        b.validate();
        a.bind(second);
        b.bind(first);
    }

    void bind(ServerPlayer player) {
        set(POWER_OWNER, powers, player);
        for (var holder : powers.getPowerHolders().values()) {
            set(HOLDER_OWNER, holder, player);
        }
        set(PROPERTY_OWNER, properties, player);
        set(FLIGHT_OWNER, flight, player);
        set(DUAL_OWNER, dualWielding, player);
        set(POWERS, player, powers);
        set(PROPERTIES, player, properties);
        set(ACCESSORIES, player, accessories);
        set(FLIGHT, player, flight);
        set(DUAL_WIELDING, player, dualWielding);
        // KubeJS-backed addonpacks commonly store character state here.
        set(PERSISTENT_DATA, player, persistentData);
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Incompatible Palladium API: " + type.getName() + "." + name, exception);
        }
    }

    private static Object get(Field field, Object object) {
        try {
            return field.get(object);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void set(Field field, Object object, Object value) {
        try {
            field.set(object, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
