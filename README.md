# Emergent

**Emergent** is a Minecraft mod designed to make the game world feel more dynamic, dangerous, and unpredictable. It tweaks core mechanics to encourage emergent gameplay scenarios where the environment reacts in ways that can spiral out of control if not managed.

## Features

### 💥 Volatile Containers
Containers with explosives detonate when caught in explosions or fire.
- **Vanilla Behavior**: When a chest full of TNT is destroyed by an explosion, the TNT items simply drop on the ground.
- **Emergent Behavior**: **Explosive items inside containers detonate when the container is destroyed by explosions or fire.**
    - **Result**: Chain reactions! A single explosion can trigger nearby chests, barrels, or hoppers containing TNT to explode, which can trigger more containers, and so on.
    - **Mechanic**: The mod checks for containers in explosion blast zones and containers targeted by fire spread. If they contain volatile items (TNT, End Crystals, Gunpowder, Fire Charges), they explode with power based on their contents.
    - **Physics**: Explosion power scales with the cube root of total explosive mass (realistic blast physics). 64 TNT = power 16 (~4× the radius of a single TNT).

### 💥 Volatile Inventory
Entities carrying explosives detonate when damaged by fire or explosions.
- **Vanilla Behavior**: If a player carrying TNT in their inventory catches fire, the TNT is unaffected.
- **Emergent Behavior**: **Fire and explosion damage triggers volatile items in entity inventories.**
    - **Result**: Players and mobs carrying explosives become walking bombs when exposed to fire or caught in blasts.
    - **Mechanic**: Checks player inventories and mob equipment slots for volatile items when they take fire/explosion damage.

### 💥 Reactive Creepers
Creepers caught in explosions immediately explode.
- **Vanilla Behavior**: Creepers take damage from explosions like any other mob but don't react specially.
- **Emergent Behavior**: **Creepers instantly detonate when damaged by an explosion.**
    - **Result**: Creeper chain reactions! One explosion near a group of creepers causes a devastating cascade.
    - **Mechanic**: Uses a recursion guard to prevent infinite loops while still allowing chain reactions.

### 🔥 Infinite Fire Spread
Fire no longer "dies of old age" when spreading.
- **Vanilla Behavior**: Fire has an `age` property (0-15). As it spreads, new fire gets an older age. Once it reaches 15, it stops spreading.
- **Emergent Behavior**: Fire always spreads as if it were "new" (age 0).
    - **Result**: Fire can spread indefinitely across forests and flammable structures.
    - **Balance**: Fire blocks still age and burnout naturally, but the *front* of the fire keeps moving as long as there is fuel.

### 🔥 Burning Entity Fire Spread
Entities on fire spread flames to flammable blocks they touch.
- **Vanilla Behavior**: Burning mobs do not ignite their surroundings. A burning zombie can walk through a wooden house without setting it ablaze.
- **Emergent Behavior**: **Burning entities ignite flammable blocks they touch.**
    - **Result**: A creeper that walked through lava will set your wooden base on fire.
    - **Balance**: Fire spread from entities has a random chance to prevent instant infernos.

### 🔊 Universal Warden Summoning
Any Sculk Shrieker can now summon a Warden.
- **Vanilla Behavior**: Only naturally generated Sculk Shriekers can summon Wardens. Player-placed or Catalyst-generated shriekers are decoration only.
- **Emergent Behavior**: **All** shriekers can summon Wardens.
    - **Result**: Players must be extremely careful when handling Sculk. Accidentally creating a shrieker via a Catalyst can lead to a Warden summoning in your base.

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
