# Emergent Feature Tracking

This file tracks likely next work without turning the README into a changelog. Keep it concise and update it when a feature moves from idea to implementation.

## Current Integration Theme

The active draft PR is a broad environmental physics integration. It covers shared runtime state for moisture, heat, cold, ash, sediment, traffic wear, structural stress, fluid flow, erosion, fire aftermath, rain puddles, plant growth, and impact/thermal/explosion interactions.

No more unrelated features should be added to that PR. New work should move to a focused branch unless it is directly required to stabilize the current environmental integration.

## Implemented Or In Draft

- Volatile dropped items, inventories, and containers.
- Reactive creepers and explosive chain reactions.
- Persistent fire spread and burning-entity fire spread.
- Wetness-aware fire dampening and tag-driven fire material reactions.
- Passenger momentum transfer and ballistic inertia.
- Kinetic impact damage and shared structural stress.
- Traffic wear across entity contact patches, including crop trampling.
- Finite water and lava flow with waterlogging support.
- Rain wetting, puddles, ash runoff, and moisture memory.
- Hydraulic erosion, suspended sediment transport, abrasion, and deposition.
- Heat/cold exposure, conduction, evaporation, freezing, snow/ice melt, thermal shock, and frost wedging.
- Biome-aware drying, wetting, solar heat, and vegetation stress.
- Moisture/ash-assisted rain growth.
- Dynamic entity XP feeding the vanilla XP/sculk catalyst path.
- Boundless enchanting, unrestricted enchantment compatibility, and boundless brewing.
- Command-line smoke checks and server GameTests.
- Mod Menu / Cloth Config screen for broad feature gates.

## High-Priority Next Work

- Environmental scheduler: active cells, wake events, deterministic staggering, and elapsed-time integration for slow systems.
- Split or close broad draft PRs once the integration work stabilizes; use focused branches for unrelated features.
- Performance profiling with a representative world using finite fluids, rain, fire, traffic, and heat/cold exposure.
- Manual gameplay feel pass for fire spread duration, rain puddle pacing, sediment deposition, freeze-thaw stress, traffic wear, and dynamic XP/sculk charge.
- README/config/PR documentation pass before release.

## Candidate Feature Ideas

- More explicit storm effects: exposed campfire/torch extinguishing, stronger runoff, lightning conduction through tagged blocks.
- Projectile material damage: arrows/tridents cracking glass or lodging as fire hazards near heat.
- Better structural failure: cave-ins, charred supports, frost-cracked stone, softened dirt, and erosion-weakened banks.
- More residue ecology: ash fertilization, soot/char runoff, soil enrichment, and visibility/traction effects if they remain performant.
- Biome-specific tuning: deserts dry quickly, jungles/swamps retain moisture, snowy biomes freeze/thaw, Nether evaporates water aggressively.
- More modded-material extensibility through tags and material profile helpers.

## Release Readiness Checklist

- `scripts/dev_smoke.ps1 -RequireMinecraftSources -CopyToPrism` passes.
- GitHub Actions are green on the PR head.
- README, config labels/tooltips, tags, and PR body reflect the shipped behavior.
- Manual in-game validation covers pacing and large-world performance.
- Version bump is chosen intentionally in `gradle.properties`.
- Annotated tag and GitHub Release are created only for tested public builds.
