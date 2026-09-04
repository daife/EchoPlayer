package com.echoplayer.api.control;

import java.util.UUID;

/**
 * Receives involuntary automation lease termination on the server thread.
 * Normal EchoPlayer death and respawn do not terminate a lease.
 */
@FunctionalInterface
public interface AutomationControlListener {
    /**
     * Called before an authorized player takes control from a preemptible lease.
     * The implementation must synchronously stop all automation input before returning {@code true}.
     */
    default boolean onPossessionPreempting(AutomationControlLease lease, UUID possessorId) {
        return false;
    }

    /** Called after possession ends and the still-active lease regains control. */
    default void onPossessionReleased(AutomationControlLease lease, UUID possessorId) {
    }

    void onLeaseTerminated(AutomationControlLease lease, AutomationControlLease.TerminationReason reason);
}
