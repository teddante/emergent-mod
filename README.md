# Emergent

A Minecraft Fabric mod that makes world systems interact in broader, more physical ways. Fire, water, rain, explosions, mobs, inventories, brewing, enchanting, and terrain all become more dynamic while staying configurable.

## Features

### Volatile Explosives

Explosive item stacks can detonate when exposed to explosions, fire, lava, lightning, burning entities, or other configured triggers.

- Dropped explosives can chain react.
- Explosive items inside inventories or containers can detonate.
- Nested container items, such as shulker boxes filled with TNT, are inspected too.
- Explosion strength scales with the quantity and category of explosives.

Example:

- 1 gunpowder = tiny pop.
- 1 TNT = vanilla-ish TNT blast.
- 64 TNT = power 16, roughly 4x TNT radius.

Tags control what counts as an explosive:

- `#emergent:low_explosives`
- `#emergent:explosives`
- `#emergent:high_explosives`

### Reactive Creepers

Creepers can react to nearby explosions instead of ignoring them.

### Infinite Fire Spread

Fire can keep spreading beyond vanilla limits when enabled. Placement still uses vanilla fire placement state selection so dimension behavior and modded hooks have a chance to participate.

### Burning Entity Fire Spread

Burning entities can ignite nearby valid blocks as they move.

### Universal Warden Summoning

Warden summoning behavior can be widened beyond vanilla's default restrictions.

### Finite Water Flow

Water sources can be prevented from regenerating infinitely, making water flow more physical.

### Rain Accumulation

Rain can accumulate water in exposed spaces.

### Hydraulic Erosion

Flowing water can erode vulnerable blocks over time. Erosion is driven by block tags and vanilla material properties where possible.

### Auto Planting

Dropped seeds and plantable items can eventually plant themselves when resting on valid soil. Failed attempts are throttled so invalid piles do not retry every tick forever.

### Material Reactions

Rain and water can interact with tagged materials.

- Rain can oxidize copper-like blocks.
- Rain can grow exposed tagged plants when the plant actually supports the growth operation.
- Water can wash away tagged blocks.
- Brittle blocks can shatter under erosion.

### Boundless Enchanting

Optional enchanting changes can remove or relax selected vanilla limits.

### Boundless Brewing

Optional brewing changes can extend potion amplifier or duration limits. Brewing stops matching once an effect is already at the configured cap, so ingredients are not consumed for no effect.

## Configuration

The config is generated at:

```text
config/emergent.json5
```

Example:

```json5
{
  "volatileContainers": true,
  "volatileDroppedItems": true,
  "volatileInventories": true,
  "reactiveCreepers": true,
  "infiniteFireSpread": true,
  "burningEntityFireSpread": true,
  "universalWardenSummoning": true,
  "finiteWaterFlow": true,
  "rainAccumulation": true,
  "hydraulicErosion": true,
  "autoPlanting": true,
  "materialReactions": true,
  "boundlessEnchanting": true,
  "unrestrictedEnchantments": true,
  "boundlessBrewing": true
}
```

## Tags

Most categorical behavior is tag-driven for compatibility with vanilla and other mods.

Common tag paths:

```text
data/emergent/tags/item/low_explosives.json
data/emergent/tags/item/explosives.json
data/emergent/tags/item/high_explosives.json
data/emergent/tags/block/brittle.json
data/emergent/tags/block/washes_away_in_water.json
data/emergent/tags/block/rain_grows.json
```

## Compatibility

Emergent prefers vanilla APIs, block tags, item tags, block states, inventories, and standard placement/growth hooks instead of hardcoded block names.

Mixin compatibility:

- Most features use non-destructive injections where practical.
- A narrow overwrite is currently used for water source conversion because the behavior must change at the exact point vanilla decides whether flowing water becomes a source.
- Container reactions use the generic `Container` interface when available, so vanilla and modded inventories are supported more broadly than chest-like block entities only.

## Building

```powershell
./gradlew.bat build
```

## License

This project is provided under the repository's license.
