# Changelog

This project uses human-readable release notes with GitHub's generated release notes as the release packaging helper.

## Unreleased

Target version: `0.1.0`.

### Added

- GitHub issue forms, PR template, dependency graph workflow, and tag-driven release workflow.
- Cross-platform smoke checks for CI and local development.

### Changed

- CI uses Java 25 and Fabric Loom 1.15.5 for Minecraft 26.1.x development.
- Repository guidance now documents Codex-owned branch, PR, issue, tag, and release workflows when the user explicitly asks for that lifecycle.
- Development version reset to `0.1.0` because Emergent is still in initial public-preview hardening, not a stable `1.0.0` contract.

### Fixed

- Hardened mixin and resource validation to catch multi-target mixin helper issues and stale vanilla IDs before release.
- Smoke checks now derive the release jar name from `gradle.properties` instead of assuming `emergent-1.0.0.jar`.
