package com.echoplayer.api.control;

import com.echoplayer.Constants;
import com.echoplayer.entity.EchoServerPlayer;
import com.echoplayer.manager.EchoPlayerManager;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Public control arbitration API shared by possession and automation mods.
 *
 * <p>Every method that reads or mutates control state must be called on the
 * target Minecraft server thread. Automation integrations should acquire a
 * lease before touching an EchoPlayer and close it on every normal exit path.</p>
 */
public final class EchoPlayerControlApi {
    private static final Map<MinecraftServer, Map<UUID, Registration>> AUTOMATION_LEASES = new IdentityHashMap<>();
    private static final Set<MinecraftServer> STOPPING_SERVERS = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final AutomationControlListener NOOP_LISTENER = (lease, reason) -> {};

    private EchoPlayerControlApi() {}

    /** Atomically acquires a non-preemptible automation lease. */
    public static AutomationControlAcquisition tryAcquireAutomation(
        EchoServerPlayer echoPlayer,
        ResourceLocation controllerId
    ) {
        return tryAcquireAutomation(echoPlayer, controllerId,
            PossessionPreemptionPolicy.DENY, NOOP_LISTENER);
    }

    /** Atomically acquires a non-preemptible automation lease with lifecycle notifications. */
    public static AutomationControlAcquisition tryAcquireAutomation(
        EchoServerPlayer echoPlayer,
        ResourceLocation controllerId,
        AutomationControlListener listener
    ) {
        return tryAcquireAutomation(echoPlayer, controllerId,
            PossessionPreemptionPolicy.DENY, listener);
    }

    /** Atomically acquires automation control with an explicit possession-preemption policy. */
    public static AutomationControlAcquisition tryAcquireAutomation(
        EchoServerPlayer echoPlayer,
        ResourceLocation controllerId,
        PossessionPreemptionPolicy possessionPreemptionPolicy,
        AutomationControlListener listener
    ) {
        Objects.requireNonNull(echoPlayer, "echoPlayer");
        Objects.requireNonNull(controllerId, "controllerId");
        Objects.requireNonNull(possessionPreemptionPolicy, "possessionPreemptionPolicy");
        Objects.requireNonNull(listener, "listener");
        MinecraftServer server = echoPlayer.server;
        requireServerThread(server);
        if (STOPPING_SERVERS.contains(server) || !isUsableEcho(echoPlayer)) {
            return AutomationControlAcquisition.rejected(AutomationControlAcquisition.Status.ECHO_UNAVAILABLE);
        }
        if (EchoPlayerManager.isPossessed(echoPlayer)) {
            return AutomationControlAcquisition.rejected(AutomationControlAcquisition.Status.POSSESSED);
        }

        Map<UUID, Registration> serverLeases = AUTOMATION_LEASES.computeIfAbsent(server, ignored -> new HashMap<>());
        Registration existing = serverLeases.get(echoPlayer.getUUID());
        if (existing != null) {
            if (existing.echoPlayer == echoPlayer && existing.lease.isActive()) {
                return AutomationControlAcquisition.rejected(AutomationControlAcquisition.Status.ALREADY_AUTOMATED);
            }
            terminate(existing, AutomationControlLease.TerminationReason.ECHO_UNAVAILABLE, true);
            serverLeases.remove(echoPlayer.getUUID(), existing);
        }

        AutomationControlLease lease = new AutomationControlLease(
            server, echoPlayer.getUUID(), controllerId, UUID.randomUUID(), possessionPreemptionPolicy);
        serverLeases.put(echoPlayer.getUUID(), new Registration(echoPlayer, lease, listener));
        return AutomationControlAcquisition.acquired(lease);
    }

    /**
     * Possession lifecycle hook. Returns whether possession may proceed after any active
     * automation controller has synchronously released its writable control channels.
     */
    public static PossessionPreemptionResult beginPossession(
        EchoServerPlayer echoPlayer,
        UUID possessorId
    ) {
        Objects.requireNonNull(echoPlayer, "echoPlayer");
        Objects.requireNonNull(possessorId, "possessorId");
        requireServerThread(echoPlayer.server);
        Registration registration = findRegistration(echoPlayer);
        if (registration == null) {
            return PossessionPreemptionResult.NOT_AUTOMATED;
        }
        if (registration.lease.state() == AutomationControlLease.State.POSSESSION_SUSPENDED) {
            return PossessionPreemptionResult.ALREADY_SUSPENDED;
        }
        if (registration.lease.possessionPreemptionPolicy() != PossessionPreemptionPolicy.ALLOW) {
            return PossessionPreemptionResult.DENIED;
        }
        final boolean released;
        try {
            released = registration.listener.onPossessionPreempting(registration.lease, possessorId);
        } catch (Throwable error) {
            Constants.LOG.error("Automation controller {} failed to yield EchoPlayer {}",
                registration.lease.controllerId(), registration.lease.echoPlayerId(), error);
            return PossessionPreemptionResult.DENIED;
        }
        if (!released || !registration.lease.isActive()) {
            return PossessionPreemptionResult.DENIED;
        }
        registration.lease.suspendForPossession();
        registration.possessorId = possessorId;
        return PossessionPreemptionResult.SUSPENDED;
    }

