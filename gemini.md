# AI Development Guidelines

1. **Philosophy**: Build dynamic, system-driven interactions that foster emergent gameplay. Features should interact with each other and the environment unpredictably but logically.
2. **Native Integration**: Always use existing vanilla functions, lists, and mechanics.
    - **Use Tags**: Never use hardcoded strings (e.g., `name.contains("tnt")`). Use Item Tags (`#emergent:high_explosives`) for all categorical checks. This ensures compatibility with other mods.
    - **Extend, Don't Reinvent**: Use vanilla `isBurnable`, `getBlastResistance`, etc.
3. **Compatibility**: Ensure maximum compatibility with Vanilla and other mods.
    - Use non-destructive Mixins (`@Inject`) wherever possible.
    - Avoid `@Redirect` unless absolutely necessary (e.g., when a value must be changed *before* it is used in logic that runs in the same tick).
4. **Quality**: Use optimal programming paradigms and design patterns. Code must be clean, modular, and performant.
5. **Source Code Access**: Do not guess method names or rely solely on online searches. The project includes generated Minecraft source code.
    - **Verify Signatures**: Always verify method signatures and mapping names by checking the generated sources or using `javap`.
    - **Generate Sources**: If sources are missing, run `./gradlew genSources` to generate them locally. This provides the correct Yarn-mapped code for the specific project version.
    - **Locate Sources**: Generated sources are stored in the Gradle cache (e.g., `~/.gradle/caches/fabric-loom/.../minecraft-merged-...-sources.jar`). Identifying and reading this JAR is the canonical way to reference vanilla code.
6. **Configuration Integrity**: Ensure configuration files are synchronized with codebase changes.
    - **Mixin Config**: When deleting or renaming a Mixin class, immediately update `mixins.json` to remove or update the reference.
    - **Refmap**: Ensure `refmap` is defined in `mixins.json` to prevent runtime mapping errors.
    - **Entrypoints**: When renaming main classes or client entry points, update `fabric.mod.json`.

# Lessons Learned & Technical Specifics

### Vanilla Limitations
-   **No Explosive Values on Items**: Vanilla Minecraft *Items* (e.g., Gunpowder, TNT items) DO NOT have an "explosive power" property. The power is hardcoded in `TntBlock` or `TntEntity`.
    -   *Solution*: Create a custom Tag system (e.g., `emergent:high_explosives`) to assign values to items via JSON. Do not try to read this from code.

### Mixin Patterns
-   **State Updates**: If a Mixin updates a BlockState and subsequent logic relies on `getCachedState()`, you MUST manually call `this.setCachedState(newState)` (suppressing deprecation) to ensure consistency within the same tick. The World state update is not immediate enough for local field access.
-   **Collection Safety**: When iterating over lists that might be modified by the action you are performing (e.g., explosions triggering other explosions), ALWAYS iterate over a **copy** of the list to avoid `ConcurrentModificationException`.

### Environment
-   **Source Verification**: If you are unsure if a vanilla method exists, **extract the source JAR** and check. Do not hallucinate based on other versions.
