# Minecraft 26.1 Modernization Notes

Emergent now targets Minecraft 26.1.2. Fabric's 26.1 migration changes are unusually large because Minecraft 26.1+ is distributed unobfuscated and Fabric no longer uses Yarn mappings as the normal target for current versions.

## Build Tooling

- `net.fabricmc.fabric-loom-remap` has been replaced with `net.fabricmc.fabric-loom`.
- Yarn mappings were removed from `build.gradle`.
- `modImplementation` dependencies were changed to Gradle's standard `implementation` configuration.
- Java and Mixin compatibility levels were raised from 21 to 25.
- Gradle wrapper metadata now points at Gradle 9.4.0.

Current target versions:

- Minecraft: `26.1.2`
- Fabric Loader: `0.19.2`
- Fabric API: `0.146.1+26.1.2`
- Loom: `1.15.5`

## User Toggles

Feature flags live in `config/emergent.json` and are generated on first launch. Every gameplay system has its own boolean so players and server owners can keep the interactions they like and disable the ones that do not fit a world or modpack.

The runtime config remains a plain JSON file for server and manual use. When Mod Menu and Cloth Config are installed, the client exposes the same broad feature gates in game without changing the server-side behavior gates.

## Porting Checklist

Before a release, verify mixin target signatures against freshly extracted 26.1.2 sources:

1. Install JDK 25 and use it for Gradle.
2. Run `.\gradlew.bat genSources`.
3. Run `.\scripts\extract_sources.ps1`.
4. Re-check all mixin `method` names and descriptors in `mc-src`.
5. Run `.\gradlew.bat build`.

The current migration compiles against Minecraft 26.1.2 with JDK 25. Runtime smoke testing should still cover each mixin-heavy feature, because successful compilation does not prove every injection point is semantically equivalent to the old Yarn-era target.
