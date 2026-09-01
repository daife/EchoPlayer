package com.echoplayer.compat;

import com.echoplayer.Constants;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ThornsEnchantment;

/**
 * Optional compatibility bridge for PlayerCollars 1.2.x.
 *
 * <p>The released 1.2.6 Forge jar stores leash state directly on the target
 * ServerPlayer in mixin-added fields and separately mirrors the holder on a
 * hidden LeashProxyEntity. EchoPlayer must migrate both pieces of state when
 * the authenticated body is replaced by a visible Echo/Shell.</p>
 */
public final class PlayerCollarsCompat {
    private static final String LEASH_IMPL = "org.jlortiz.playercollars.leash.LeashImpl";
    private static final String HOLDER_FIELD = "leashplayers$holder";
    private static final String LAST_AGE_FIELD = "leashplayers$lastage";
    private static final String LOYALTY_FIELD = "leashplayer$loyalty";
    private static final String ATTACH_METHOD = "leashplayers$attach";
    private static final String DETACH_METHOD = "leashplayers$detach";
    private static final String DROP_METHOD = "leashplayers$drop";

    private static volatile ReflectionState reflectionState;
    private static volatile boolean lookupAttempted;
    private static volatile CuriosState curiosState;
    private static volatile boolean curiosLookupAttempted;

    private PlayerCollarsCompat() {
    }

    /**
     * PlayerCollars 1.2.6 detaches only when its private holder field is the
     * exact Player object passed to Player.interactOn. During possession the
     * visible leash holder is the controlled EchoPlayer instead. Just before
     * PlayerCollars' RETURN injector executes, temporarily expose the real
     * controller for that comparison. The proxy remains attached to the Echo
     * until PlayerCollars itself calls detach(), so rendering never jumps.
     */
    public static void prepareDetachInteraction(Player target, ServerPlayer controller, Player visibleActor) {
        if (!(target instanceof ServerPlayer) || controller == null || visibleActor == null || controller == visibleActor) {
            return;
        }
        ReflectionState state = state(target);
        if (state == null || !state.leashImpl.isInstance(target)) {
            return;
        }
        try {
            if (state.holder.get(target) != visibleActor) {
                return;
            }
            int lastAge = state.lastAge.getInt(target);
            if (lastAge + 20 >= target.tickCount) {
                return;
            }
            state.holder.set(target, controller);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logFailure("prepare PlayerCollars detach interaction", e);
        }
    }

