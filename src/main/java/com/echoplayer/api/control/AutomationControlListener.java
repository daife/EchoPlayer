package com.echoplayer.api.control;

/**
 * Receives involuntary automation lease termination on the server thread.
 * Normal EchoPlayer death and respawn do not terminate a lease.
 */
@FunctionalInterface
public interface AutomationControlListener {
    void onLeaseTerminated(AutomationControlLease lease, AutomationControlLease.TerminationReason reason);
}
