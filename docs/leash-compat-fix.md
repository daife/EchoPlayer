# Leash compatibility fix 1.0.2

## Crash diagnosis from real 1.20.1 Forge environment

The 1.0.1 attempt used a `@Redirect` inside `Mob.checkAndHandleImportantInteractions` and expected an invocation of `Mob.getLeashHolder()`. In the supplied Forge 47.4.23 runtime that redirect matched zero instructions, so the required injection failed during bootstrap.

1.0.2 removes that internal redirect completely. Vanilla right-click-to-unleash support now injects at `Mob.interact` HEAD and only handles the possession-specific case where the authenticated player differs from the visible Echo leash holder. This depends on the stable public interaction entry point rather than a particular instruction sequence inside a private vanilla method.

## Released PlayerCollars 1.2.6 Forge jar

The supplied release jar was inspected directly. Its relevant implementation still uses these mixin-added members on `ServerPlayer`:

- `leashplayers$holder`
- `leashplayers$lastage`
- `leashplayer$loyalty`
- `leashplayers$attach(Entity)`
- `leashplayers$detach()`
- `leashplayers$interact(Player, InteractionHand)`

Its detach test is an object-identity comparison (`leashplayers$holder == player`) plus the 20-tick cooldown. The release jar therefore matches the uploaded source on the leash path. The jar's `mods.toml` still reports `version="1.2.4"` even though the file/build is 1.2.6; this is PlayerCollars packaging metadata and is not used as a compatibility implementation detail here.

## Vanilla leads

- New leash holders continue to be projected from the authenticated controller to the visible controlled Echo by `MixinMobLeash`.
- Right-click-to-unleash now checks the visible holder at `Mob.interact` HEAD, then performs the same `dropLeash(true, !instabuild)` behavior as vanilla.
- Fence binding changes the `Player` argument of `LeadItem.bindPlayerMobs` to the visible controlled Echo, so both ordinary mobs and PlayerCollars proxy mobs can be found and rebound to a fence knot.

## PlayerCollars

PlayerCollars keeps a private logical holder on the target player and a hidden proxy mob carrying the vanilla leash. Both must move together.

- Before PlayerCollars performs its `holder == player` detach check, EchoPlayer temporarily exposes the authenticated controller in the private comparison field when the visible holder is the controlled Echo and the 20-tick cooldown is ready.
- When the body Shell is created, PlayerCollars holder references are transferred from the real body to the Shell along with vanilla proxy holders.
- If the replaced body itself is a PlayerCollars-leashed target, its private holder/loyalty state is recreated on the Shell without dropping a lead.
- Immediately before leaving a controlled Echo, any newly-created PlayerCollars links that still name the authenticated controller are normalized to the controlled Echo. This covers attaching and immediately unpossessing before PlayerCollars gets a tick to reconcile its proxy.
- On unpossess, references from Shell -> real body and the Shell's own PlayerCollars target state are restored before the Shell is removed, preventing the next PlayerCollars tick from treating a removed Shell as a dead holder and dropping the lead.

PlayerCollars remains optional. The compatibility bridge resolves these release-jar members reflectively and is a no-op if PlayerCollars is absent.
