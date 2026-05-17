# Emergent Feature Tracking

This file tracks likely next work without turning the README into a changelog. Keep it concise and update it when a feature moves from idea to implementation.

## Current PR Stack

The active work is split across stacked draft PRs:

- PR #3, `fix/water-erosion-physics`, integrates the broad environmental physics layer: shared runtime state for moisture, heat, cold, ash, sediment, traffic wear, structural stress, fluid flow, erosion, fire aftermath, rain puddles, plant growth, and impact/thermal/explosion interactions.
- PR #4, `perf-environmental-scheduler`, is stacked on PR #3 and focuses on slow environmental scheduling, finite-fluid quiescence, profiling, and headless performance checks.

No more unrelated features should be added to either PR. New gameplay systems should move to a focused branch from updated `main` once the current stack is merged, unless the work is directly required to stabilize the existing environmental integration.

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
- Deterministic scheduler for slow surface-weather samples, including weighted queued rain, snow, drying, puddle, and climate updates.
- Opt-in Emergent tick profiler with finite-fluid water/lava counters, active-schedule and quiet-skip reason counters, heated block summaries, finite-fluid chunk hotspots, and traffic contact-cell hotspots when traffic becomes a slow contributor.
- Headless stress/perf GameTests and compact `scripts/dev_perf.ps1` summaries covering stable fluids, multi-chunk finite water, sloped finite-water channels, surface weather, fire scans, traffic contact patches, lava/water thermal reactions, and finite-fluid active/quiet diagnosis.
- Dynamic entity XP feeding the vanilla XP/sculk catalyst path.
- Boundless enchanting, unrestricted enchantment compatibility, and boundless brewing.
- Command-line smoke checks and server GameTests.
- Mod Menu / Cloth Config screen for broad feature gates.

## High-Priority Next Work

- Extend the environmental scheduler beyond surface weather into other slow active cells where profiling proves it helps.
- Use finite-fluid chunk hotspot output from real Prism logs to identify whether heavy ticking comes from one loaded area, stale wakeups, or genuinely active fluid movement.
- Add broader representative performance scenarios only where they cover real-world lag patterns that the current headless tests miss, especially larger player-made fluid systems and any Prism logs that do not resemble the current basin/channel stress cases.
- Design the experience-energy layer so XP, sculk charge, enchanting, anvils, books, and enchantment output all use one shared quantity instead of unrelated costs.
- Merge or close the current draft PR stack in order once the integration work stabilizes; use focused branches for unrelated features.
- Manual gameplay feel pass for fire spread duration, rain puddle pacing, sediment deposition, freeze-thaw stress, traffic wear, and dynamic XP/sculk charge.
- README/config/PR documentation pass before release.

## Experience Energy Direction

Treat XP points as quantized usable experience energy. Player levels are the vanilla nonlinear storage/display curve; the model should reason in raw XP points internally.

Proposed shared flow:

- Living entities expose death energy from health, estimated body mass, armor, toughness, and later maybe active effects or equipment.
- Vanilla XP orbs and sculk catalyst charge read that same energy through the central living-entity reward path.
- Enchanting tables and anvils spend raw XP energy, not arbitrary level labels, while still presenting vanilla-compatible levels in the UI.
- Enchanted books and items can store an energy budget derived from their enchantment levels and rarity.
- Stronger enchantment effects should have explainable output: added damage, protection, duration, speed, durability savings, or utility work should scale from stored energy and use rate.
- Merging enchanted items should combine stored energy and resolve levels from that budget, rather than only applying a hard max-level rule.

Design constraints:

- Use `energy` for total capacity and `power` only for rate of output over time.
- Avoid pretending XP is literal biological chemical energy in joules. It is a Minecraft-scale usable energy analogue calibrated to vanilla XP points.
- Preserve vanilla compatibility paths first: XP rewards, sculk charge, enchanting, anvil output, and enchantment compatibility should still pass through native hooks.
- Add narrow tests for conversion invariants before changing player-facing mechanics.

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
