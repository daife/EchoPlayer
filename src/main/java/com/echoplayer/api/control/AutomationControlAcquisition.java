package com.echoplayer.api.control;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of an automation control request.
 */
public final class AutomationControlAcquisition {
    private final Status status;
    private final AutomationControlLease lease;

    private AutomationControlAcquisition(Status status, AutomationControlLease lease) {
        this.status = Objects.requireNonNull(status, "status");
        this.lease = lease;
    }

    static AutomationControlAcquisition acquired(AutomationControlLease lease) {
        return new AutomationControlAcquisition(Status.ACQUIRED, Objects.requireNonNull(lease, "lease"));
    }

    static AutomationControlAcquisition rejected(Status status) {
        if (status == Status.ACQUIRED) {
            throw new IllegalArgumentException("An acquired result requires a lease");
        }
        return new AutomationControlAcquisition(status, null);
    }

    public Status status() {
        return this.status;
    }

    public Optional<AutomationControlLease> lease() {
        return Optional.ofNullable(this.lease);
    }

    public enum Status {
        ACQUIRED,
        POSSESSED,
        ALREADY_AUTOMATED,
        ECHO_UNAVAILABLE
    }
}
