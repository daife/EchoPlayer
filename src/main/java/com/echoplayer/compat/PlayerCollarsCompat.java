package com.echoplayer.compat;

import com.echoplayer.Constants;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Optional compatibility bridge for PlayerCollars' player leash implementation.
 *
 * <p>PlayerCollars stores its logical leash holder in fields mixed directly into
 * {@link ServerPlayer}; that state is separate from the vanilla leash holder on
 * its invisible proxy mob. EchoPlayer replaces the authenticated body with a
 * visible shell while possessed, so both pieces of state must move together.
 * Reflection keeps PlayerCollars an optional dependency.</p>
 */
public final class PlayerCollarsCompat {
    private static final String LEASH_IMPL = "org.jlortiz.playercollars.leash.LeashImpl";
    private static final String HOLDER_FIELD = "leashplayers$holder";
    private static final String LAST_AGE_FIELD = "leashplayers$lastage";
    private static final String LOYALTY_FIELD = "leashplayer$loyalty";
    private static final String ATTACH_METHOD = "leashplayers$attach";
    private static final String DETACH_METHOD = "leashplayers$detach";

    private static volatile ReflectionState reflectionState;
    private static volatile boolean lookupAttempted;

    private PlayerCollarsCompat() {
    }

    /**
     * PlayerCollars compares its private holder field directly with the
     * authenticated player passed to Player.interactOn. While possessed, the
     * private holder normally points at the visible Echo instead. Just before a
     * valid detach interaction, temporarily expose the authenticated controller
     * in that private comparison field. The proxy remains attached to the Echo,
     * so no visible relationship changes before PlayerCollars performs detach().
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

    /**
     * Replaces holder references on every PlayerCollars-leashed player in the
     * level. This is the PlayerCollars equivalent of moving vanilla Mob leash
     * holders from body -> shell or shell -> body.
     */
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
     * Moves the leash state where the body itself is the leashed target. A
     * PlayerCollars proxy permanently follows the target passed to its
     * constructor, so the old proxy must be detached without dropping a lead
     * and a new proxy must be attached to the replacement body.
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

            // detach() removes the old proxy but deliberately does not drop a lead.
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

    private static ReflectionState state(Object instance) {
        if (reflectionState != null) {
            return reflectionState;
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
                Class<?> serverPlayerClass = ServerPlayer.class;
                Field holder = findField(serverPlayerClass, HOLDER_FIELD);
                Field lastAge = findField(serverPlayerClass, LAST_AGE_FIELD);
                Field loyalty = findField(serverPlayerClass, LOYALTY_FIELD);
                Method attach = findMethod(serverPlayerClass, ATTACH_METHOD, Entity.class);
                Method detach = findMethod(serverPlayerClass, DETACH_METHOD);

                holder.setAccessible(true);
                lastAge.setAccessible(true);
                loyalty.setAccessible(true);
                attach.setAccessible(true);
                detach.setAccessible(true);
                reflectionState = new ReflectionState(leashImpl, holder, lastAge, loyalty, attach, detach);
                return reflectionState;
            } catch (ClassNotFoundException e) {
                // PlayerCollars is optional; absence is expected.
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
        Method detach
    ) {
    }
}
