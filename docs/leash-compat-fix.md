# Leash compatibility fixes (1.0.1)

This patch keeps leash identity tied to the visible body while a real player is possessing an EchoPlayer.

## Vanilla leads

- Mob leash creation already projected the authenticated controller to the possessed EchoPlayer.
- Unleashing now applies the same projection to vanilla's holder identity comparison.
- `LeadItem.bindPlayerMobs` now searches using the visible EchoPlayer, so mobs held by a possessed EchoPlayer can be attached to a fence knot.

## Player Collars

Player Collars stores two pieces of leash state for players: a private holder field mixed into `ServerPlayer`, and a hidden vanilla-mob proxy used for rendering and fence knots. Both now migrate together.

- Before PlayerCollars performs its direct `holder == player` detach check, EchoPlayer temporarily exposes the authenticated controller in the private comparison field while leaving the visible proxy attached to the controlled EchoPlayer.
- Fence binding works through the same visible-holder projection as vanilla mobs, so PlayerCollars proxy mobs are found by `LeadItem.bindPlayerMobs`.
- When possession creates the original-body Shell, PlayerCollars holder references are transferred from the real body to the Shell.
- If the body itself is leashed, its PlayerCollars target state is moved to the Shell without dropping a lead.
- Immediately before leaving possession, any just-created PlayerCollars links that still name the authenticated controller are normalized to the controlled EchoPlayer.
- On unpossess, holder references and leashed-target state are moved from the Shell back to the real body before the Shell is removed. This prevents PlayerCollars from seeing a dead/removed holder on the next tick and dropping or losing the lead.

PlayerCollars remains optional; the compatibility bridge uses reflection and becomes a no-op when the mod is absent.
