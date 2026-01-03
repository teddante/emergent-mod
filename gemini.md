# AI Development Guidelines

1. **Philosophy**: Build dynamic, system-driven interactions that foster emergent gameplay. Features should interact with each other and the environment unpredictably but logically.
2. **Native Integration**: Always use existing vanilla functions, lists, and mechanics (e.g., Tags, `isBurnable`). Do not reinvent base game logic; extend it naturally.
3. **Compatibility**: Ensure maximum compatibility with Vanilla and other mods. Use non-destructive Mixins (`@Inject`) and data-driven Tags.
4. **Quality**: Use optimal programming paradigms and design patterns. Code must be clean, modular, and performant.
5. **Source Code Access**: Do not guess method names or rely solely on online searches. The project includes generated Minecraft source code.
    - **Verify Signatures**: Always verify method signatures and mapping names by checking the generated sources or using `javap`.
    - **Generate Sources**: If sources are missing, run `./gradlew genSources` to generate them locally. This provides the correct Yarn-mapped code for the specific project version.
    - **Locate Sources**: Generated sources are stored in the Gradle cache (e.g., `~/.gradle/caches/fabric-loom/.../minecraft-merged-...-sources.jar`). Identifying and reading this JAR is the canonical way to reference vanilla code.
