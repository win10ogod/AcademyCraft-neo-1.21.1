# AcademyCraft NeoForge porting notes

## Source baseline

AcademyCraft **1.1.3 / Forge 1.12.2** in the checked-out working tree is the primary source for the game implementation, key contexts, UI XML layouts, models, sounds and visual behaviour. Git tag `1.0.7` (`build.properties` reports `mc_ver = 1.7.10`) is used only to cross-check stable IDs, retained gameplay and balancing history. Old-world compatibility is not part of this port.

## Implemented in this port

- Java 21, Gradle 9, ModDevGradle, NeoForge 21.1 and Minecraft 1.21.1 project setup.
- Stable `academy:*` IDs for all 34 original items and 21 original blocks.
- Deferred block, item, fluid, entity, sound, data-component, block-entity and creative-tab registration.
- All four ability categories and all 50 original skill-tree entries (including generic passive courses).
- Persistent, server-authoritative category, developer-gated level/skill progression, skill experience, four independent four-key presets, CP, overload lockout, cooldown, terminal, app and named teleport-location state.
- NeoForge custom-payload synchronization, validated server-side actions, key mappings and a CP/overload/preset HUD.
- Playable implementations for every controllable Electromaster, Meltdowner, Teleporter and Vector Manipulation skill.
- Holographic Data Terminal with original app icons/sounds, install animation and preinstalled About app; graphical skill tree, developer interface, named Location Teleport interface, research-gated MisakaCloud reader with inline images/item previews, and media player.
- Induction-factor, three-tier matrix-core, matter-unit, media and energy state migrated from item metadata/NBT to data components; Matter Units also expose NeoForge fluid capabilities.
- FE-compatible energy units and powered machine block entities, sided adjacent transfer, five-slot machine menus, fluid tanks, and password-authenticated wireless matrix/node/user networks.
- Rain-aware solar, 8–40-pillar wind, fluid-burning phase and cat generators; automated Imag Fusor; four-mode Metal Former; portable/normal/advanced developers; assembled matrix tiers; and Ability Interferer.
- Native Imag Phase flowing fluid, world generation for four ores and underground phase lakes.
- Throwable silicon barn and magnetic-hook entities, coin flipping and railgun reagent handling.
- Modern recipes, loot tables, chest loot injection, block/tool tags and advancement tree.
- Six legacy translations converted from `.lang` to UTF-8 JSON; all legacy sounds and visual assets retained, with animated machine/node/item textures, grouped OBJ renderers and a modern core GLSL energy shader.
- Operator commands under `/academy` for status, category, level, learning and recovery, plus `/aim`, `/aimp` and `/acach` compatibility aliases.
- Native NeoForge configuration and JUnit tests running inside the NeoForge unit-test environment.

## Intentional architecture replacements

- ForgeGradle 2 / Java 8 -> ModDevGradle / Java 21.
- LambdaLib2 registry and data parts -> deferred registers and persistent player NBT.
- LambdaLib2 contexts -> compact server-side skill executors and custom payload actions.
- `SimpleNetworkWrapper` -> NeoForge payload registration.
- Item metadata -> data components.
- Ore Dictionary -> vanilla/NeoForge tags.
- Legacy custom recipe scripts -> normal data-pack recipes.
- Legacy Scala/OpenGL/LambdaLib GUI code -> native Java screens and `GuiGraphics`.
- Legacy RF/IC2 bridge internals -> standard NeoForge FE capabilities.

## Compatibility and architecture scope

Legacy 1.7.10/1.12.2 save conversion is explicitly outside this port's scope because the world and LambdaLib player-data formats differ too substantially. Start a new 1.21.1 world; stable `academy:*` registry IDs are still retained for resource-pack and command compatibility.

- The 1.12.2 key-context lifecycle is restored with server-authoritative key-down, per-tick consumption, key-up, abort and toggle phases. Charge thresholds, continuous effects, mouse-wheel teleport distance and context cooldowns are handled without LambdaLib.
- Legacy fixed-pipeline visuals are implemented through grouped Wavefront rendering, original sprites, custom particles, additive world effects and a NeoForge core shader rather than removed OpenGL APIs.
- IC2 EU, CraftTweaker and JEI integrations are not hard dependencies; standard FE and vanilla recipes are used.
- Analytics/upload code was intentionally not restored.

## Verification

The following checks pass with Java 21:

```bash
./gradlew clean test build
```

A dedicated NeoForge server was started through world creation, registry/data-pack loading and `Done`; an Xvfb client was started through resource reload and title-screen initialization with no AcademyCraft model, texture, registry or payload errors.
