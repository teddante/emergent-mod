# Versioning

Emergent uses SemVer-style versions:

```text
MAJOR.MINOR.PATCH
```

Git tags add a leading `v`:

```text
v0.1.0
```

## Current Line

The current development version is `0.1.0`.

This is intentionally not `1.0.0` yet. Emergent is still in public-preview hardening: physics, fire behavior, config defaults, and compatibility expectations are still being tuned. Reaching `1.0.0` should mean the project is ready to treat its config/data behavior and main gameplay promises as stable enough for ordinary users to depend on.

## Bump Rules

- Patch: crash fixes, compatibility fixes, small tuning, documentation/process fixes.
- Minor: new configurable gameplay systems or meaningful behavior expansion.
- Major: breaking config/data behavior, large compatibility shifts, or dropping a supported Minecraft line.

For `0.x` releases, minor bumps may still include behavior changes while the mod is stabilizing. Call those out clearly in `CHANGELOG.md` and release notes.

## Minecraft Compatibility

Minecraft compatibility is tracked in `fabric.mod.json`, README/docs, and release notes. Do not put the Minecraft version into tags unless the project later intentionally supports parallel release trains.

## Release Checklist

1. Decide the next version using the bump rules.
2. Update `mod_version` in `gradle.properties`.
3. Update `CHANGELOG.md`.
4. Run `.\scripts\dev_smoke.ps1`.
5. Merge the release PR to `main`.
6. Create an annotated tag:

```powershell
git tag -a v0.1.0 -m "v0.1.0"
git push origin v0.1.0
```

The GitHub release workflow builds the jar, runs smoke checks, and publishes a release with generated notes.
