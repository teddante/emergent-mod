# AI Development Guidelines

1. **Philosophy**: Build dynamic, system-driven interactions that foster emergent gameplay. Features should interact with each other and the environment unpredictably but logically. If you do something and you expect it to happen as per logic or real life, it should happen. This is what the mod is going for.
2. **Native Integration**: Always use existing vanilla functions, lists, and mechanics.
    - **Use Tags**: Never use hardcoded strings (e.g., `name.contains("tnt")`). Use Item Tags (`#emergent:high_explosives`) for all categorical checks. This ensures compatibility with other mods.
    - **Extend, Don't Reinvent**: Use vanilla `isBurnable`, `getBlastResistance`, etc.
3. **Compatibility**: Ensure maximum compatibility with Vanilla and other mods.
    - Use non-destructive Mixins (`@Inject`) wherever possible.
    - Avoid `@Redirect` unless absolutely necessary (e.g., when a value must be changed *before* it is used in logic that runs in the same tick).
4. **Quality**: Use optimal programming paradigms and design patterns. Code must be clean, modular, and performant.
5. **Source Code Access**: Do not guess method names or rely solely on online searches. The project includes generated Minecraft source code.
    - **Local Source Cache**: Use the `mc-src` directory in the project root to inspect Minecraft source code. If it is empty or missing, run `scripts/extract_sources.ps1` to populate it.
    - **Verify Signatures**: Always verify method signatures and mapping names by checking `mc-src` or using `javap`.
    - **Generate Sources**: If sources are missing from the Gradle cache, run `./gradlew genSources` first, then run the extraction script.
6. **Configuration Integrity**: Ensure configuration files are synchronized with codebase changes.
    - **Mixin Config**: When deleting or renaming a Mixin class, immediately update `mixins.json` to remove or update the reference.
    - **Refmap**: Ensure `refmap` is defined in `mixins.json` to prevent runtime mapping errors.
    - **Entrypoints**: When renaming main classes or client entry points, update `fabric.mod.json`.

7. **GitHub Workflow**: Keep repository operations simple, reviewable, and release-ready.
    - **Agent Autonomy**: When explicitly asked, Codex may create/switch branches, stage files, commit, push, open PRs, triage/create issues, create version tags, and publish GitHub releases using the GitHub tools/CLI. Do the whole requested lifecycle cleanly instead of handing manual steps back to the user.
    - **Branches**: Work on short feature/fix/docs branches. Keep `main` buildable and use `release/<version>` only for release stabilization.
    - **Pull Requests**: Every PR should explain gameplay/config compatibility impact and list verification. Run `scripts/dev_smoke.ps1` before proposing changes. Prefer draft PRs for work that still needs in-game validation.
    - **Issues**: Prefer structured issues with Minecraft version, Fabric Loader/API versions, Emergent version, reproduction steps, logs, and mod list when relevant. Use issues for reproducible bugs and concrete feature ideas, not for every tiny internal cleanup.
    - **Versioning**: Use `MAJOR.MINOR.PATCH` in `gradle.properties` and annotated `vMAJOR.MINOR.PATCH` Git tags. Patch for fixes/tuning, minor for new configurable systems or meaningful behavior expansion, major for breaking config/data behavior or dropping a supported Minecraft line. Minecraft compatibility belongs in metadata/docs, not in the tag unless a future multi-loader/multi-Minecraft release policy requires it.
    - **Releases**: Release from annotated `vMAJOR.MINOR.PATCH` tags after `gradle.properties` `mod_version`, `CHANGELOG.md`, docs, and smoke checks are updated. Let the release workflow build the jar and generate release notes.
    - **CI**: GitHub Actions must use Java 25 for Minecraft 26.1.x and run the smoke checks rather than only Gradle compilation.
    - **Dependency/Source Hygiene**: Avoid snapshot tool versions in committed config unless deliberately testing snapshots. Prefer pinned stable tool versions for reproducible CI.
    - **Safety**: Never rewrite public history, force-push, delete branches, close issues, publish releases, or alter repository settings unless the user clearly asks for that exact action.

# Lessons Learned & Technical Specifics

### Vanilla Limitations
-   **No Explosive Values on Items**: Vanilla Minecraft *Items* (e.g., Gunpowder, TNT items) DO NOT have an "explosive power" property. The power is hardcoded in `TntBlock` or `TntEntity`.
    -   *Solution*: Create a custom Tag system (e.g., `emergent:high_explosives`) to assign values to items via JSON. Do not try to read this from code.

### Mixin Patterns
-   **State Updates**: If a Mixin updates a BlockState and subsequent logic relies on `getCachedState()`, you MUST manually call `this.setCachedState(newState)` (suppressing deprecation) to ensure consistency within the same tick. The World state update is not immediate enough for local field access.
-   **Collection Safety**: When iterating over lists that might be modified by the action you are performing (e.g., explosions triggering other explosions), ALWAYS iterate over a **copy** of the list to avoid `ConcurrentModificationException`.

### Environment
-   **Source Verification**: If you are unsure if a vanilla method exists, **extract the source JAR** and check. Do not hallucinate based on other versions.