    /** Possession lifecycle hook that returns a retained lease to its automation controller. */
    public static void endPossession(EchoServerPlayer echoPlayer, UUID possessorId) {
        Objects.requireNonNull(echoPlayer, "echoPlayer");
        Objects.requireNonNull(possessorId, "possessorId");
        requireServerThread(echoPlayer.server);
        Registration registration = findRegistration(echoPlayer);
        if (registration == null
            || registration.lease.state() != AutomationControlLease.State.POSSESSION_SUSPENDED
            || !possessorId.equals(registration.possessorId)) {
            return;
        }
        registration.possessorId = null;
        registration.lease.resumeAfterPossession();
        try {
            registration.listener.onPossessionReleased(registration.lease, possessorId);
        } catch (Throwable error) {
            Constants.LOG.error("Automation controller {} failed while resuming EchoPlayer {}",
                registration.lease.controllerId(), registration.lease.echoPlayerId(), error);
        }
    }

    public static ControlMode getControlMode(EchoServerPlayer echoPlayer) {
        Objects.requireNonNull(echoPlayer, "echoPlayer");
        requireServerThread(echoPlayer.server);
        if (STOPPING_SERVERS.contains(echoPlayer.server) || !isUsableEcho(echoPlayer)) {
            return ControlMode.UNAVAILABLE;
        }
        if (EchoPlayerManager.isPossessed(echoPlayer)) {
            return ControlMode.POSSESSED;
        }
        return findRegistration(echoPlayer) != null ? ControlMode.AUTOMATED : ControlMode.IDLE;
    }

    public static Optional<ResourceLocation> getAutomationControllerId(EchoServerPlayer echoPlayer) {
        Objects.requireNonNull(echoPlayer, "echoPlayer");
        requireServerThread(echoPlayer.server);
        Registration registration = findRegistration(echoPlayer);
        return registration == null ? Optional.empty() : Optional.of(registration.lease.controllerId());
    }

    public static boolean isAutomated(EchoServerPlayer echoPlayer) {
        return getAutomationControllerId(echoPlayer).isPresent();
    }

    /**
     * EchoPlayer lifecycle hook. Integrations must not call this to steal a lease.
     */
    public static void releaseUnavailableEcho(EchoServerPlayer echoPlayer) {
        Objects.requireNonNull(echoPlayer, "echoPlayer");
        MinecraftServer server = echoPlayer.server;
        requireServerThread(server);
        if (isUsableEcho(echoPlayer)) {
            throw new IllegalStateException("Cannot revoke automation control from an available EchoPlayer");
        }
        Map<UUID, Registration> serverLeases = AUTOMATION_LEASES.get(server);
        if (serverLeases == null) {
            return;
        }
        Registration registration = serverLeases.get(echoPlayer.getUUID());
        if (registration == null || registration.echoPlayer != echoPlayer) {
            return;
        }
        serverLeases.remove(echoPlayer.getUUID());
        terminate(registration, AutomationControlLease.TerminationReason.ECHO_UNAVAILABLE, true);
        removeEmptyServerMap(server, serverLeases);
    }

    /**
     * EchoPlayer lifecycle hook used once per server tick to clean up entities
     * removed by external code.
     */
    public static void maintainLeases(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        Map<UUID, Registration> serverLeases = AUTOMATION_LEASES.get(server);
        if (serverLeases == null) {
            return;
        }
        Iterator<Registration> iterator = serverLeases.values().iterator();
        while (iterator.hasNext()) {
            Registration registration = iterator.next();
            EchoServerPlayer echoPlayer = registration.echoPlayer;
            boolean invalidShell = echoPlayer.linkedRealPlayer != null;
            boolean removedWithoutDeath = echoPlayer.isRemoved() && !echoPlayer.isDeadOrDying();
            if (invalidShell || removedWithoutDeath) {
                iterator.remove();
                terminate(registration, AutomationControlLease.TerminationReason.ECHO_UNAVAILABLE, true);
            }
        }
        removeEmptyServerMap(server, serverLeases);
    }

