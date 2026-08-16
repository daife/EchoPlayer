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
    private volatile TerminationReason terminationReason;

    AutomationControlLease(MinecraftServer server, UUID echoPlayerId, ResourceLocation controllerId, UUID token) {
        this.server = Objects.requireNonNull(server, "server");
        this.echoPlayerId = Objects.requireNonNull(echoPlayerId, "echoPlayerId");
        this.controllerId = Objects.requireNonNull(controllerId, "controllerId");
        this.token = Objects.requireNonNull(token, "token");
    }

    public UUID echoPlayerId() {
        return this.echoPlayerId;
    }

    public ResourceLocation controllerId() {
        return this.controllerId;
    }

    public boolean isActive() {
        return this.terminationReason == null;
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

    void terminate(TerminationReason reason) {
        if (this.terminationReason == null) {
            this.terminationReason = Objects.requireNonNull(reason, "reason");
        }
    }

    public enum TerminationReason {
        RELEASED,
        ECHO_UNAVAILABLE,
        SERVER_STOPPING
    }
}
