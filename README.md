# EchoPlayer

Summon persistent virtual player echoes, possess them directly, and hand their
control over to automation or AI systems without running additional game
clients.

## Features

- Spawn and remove persistent EchoPlayers.
- Possess an EchoPlayer and control it from your current client.
- Keep player state, inventory, effects, movement, combat, and interactions in sync.
- Change EchoPlayer skins by player name or URL.
- Support multiple controllers through server configuration.

The root command is `/echoplayer`.

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
