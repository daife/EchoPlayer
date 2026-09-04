package com.echoplayer.api.control;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * An exclusive automation claim for one EchoPlayer.
 *
 * <p>The lease is deliberately not transferable. It must be closed on the
 * owning Minecraft server thread. A lease may also be revoked by EchoPlayer
 * when the target is removed or its server stops. Death and normal respawn do
 * not end the lease.</p>
 */
public final class AutomationControlLease implements AutoCloseable {
    private final MinecraftServer server;
    private final UUID echoPlayerId;
    private final ResourceLocation controllerId;
    private final UUID token;
    private final PossessionPreemptionPolicy possessionPreemptionPolicy;
    private volatile State state = State.ACTIVE;
    private volatile TerminationReason terminationReason;

    AutomationControlLease(
        MinecraftServer server,
        UUID echoPlayerId,
        ResourceLocation controllerId,
        UUID token,
        PossessionPreemptionPolicy possessionPreemptionPolicy
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.echoPlayerId = Objects.requireNonNull(echoPlayerId, "echoPlayerId");
        this.controllerId = Objects.requireNonNull(controllerId, "controllerId");
        this.token = Objects.requireNonNull(token, "token");
        this.possessionPreemptionPolicy = Objects.requireNonNull(
            possessionPreemptionPolicy, "possessionPreemptionPolicy");
    }

    public UUID echoPlayerId() {
        return this.echoPlayerId;
    }

    public ResourceLocation controllerId() {
        return this.controllerId;
    }

    public boolean isActive() {
        return this.state != State.TERMINATED;
    }

    /** Returns whether automation currently owns the writable control channels. */
    public boolean hasControl() {
        return this.state == State.ACTIVE;
    }

    /** Returns the lease lifecycle and current control-grant state. */
    public State state() {
        return this.state;
    }

    /**
     * Returns {@code null} while this lease is active.
     */
    public TerminationReason terminationReason() {
        return this.terminationReason;
    }

    @Override
    public void close() {
        EchoPlayerControlApi.release(this);
    }

    MinecraftServer server() {
        return this.server;
    }

    UUID token() {
        return this.token;
    }

    PossessionPreemptionPolicy possessionPreemptionPolicy() {
        return this.possessionPreemptionPolicy;
    }

    void suspendForPossession() {
        if (this.state == State.ACTIVE) {
            this.state = State.POSSESSION_SUSPENDED;
        }
    }

    void resumeAfterPossession() {
        if (this.state == State.POSSESSION_SUSPENDED) {
            this.state = State.ACTIVE;
        }
    }

    void terminate(TerminationReason reason) {
        if (this.terminationReason == null) {
            this.terminationReason = Objects.requireNonNull(reason, "reason");
            this.state = State.TERMINATED;
        }
    }

    /** Lifecycle and current writable-control state of this lease. */
    public enum State {
        ACTIVE,
        POSSESSION_SUSPENDED,
        TERMINATED
    }

    public enum TerminationReason {
        RELEASED,
        ECHO_UNAVAILABLE,
        SERVER_STOPPING
    }
}