    /** Moves every PlayerCollars private holder reference old -> new. */
    public static void transferHolderReferences(ServerLevel level, Entity previousHolder, Entity newHolder) {
        if (level == null || previousHolder == null || newHolder == null || previousHolder == newHolder) {
            return;
        }
        ReflectionState state = state(previousHolder);
        if (state == null) {
            return;
        }
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof ServerPlayer target) || !state.leashImpl.isInstance(target)) {
                continue;
            }
            try {
                if (state.holder.get(target) == previousHolder) {
                    state.attach.invoke(target, newHolder);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                logFailure("transfer PlayerCollars holder reference", e);
                return;
            }
        }
    }

    /**
     * Moves PlayerCollars state when the replaced player itself is the leashed
     * target. The 1.2.6 proxy permanently follows the target passed to its
     * constructor, so that proxy must be recreated on the replacement body.
     */
    public static void transferLeashedTarget(ServerPlayer previousTarget, ServerPlayer newTarget) {
        if (previousTarget == null || newTarget == null || previousTarget == newTarget) {
            return;
        }
        ReflectionState state = state(previousTarget);
        if (state == null || !state.leashImpl.isInstance(previousTarget) || !state.leashImpl.isInstance(newTarget)) {
            return;
        }
        try {
            Object holderValue = state.holder.get(previousTarget);
            if (!(holderValue instanceof Entity holder)) {
                return;
            }
            int loyalty = state.loyalty.getInt(previousTarget);

            // PlayerCollars detach() removes its hidden proxy but does not drop a lead.
            state.detach.invoke(previousTarget);
            if (state.holder.get(newTarget) instanceof Entity) {
                state.detach.invoke(newTarget);
            }
            state.loyalty.setInt(newTarget, loyalty);
            state.attach.invoke(newTarget, holder);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logFailure("transfer PlayerCollars leashed target", e);
        }
    }

    /**
     * Projects PlayerCollars' leash physics from the visible EchoPlayer onto
     * the authenticated player that actually receives movement input while it
     * is being controlled. The leash state and proxy stay on the EchoPlayer so
     * other clients continue to see the relationship on the visible avatar.
     */
    public static void applyControlledLeashPhysics(ServerPlayer visibleTarget, ServerPlayer controller) {
        if (visibleTarget == null || controller == null || visibleTarget == controller) {
            return;
        }
        ReflectionState state = state(visibleTarget);
        if (state == null || !state.leashImpl.isInstance(visibleTarget)) {
            return;
        }
        try {
            Object holderValue = state.holder.get(visibleTarget);
            if (!(holderValue instanceof Entity holder) || holder.level() != controller.level()) {
                return;
            }

            int loyalty = state.loyalty.getInt(visibleTarget);
            float distance = controller.distanceTo(holder);
            if (distance < 4 - loyalty) {
                return;
            }
            if (distance > 10.0f - loyalty) {
                state.detach.invoke(visibleTarget);
                state.drop.invoke(visibleTarget);
                return;
            }

            double dx = (holder.getX() - controller.getX()) / distance;
            double dy = (holder.getY() - controller.getY()) / distance;
            double dz = (holder.getZ() - controller.getZ()) / distance;
            double factor = 0.4d + 0.1d * loyalty;
            controller.push(
                Math.copySign(dx * dx * factor, dx),
                Math.copySign(dy * dy * factor, dy),
                Math.copySign(dz * dz * factor, dz));
            controller.connection.send(new ClientboundSetEntityMotionPacket(controller));
            controller.hasImpulse = false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            logFailure("apply PlayerCollars controlled leash physics", e);
        }
    }

    /** Applies vanilla thorns behaviour for a PlayerCollars collar in the Curios necklace slot. */
    public static void applyCollarThorns(ServerPlayer wearer, Entity attacker) {
        if (wearer == null || attacker == null || attacker == wearer || wearer.level() != attacker.level()) {
            return;
        }
        ItemStack collar = findFunctionalCollar(wearer);
        int level = collar.getEnchantmentLevel(Enchantments.THORNS);
        if (level <= 0 || !ThornsEnchantment.shouldHit(level, wearer.getRandom())) {
            return;
        }
        attacker.hurt(wearer.damageSources().thorns(wearer), ThornsEnchantment.getDamage(level, wearer.getRandom()));
        collar.hurtAndBreak(2, wearer, ignored -> {
        });
    }

    private static ItemStack findFunctionalCollar(ServerPlayer wearer) {
        CuriosState state = curiosState(wearer);
        if (state == null) {
            return ItemStack.EMPTY;
        }
        try {
            Object lazyOptional = state.getCuriosInventory.invoke(null, wearer);
            Optional<?> handler = (Optional<?>)state.resolve.invoke(lazyOptional);
            if (handler.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Optional<?> necklace = (Optional<?>)state.getStacksHandler.invoke(handler.get(), "necklace");
            if (necklace.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object stacks = state.getStacks.invoke(necklace.get());
            int slots = (Integer)state.getSlots.invoke(stacks);
            for (int slot = 0; slot < slots; slot++) {
                ItemStack stack = (ItemStack)state.getStackInSlot.invoke(stacks, slot);
                if (!stack.isEmpty() && state.collarItem.isInstance(stack.getItem())) {
                    return stack;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            logFailure("apply PlayerCollars collar thorns", e);
        }
        return ItemStack.EMPTY;
    }

    private static CuriosState curiosState(Object instance) {
        CuriosState existing = curiosState;
        if (existing != null) {
            return existing;
        }
        if (curiosLookupAttempted || instance == null) {
            return null;
        }
        synchronized (PlayerCollarsCompat.class) {
            if (curiosState != null) {
                return curiosState;
            }
            if (curiosLookupAttempted) {
                return null;
            }
            curiosLookupAttempted = true;
            try {
                ClassLoader loader = instance.getClass().getClassLoader();
                Class<?> collarItem = Class.forName("org.jlortiz.playercollars.item.CollarItem", false, loader);
                Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi", false, loader);
                Class<?> handlerType = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler", false, loader);
                Class<?> stacksHandlerType = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler", false, loader);
                Class<?> dynamicStacksType = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler", false, loader);
                Method getCuriosInventory = curiosApi.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
                Object lazyOptional = getCuriosInventory.invoke(null, instance);
                Method resolve = lazyOptional.getClass().getMethod("resolve");
                Method getStacksHandler = handlerType.getMethod("getStacksHandler", String.class);
                Method getStacks = stacksHandlerType.getMethod("getStacks");
                Method getSlots = dynamicStacksType.getMethod("getSlots");
                Method getStackInSlot = dynamicStacksType.getMethod("getStackInSlot", int.class);
                curiosState = new CuriosState(collarItem, getCuriosInventory, resolve, getStacksHandler, getStacks, getSlots, getStackInSlot);
                return curiosState;
            } catch (ClassNotFoundException e) {
                return null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                logFailure("initialize PlayerCollars collar thorns compatibility", e);
                return null;
            }
        }
    }

    private static ReflectionState state(Object instance) {
        ReflectionState existing = reflectionState;
        if (existing != null) {
            return existing;
        }
        if (lookupAttempted || instance == null) {
            return null;
        }
        synchronized (PlayerCollarsCompat.class) {
            if (reflectionState != null) {
                return reflectionState;
            }
            if (lookupAttempted) {
                return null;
            }
            lookupAttempted = true;
            try {
                ClassLoader loader = instance.getClass().getClassLoader();
                Class<?> leashImpl = Class.forName(LEASH_IMPL, false, loader);
                Field holder = findField(ServerPlayer.class, HOLDER_FIELD);
                Field lastAge = findField(ServerPlayer.class, LAST_AGE_FIELD);
                Field loyalty = findField(ServerPlayer.class, LOYALTY_FIELD);
                Method attach = findMethod(ServerPlayer.class, ATTACH_METHOD, Entity.class);
                Method detach = findMethod(ServerPlayer.class, DETACH_METHOD);
                Method drop = findMethod(ServerPlayer.class, DROP_METHOD);

                holder.setAccessible(true);
                lastAge.setAccessible(true);
                loyalty.setAccessible(true);
                attach.setAccessible(true);
                detach.setAccessible(true);
                drop.setAccessible(true);
                reflectionState = new ReflectionState(leashImpl, holder, lastAge, loyalty, attach, detach, drop);
                return reflectionState;
            } catch (ClassNotFoundException e) {
                // PlayerCollars is optional.
                return null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                logFailure("initialize PlayerCollars compatibility", e);
                return null;
            }
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void logFailure(String action, Throwable throwable) {
        Throwable cause = throwable instanceof InvocationTargetException invocation && invocation.getCause() != null
            ? invocation.getCause() : throwable;
        Constants.LOG.warn("Failed to {}", action, cause);
    }

    private record ReflectionState(
        Class<?> leashImpl,
        Field holder,
        Field lastAge,
        Field loyalty,
        Method attach,
        Method detach,
        Method drop
    ) {
    }

    private record CuriosState(
        Class<?> collarItem,
        Method getCuriosInventory,
        Method resolve,
        Method getStacksHandler,
        Method getStacks,
        Method getSlots,
        Method getStackInSlot
    ) {
    }
}
