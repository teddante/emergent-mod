# Emergent

A Minecraft Fabric mod that makes world systems interact in broader, more physical ways. Fire, water, rain, explosions, mobs, inventories, brewing, enchanting, and terrain all become more dynamic while staying configurable.

## Features

### Volatile Explosives

Explosive item stacks can detonate when exposed to fire, lava, or explosion damage. Lightning and burning entities can trigger them indirectly when they apply those vanilla damage types.

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
- `#emergent:volatile_explosives`

### Reactive Creepers

Creepers can react to nearby explosions instead of ignoring them.

### Infinite Fire Spread

Fire can keep spreading beyond vanilla limits when enabled. Placement still uses vanilla fire placement state selection so dimension behavior and modded hooks have a chance to participate.

### Burning Entity Fire Spread

Burning entities can ignite nearby valid blocks as they move.

### Wetness Fire Dampening

Rain, waterlogged blocks, and nearby water reduce the chance that fire spreads or burning entities ignite a block. Vanilla flammability still decides what can burn, while local wetness changes how likely ignition is.

Grass-like surface blocks tagged with `#emergent:scorches_to_dirt_in_fire` have their own living moisture. When fire reaches them, they can resist, scorch into dirt, or scorch and briefly carry flame above the surface.

Additional fire reaction tags cover fragile organics, dry flash fuels, and dense fuels:

- `#emergent:burns_away_in_fire`
- `#emergent:flash_burns_in_fire`
- `#emergent:sustains_fire`

### Passenger Momentum Transfer

Dismounting from a moving vehicle carries the vehicle's momentum into the passenger. Fast minecarts can throw players and mobs forward instead of letting them step off as if the cart were stationary, including when minecart speed has been increased by game rules or other mods. Inherited momentum briefly preserves inertia against vanilla ground friction so high-speed dismounts slide or fly farther before terrain, fluids, or collisions bleed the motion away.

### Kinetic Impacts

Fast minecarts, boats, falling blocks, and moving living entities can injure entities or break brittle blocks based on mass and relative speed. Falling sand, gravel, anvils, and other falling blocks transfer impact momentum instead of only relying on vanilla's special-case anvil damage.

### Ballistic Inertia

Airborne living entities, off-rail minecarts, boats, falling blocks, and dropped items keep more physically believable trajectories. Drag is based on simple mass versus frontal area, so dense/heavy objects hold speed better while small light objects slow more. Ground, rails, water, lava, and collisions still bleed momentum through their normal environmental rules.

### Universal Warden Summoning

Warden summoning behavior can be widened beyond vanilla's default restrictions.

### Finite Water Flow

Water sources can be prevented from regenerating infinitely. Water moves as conserved block-volume units: gravity is preferred, horizontal flow equalizes lower neighboring cells, and source water can fill waterloggable blocks through vanilla fluid-container hooks.

### Rain Accumulation

Rain can slowly accumulate water in exposed spaces. Absorbent surfaces such as dirt, grass, mud, and sand collect less readily, while existing shallow water can deepen over time.

### Hydraulic Erosion

Flowing water can erode vulnerable blocks over time. Erosion is driven by actual finite-water transfer when finite flow is enabled, with bank impact and bed shear applying bounded probabilities based on block hardness and tags.

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
config/emergent.json
```

Example:

```json
{
  "volatileContainers": true,
  "volatileDroppedItems": true,
  "volatileInventories": true,
  "reactiveCreepers": true,
  "infiniteFireSpread": true,
  "burningEntityFireSpread": true,
  "wetnessFireDampening": true,
  "passengerMomentumTransfer": true,
  "kineticImpacts": true,
  "ballisticInertia": true,
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
data/emergent/tags/item/volatile_explosives.json
data/emergent/tags/block/brittle.json
data/emergent/tags/block/burns_away_in_fire.json
data/emergent/tags/block/flash_burns_in_fire.json
data/emergent/tags/block/scorches_to_dirt_in_fire.json
data/emergent/tags/block/sustains_fire.json
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

Fast local smoke check:

```powershell
.\scripts\dev_smoke.ps1
```

The smoke check also runs Fabric server GameTests from `src/gametest`. These tests are for physics and interaction behavior that needs a real Minecraft world tick, such as finite water movement, waterlogging hooks, and erosion outcomes.

Build, check mixin-package hygiene, and copy the jar into the default Prism test instance:

```powershell
.\scripts\dev_smoke.ps1 -CopyToPrism
```

## License

This project is provided under the repository's license.
