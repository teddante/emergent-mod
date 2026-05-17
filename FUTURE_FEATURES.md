# Emergent Feature Tracking

This file tracks likely next work without turning the README into a changelog. Keep it concise and update it when a feature moves from idea to implementation.

## Current PR Stack

The active work is split across stacked draft PRs:

- PR #3, `fix/water-erosion-physics`, integrates the broad environmental physics layer: shared runtime state for moisture, heat, cold, ash, sediment, traffic wear, structural stress, fluid flow, erosion, fire aftermath, rain puddles, plant growth, and impact/thermal/explosion interactions.
- PR #4, `perf-environmental-scheduler`, is stacked on PR #3 and focuses on slow environmental scheduling, finite-fluid quiescence, profiling, and headless performance checks.
- PR #5, `feature/experience-energy-model`, is stacked on PR #4 and introduces raw XP points as the shared experience-energy quantity used by dynamic entity rewards, the vanilla sculk catalyst path, and whole-level cost spending for enchanting/anvils.
- PR #7, `feature/enchantment-energy-costs`, is stacked on PR #5 and extends the same model to vanilla enchantment item/book budgets using `ItemEnchantments`, `Enchantment.getAnvilCost()`, and the vanilla enchanted-book half-cost rule.

No more unrelated features should be added to PR #3 or PR #4. Experience-energy follow-through should stay in focused branches stacked on PR #5 until the current stack is merged.

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
- Opt-in Emergent tick profiler with finite-fluid water/lava counters, active-schedule counters, quiet schedule/tick-skip reason counters, heated block summaries, finite-fluid chunk hotspots, and traffic contact-cell hotspots when traffic becomes a slow contributor.
- Headless stress/perf GameTests, compact `scripts/dev_perf.ps1` summaries, and saved-log analysis via `scripts/analyze_profiler_log.ps1` covering stable fluids, multi-chunk finite water, sloped finite-water channels, surface weather, fire scans, traffic contact patches, lava/water thermal reactions, finite-fluid active/quiet diagnosis, and `Can't keep up!` correlation.
- Dynamic entity XP feeding the vanilla XP/sculk catalyst path through a shared raw-XP experience-energy helper, plus whole-level raw XP spending for boundless enchanting and anvil costs.
- Enchanted item/book budget helpers that derive work from vanilla enchantment anvil costs and levels, then convert that work into raw XP through the shared vanilla level curve.
- Repair enchantment output above the vanilla cap scales durability repaired per raw XP by stored repair-enchantment work, preserving vanilla-level Mending behavior.
- Boundless enchanting, unrestricted enchantment compatibility, and boundless brewing.
- Command-line smoke checks and server GameTests.
- Mod Menu / Cloth Config screen for broad feature gates.

## High-Priority Next Work

- Extend the environmental scheduler beyond surface weather into other slow active cells where profiling proves it helps.
- Use finite-fluid chunk hotspot output from real Prism logs to identify whether heavy ticking comes from one loaded area, stale wakeups, or genuinely active fluid movement.
- Add broader representative performance scenarios only where they cover real-world lag patterns that the current headless tests miss, especially larger player-made fluid systems and any Prism logs that do not resemble the current basin/channel stress cases.
- Extend the experience-energy layer from repair-output scaling into more enchantment effect outputs where there is a clear conserved work or rate interpretation, plus optional UI/tooltips.
- Merge or close the current draft PR stack in order once the integration work stabilizes; use focused branches for unrelated features.
- Manual gameplay feel pass for fire spread duration, rain puddle pacing, sediment deposition, freeze-thaw stress, traffic wear, and dynamic XP/sculk charge.
- README/config/PR documentation pass before release.

## Experience Energy Direction

Treat XP points as quantized usable experience energy. Player levels are the vanilla nonlinear storage/display curve; the model should reason in raw XP points internally.

Proposed shared flow:

- Living entities expose death energy from health, estimated body mass, armor, toughness, and later maybe active effects or equipment.
- Vanilla XP orbs and sculk catalyst charge read that same energy through the central living-entity reward path.
- Enchanting tables and anvils spend raw XP energy for whole-level costs when boundless enchanting is enabled, while still presenting vanilla-compatible levels in the UI.
- Enchanted books and items expose an energy budget derived from their enchantment levels and vanilla anvil-cost rarity.
- Merging enchanted items combines stored enchantment work budgets into the resulting level while preserving Minecraft's component level cap.
- Repair enchantments above their vanilla maximum output a higher durability-repair rate from raw XP in proportion to stored repair work.
- Stronger enchantment effects should have explainable output: added damage, protection, duration, speed, durability savings, or utility work should scale from stored energy and use rate.

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
