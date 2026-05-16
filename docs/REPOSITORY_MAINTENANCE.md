# Repository Maintenance

This project should stay lightweight, but not loose. GitHub should enforce the same basic discipline that local development already expects.

## Branches

- `main`: stable development branch. It should build and pass smoke checks.
- `feature/<short-name>`: new gameplay systems or compatibility work.
- `fix/<short-name>`: crashes, behavior bugs, compatibility failures, or test/smoke fixes.
- `docs/<short-name>`: documentation-only work.
- `release/<version>`: only when a release needs stabilization before tagging.

Codex can manage this flow when asked: create the branch, make the change, run smoke checks, commit, push, open a PR, and later tag/release after approval. Keep the process direct and visible; do not add extra automation layers unless they remove repeated real work.

Recommended `main` branch protection:

- Require pull requests before merging.
- Require the `Build and smoke check` status check.
- Require branches to be up to date before merging when several PRs are active.
- Block force pushes and deletions.
- Use squash or merge commits consistently; avoid rewriting public release history.

## Issues and Labels

Keep labels small and useful:

- `bug`
- `enhancement`
- `feature`
- `gameplay`
- `compatibility`
- `documentation`
- `build`
- `ci`
- `refactor`
- `ignore-for-release`

These labels match `.github/release.yml`, so generated GitHub release notes stay readable without manual sorting.

## Pull Requests

Every PR should answer three questions:

- What changed?
- How was it verified?
- What compatibility/config/world-save behavior could be affected?

For code changes, run:

```powershell
.\scripts\dev_smoke.ps1
```

On GitHub Actions/Linux, the workflow runs the same script with PowerShell Core.

For gameplay changes, also do a focused in-game smoke test and describe it in the PR.

When Codex opens a PR, prefer a draft PR unless the change has already been built, smoke checked, and is ready for review.

## Agent-Owned GitHub Work

When the user asks Codex to handle repository work, Codex may perform the GitHub lifecycle directly:

- create or switch to a focused branch
- stage only the intended files
- commit with a short imperative message
- push the branch
- open or update a PR
- create or update issues when tracking is useful
- create annotated release tags
- publish GitHub releases from those tags

Codex should not force-push, rewrite public history, delete branches, close issues, change repository settings, or publish a release unless the user clearly asks for that exact action.

## Releases

Use `MAJOR.MINOR.PATCH` in `gradle.properties` and release only from annotated `vMAJOR.MINOR.PATCH` tags.

- Patch: crash fixes, compatibility fixes, small tuning, documentation/process corrections.
- Minor: new configurable gameplay systems or notable behavior expansion.
- Major: breaking config/data behavior, large compatibility shifts, or dropping a supported Minecraft line.

Minecraft compatibility is tracked in `fabric.mod.json`, README/docs, and release notes. Do not put the Minecraft version into tags unless the project later intentionally supports parallel release trains that need that distinction.

1. Update `mod_version` in `gradle.properties`.
2. Update `CHANGELOG.md`.
3. Run `.\scripts\dev_smoke.ps1`.
4. Merge to `main`.
5. Create an annotated tag:

```powershell
git tag -a v0.1.0 -m "v0.1.0"
git push origin v0.1.0
```

The release workflow builds the jar, runs smoke checks, and creates a GitHub release using generated notes.

## Minecraft/Fabric Baseline

For Minecraft 26.1.x:

- Java 25 is the local and CI baseline.
- Gradle wrapper should stay on the Fabric-recommended 9.4.x line unless upgrading intentionally.
- Fabric Loom should stay on a stable 1.15.x release unless Fabric's current guidance changes.
- Use `net.fabricmc.fabric-loom` rather than older remapping plugin IDs.
- Use Mojang official names and verify signatures against local `mc-src` before editing mixins.

## Agent Workflow

Codex should keep changes small and branch/release aware:

- Prefer tag-driven/block-driven compatibility over hardcoded Minecraft IDs.
- Run `scripts/dev_smoke.ps1` after mixin, tag, config, or workflow changes.
- Update `AGENTS.md`, `README.md`, `CHANGELOG.md`, and GitHub templates when changing project process.
- Do not create releases, tags, or protected-branch changes without an explicit user request.
