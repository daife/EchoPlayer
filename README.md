# EchoPlayer

Summon persistent virtual player echoes, possess them directly, and hand their
control over to automation or AI systems without running additional game
clients.

## Features

- Spawn and remove persistent EchoPlayers.
- Possess an EchoPlayer and control it from your current client.
- Keep player state, inventory, effects, movement, combat, and interactions in sync.
- Change EchoPlayer skins by player name or URL.
- Restrict control to each EchoPlayer's creator by default, with an option to allow other players.

The root command is `/echoplayer`.

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
