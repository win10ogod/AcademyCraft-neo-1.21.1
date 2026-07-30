# AcademyCraft NeoForge 1.21.1

A Java 21 / NeoForge 1.21.1 port of AcademyCraft, based primarily on AcademyCraft 1.1.3 for Minecraft 1.12.2. The older `1.0.7` tag is used only to cross-check stable IDs and balancing history.

## Build

```bash
./gradlew clean test build
```

Minecraft 1.21.1 requires Java 21. The resulting mod jar is written to `build/libs`.

## Installation

Install NeoForge 21.1.242 or newer for Minecraft 1.21.1, then place
`AcademyCraft-neo-1.21.1-2.0.0.jar` in the instance's `mods` directory. Clients and servers
must use the same jar. Create a new world; legacy saves are not converted.

## Port status

The port keeps the original `academy` namespace and registry identifiers for stable resource-pack and command names. Legacy 1.7.10/1.12.2 saves are intentionally unsupported; use a new 1.21.1 world. See [`PORTING.md`](PORTING.md) for implemented systems and architecture notes.

The original AcademyCraft license and its additional non-commercial restrictions still apply.