    /**
     * EchoPlayer lifecycle hook that moves an active lease from a dead entity
     * instance to the replacement instance created by the vanilla respawn path.
     */
    public static void transferRespawnLease(EchoServerPlayer oldPlayer, EchoServerPlayer newPlayer) {
        Objects.requireNonNull(oldPlayer, "oldPlayer");
        Objects.requireNonNull(newPlayer, "newPlayer");
        MinecraftServer server = oldPlayer.server;
        requireServerThread(server);
        if (newPlayer.server != server || !oldPlayer.getUUID().equals(newPlayer.getUUID())) {
            throw new IllegalArgumentException("Respawned EchoPlayer must keep its server and UUID");
        }
        Map<UUID, Registration> serverLeases = AUTOMATION_LEASES.get(server);
        if (serverLeases == null) {
            return;
        }
        Registration registration = serverLeases.get(oldPlayer.getUUID());
        if (registration != null && registration.echoPlayer == oldPlayer && registration.lease.isActive()) {
            registration.echoPlayer = newPlayer;
        }
    }

    /**
     * EchoPlayer lifecycle hook used while a server is stopping.
     */
    public static void releaseServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        STOPPING_SERVERS.add(server);
        Map<UUID, Registration> serverLeases = AUTOMATION_LEASES.remove(server);
        if (serverLeases == null) {
            return;
        }
        for (Registration registration : serverLeases.values()) {
            terminate(registration, AutomationControlLease.TerminationReason.SERVER_STOPPING, true);
        }
        serverLeases.clear();
    }

    static void release(AutomationControlLease lease) {
        Objects.requireNonNull(lease, "lease");
        if (!lease.isActive()) {
            return;
        }
        MinecraftServer server = lease.server();
        requireServerThread(server);
        Map<UUID, Registration> serverLeases = AUTOMATION_LEASES.get(server);
        if (serverLeases != null) {
            Registration registration = serverLeases.get(lease.echoPlayerId());
            if (registration != null && registration.lease.token().equals(lease.token())) {
                serverLeases.remove(lease.echoPlayerId());
                removeEmptyServerMap(server, serverLeases);
            }
        }
        lease.terminate(AutomationControlLease.TerminationReason.RELEASED);
    }

    private static Registration findRegistration(EchoServerPlayer echoPlayer) {
        Map<UUID, Registration> serverLeases = AUTOMATION_LEASES.get(echoPlayer.server);
        if (serverLeases == null) {
            return null;
        }
        Registration registration = serverLeases.get(echoPlayer.getUUID());
        return registration != null && registration.echoPlayer == echoPlayer && registration.lease.isActive()
            ? registration
            : null;
    }

    private static boolean isUsableEcho(EchoServerPlayer echoPlayer) {
        return echoPlayer.linkedRealPlayer == null && !echoPlayer.isRemoved() && !echoPlayer.isDeadOrDying();
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("EchoPlayer control API must be called on the Minecraft server thread");
        }
    }

    private static void terminate(
        Registration registration,
        AutomationControlLease.TerminationReason reason,
        boolean notify
    ) {
        if (!registration.lease.isActive()) {
            return;
        }
        registration.lease.terminate(reason);
        if (!notify) {
            return;
        }
        try {
            registration.listener.onLeaseTerminated(registration.lease, reason);
        } catch (Throwable error) {
            Constants.LOG.error("Automation controller {} failed while releasing EchoPlayer {}",
                registration.lease.controllerId(), registration.lease.echoPlayerId(), error);
        }
    }

    private static void removeEmptyServerMap(MinecraftServer server, Map<UUID, Registration> serverLeases) {
        if (serverLeases.isEmpty()) {
            AUTOMATION_LEASES.remove(server);
        }
    }

    public enum ControlMode {
        IDLE,
        POSSESSED,
        AUTOMATED,
        UNAVAILABLE
    }

    /** Result of asking the current automation registration to yield for possession. */
    public enum PossessionPreemptionResult {
        NOT_AUTOMATED,
        SUSPENDED,
        ALREADY_SUSPENDED,
        DENIED
    }

    private static final class Registration {
        private EchoServerPlayer echoPlayer;
        private final AutomationControlLease lease;
        private final AutomationControlListener listener;
        private UUID possessorId;

        private Registration(
            EchoServerPlayer echoPlayer,
            AutomationControlLease lease,
            AutomationControlListener listener
        ) {
            this.echoPlayer = echoPlayer;
            this.lease = lease;
            this.listener = listener;
        }
    }
}
