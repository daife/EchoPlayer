# Possession routing history

## Correct line

The validated base is `a166164` (`feat: support logical sleep during possession`).
Gameplay packets continue to run as the authenticated `ServerPlayer`; the
existing possession synchronizer then projects that authoritative state onto
the EchoPlayer.

The following changes were replayed independently on top of that base:

- follower owner resolution, without adding `getGameplayPlayer`;
- dead-code and stale-refmap cleanup;
- the CodeGraph repository guidance.

## Rejected line

The old line is retained by the `archive/broken-gameplay-routing` branch for
diagnosis. Do not merge or cherry-pick it as a range into the correct line.

- `f96e995` redirects broad `ServerGamePacketListenerImpl.player` reads to the
  EchoPlayer. This mixes an authenticated connection with a fake player
  connection and conflicts with the real-player-to-echo synchronization model.
- `1dc08e6` extends that model to Forge `NetworkEvent.Context#getSender` and
  fake-connection packet forwarding, so it is intentionally excluded as well.
- `bc4c80b` was developed downstream of those routing changes. It is not
  included; any useful item-authority behavior must be re-evaluated and
  implemented independently against the correct line.
