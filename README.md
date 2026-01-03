# Emergent

**Emergent** is a Minecraft mod designed to make the game world feel more dynamic, dangerous, and unpredictable. It tweaks core mechanics to encourage emergent gameplay scenarios where the environment reacts in ways that can spiral out of control if not managed.

## Features

### 🔥 Infinite Fire Spread
Fire no longer "dies of old age" when spreading.
- **Vanilla Behavior**: Fire has an `age` property (0-15). As it spreads, new fire gets an older age. Once it reaches 15, it stops spreading. Behavior: Fire patches eventually burn themselves out.
- **Emergent Behavior**: Fire always spreads as if it were "new" (age 0).
    - **Result**: Fire can spread indefinitely across forests and flammable structures.
    - **Balance**: Fire blocks still age and burnout naturally, so you won't be left with "eternal fire" blocks, but the *front* of the fire will keep moving as long as there is fuel.

### 🔥 Burning Entity Fire Spread
Entities on fire spread flames to flammable blocks they touch.
- **Vanilla Behavior**: When a mob, player, or entity is burning, they take fire damage but do not ignite their surroundings. A burning zombie can walk through a wooden house without setting it ablaze.
- **Emergent Behavior**: **Burning entities ignite flammable blocks they touch.**
    - **Result**: A creeper that walked through lava will set your wooden base on fire. Flaming arrows aren't the only way to start fires anymore.
    - **Mechanic**: The mod checks if burning entities are touching air blocks adjacent to flammable materials and randomly ignites them. Fire spreads naturally from there using vanilla mechanics (enhanced by Infinite Fire Spread above).
    - **Balance**: Fire spread from entities has a cooldown and random chance to prevent instant infernos, making it feel natural rather than explosive.

### 🔊 Universal Warden Summoning
Any Sculk Shrieker can now summon a Warden.
- **Vanilla Behavior**: Only naturally generated Sculk Shriekers (placed during world generation) can summon Wardens. Player-placed or Catalyst-generated shriekers are decoration only.
- **Emergent Behavior**: **All** shriekers can summon Wardens.
    - **Result**: Players must be extremely careful when handling Sculk. Accidentally creating a shrieker via a Catalyst can lead to a Warden summoning in your base.
    - **Mechanic**: The mod forces the `can_summon` check to always be true.

## Compatibility

Emergent is designed for **maximum compatibility** with vanilla Minecraft and other mods.

- **Vanilla-First Design**: All features use standard Minecraft APIs and block/entity methods. Fire spreads using vanilla fire blocks, flammability is checked using vanilla `isBurnable()`, and block placement uses standard `setBlockState()`.
- **Non-Destructive Mixins**: The mod uses `@Inject` injections rather than `@Overwrite`, meaning it adds behavior without replacing vanilla code. Other mods targeting the same methods will work alongside Emergent.
- **Modded Content Support**: Custom blocks from other mods that define proper flammability will automatically work with fire spread. Custom entities that extend Minecraft's Entity class will inherit fire-spreading behavior when burning.
- **Performance Mods**: Fully compatible with optimization mods like Lithium, Sodium, and similar performance enhancers.

If you encounter compatibility issues with a specific mod, please [open an issue](https://github.com/teddante/emergent-mod/issues).

## Installation

1.  Download the latest `.jar` from the [Releases](https://github.com/teddante/emergent-mod/releases) page.
2.  Install [Fabric Loader](https://fabricmc.net/) for Minecraft 1.21.1.
3.  Place the `.jar` and [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods` folder.

## Building from Source

This project uses Gradle.

1.  Clone the repository.
2.  Run `./gradlew build` (Linux/Mac) or `gradlew build` (Windows).
3.  The compiled jar will be in `build/libs/`.

## License

This project is licensed under the **MIT License**. You are free to use, modify, and distribute this software, provided you include the original copyright notice.
