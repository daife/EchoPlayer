# EchoPlayer

Summon persistent virtual player echoes, possess them directly, and hand their
control over to automation or AI systems without running additional game
clients.

## Features

- Spawn and remove persistent EchoPlayers.
- Possess an EchoPlayer and control it from your current client.
- Keep player state, inventory, effects, movement, combat, and interactions in sync.
- Preserve and restore Curios inventories across possession, including Player Collars ownership.
- Change EchoPlayer skins by player name or URL.
- Restrict control to each EchoPlayer's creator by default, with an option to allow other players.

The root command is `/echoplayer`.

## Automation control API

Add-on mods must acquire an exclusive automation lease before controlling an
EchoPlayer. `EchoPlayerControlApi.tryAcquireAutomation(...)` rejects a target
that is possessed, unavailable, or already leased. While the lease is active,
EchoPlayer rejects possession of that target. Close the returned
`AutomationControlLease` on the Minecraft server thread on every normal exit
path.

An optional `AutomationControlListener` is invoked on the server thread when
EchoPlayer revokes a lease because the target is removed, is unloaded outside
the normal death flow, or the server stops. A lease survives death and is moved
to the respawned EchoPlayer instance with the same UUID. Add-ons should pause
actions while that instance is unavailable and resume or replan after respawn.
The API arbitrates control only; add-ons remain responsible for checking the
requesting user's authorization before acquiring a lease.

## Controls

- Press `O` to run `/echoplayer unpossess` immediately.
- Hold `Left Alt` to open the possession wheel, point at your real player or an
  EchoPlayer, and release the key to switch. Release while the pointer is in the
  center to cancel. Both controls can be rebound in Minecraft's key settings.

## Development

- Minecraft: 1.20.1
- Forge: 47.4.22 (latest release for Minecraft 1.20.1)
- Java: 17
- Mappings: Parchment 2023.08.20 for Minecraft 1.20.1

Build with JDK 17 and the included Gradle Wrapper:

```powershell
.\gradlew.bat clean build --console=plain
```

The compiled mod and sources JARs are written to `build/libs`.
