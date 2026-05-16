# Contributing to Emergent

Emergent is a system-driven Minecraft Fabric mod. Changes should make vanilla systems interact more logically without hardcoding narrow special cases.

## Branches

- `main` is the stable development branch.
- Use short feature branches such as `feature/fire-wetness`, `fix/minecart-momentum`, or `docs/github-workflow`.
- Keep release stabilization work on `release/<version>` only when a release needs more than one fix before tagging.

Codex may create branches, commits, PRs, tags, and releases for the project when explicitly asked. The expected style is focused and boring in the best way: one clear branch, one clear PR, smoke checks before review, and no surprise force-pushes or releases.

## Issues

Use issues for reproducible bugs and concrete feature proposals. A good issue includes:

- Minecraft version, Fabric Loader, Fabric API, and Emergent version.
- Steps to reproduce or a specific gameplay scenario.
- Logs/crash reports for crashes or startup failures.
- Other mods when compatibility may matter.

## Pull Requests

PRs should be small enough to review. Prefer one gameplay system or one maintenance task per PR.

Before opening a PR:

```powershell
.\scripts\dev_smoke.ps1
```

On GitHub Actions/Linux, the same script runs through PowerShell Core as `./scripts/dev_smoke.ps1`.

For gameplay changes, also test in-game with the smallest world/setup that exercises the behavior. Mention that scenario in the PR.

## Versioning

Use annotated release tags in the form `vMAJOR.MINOR.PATCH`, for example `v0.1.0`.

- Patch: crash fixes, compatibility fixes, small tuning.
- Minor: new configurable gameplay systems or notable behavior expansion.
- Major: breaking config/data behavior, large compatibility shifts, or dropping a supported Minecraft line.

Keep `gradle.properties` `mod_version` aligned with the release tag before tagging.

Minecraft compatibility is tracked in `fabric.mod.json`, README/docs, and release notes. Keep tags as `vMAJOR.MINOR.PATCH` unless the project later needs parallel Minecraft release trains.

## Releases

1. Update `mod_version` in `gradle.properties`.
2. Update `CHANGELOG.md`.
3. Run `.\scripts\dev_smoke.ps1`.
4. Merge to `main`.
5. Create and push a tag, for example:

```powershell
git tag -a v0.1.0 -m "v0.1.0"
git push origin v0.1.0
```

The release workflow builds the mod, runs the smoke checks, and creates a GitHub release with the compiled jar.

## Minecraft 26.1 Development Notes

- Use Java 25 for local Gradle and CI.
- Use Fabric Loom 1.15.x for Minecraft 26.1.x unless intentionally migrating after checking Fabric's current guidance.
- Use Mojang official names for code and local source verification.
- Prefer vanilla APIs, tags, block states, inventories, and events.
- Use `@Inject` where possible; avoid `@Redirect` unless the value must be changed before vanilla uses it.
- When changing mixins, tags, config keys, entrypoints, or release metadata, update the related manifests and docs in the same PR.
