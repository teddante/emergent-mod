# Emergent

Emergent is a Minecraft Fabric mod that makes world systems interact in broader, more physical ways. Fire, water, rain, heat, impacts, mobs, inventories, brewing, enchanting, and terrain all become more dynamic while staying configurable.

The goal is not to add isolated gimmicks. Emergent tries to make Minecraft's existing systems share state: wet surfaces resist fire, water carries sediment, heat and cold change materials, impacts weaken blocks, and the same entity-energy model can affect dropped XP and sculk catalyst charge.

## Feature Overview

### Explosives And Volatile Inventories

Explosive item stacks can detonate when exposed to fire, lava, or explosion damage.

- Dropped explosives can ignite, detonate, and chain-react.
- Explosive items inside inventories or containers can react.
- Nested container items, such as shulker boxes filled with TNT, are inspected too.
- Explosion strength scales with quantity and tag category.

Item tags control explosive categories:

- `#emergent:low_explosives`
- `#emergent:explosives`
- `#emergent:high_explosives`
- `#emergent:volatile_explosives`

### Fire, Wetness, And Material Reactions

Fire spread remains based on vanilla placement and flammability rules, but surrounding systems can now affect the outcome.

- Rain, stored surface moisture, waterlogged blocks, and nearby water dampen ignition.
- Grass-like surfaces can resist fire, scorch to dirt, or briefly carry flame.
- Tagged organics can flash-burn, burn away, char, or sustain fire longer.
- Fire and lava can add stored heat to nearby material, feeding later thermal shock or drying.
- Charred wood and burned blocks can leave ash residue for rain runoff or plant growth.

Important block tags include:

- `#emergent:burns_away_in_fire`
- `#emergent:chars_in_fire`
- `#emergent:flash_burns_in_fire`
- `#emergent:scorches_to_dirt_in_fire`
- `#emergent:sustains_fire`
- `#emergent:heat_sources`

### Fluid, Rain, And Erosion Physics

Water and lava can use finite volume instead of infinite source regeneration.

- Fluids move as conserved block-volume units.
- Gravity is preferred; horizontal flow equalizes lower neighboring cells.
- Thin layers settle as puddles instead of spreading forever.
- Source water can fill waterloggable blocks through vanilla fluid-container hooks.
- Lava follows slower vanilla timing and reacts with water.

Rain and water feed the same surface-memory model:

- Rain first wets exposed surfaces.
- Hard, low-absorption surfaces can release visible puddles sooner.
- Soil, mud, sand, and grass soak more moisture before puddles appear.
- Puddle formation consumes stored surface moisture instead of creating free water.
- Puddles and flowing water can wash ash into suspended sediment.
- Slow rain, snow, drying, and climate samples are queued through a deterministic scheduler so busy weather does not all execute inside one vanilla precipitation tick.

Hydraulic erosion is driven by actual water movement when finite flow is enabled:

- Flowing water adds wear based on moved volume and direction.
- Wet soft material erodes more easily.
- Structural stress can lower erosion thresholds.
- Water can carry suspended sediment downstream.
- Settled sediment can deposit as dirt, clay, mud, sand, or gravel depending on carried mass and water concentration.

### Heat, Cold, And Structural Stress

Blocks can accumulate temporary runtime heat, cold, moisture, ash, sediment, wear, and stress.

- Stored heat can evaporate shallow water, melt snow/ice, dry surfaces, and diffuse through conductive material.
- Stored cold can freeze finite water or stored surface moisture into snow layers.
- Freezing wet porous material can add frost-wedging structural stress.
- Thermal shock, explosions, impacts, erosion, and frost wedging all feed shared structural stress.
- Stressed material can fracture into sensible products such as cobblestone, sand, or gravel.

### Movement, Traffic, And Impacts

Movement systems preserve more physical momentum while still respecting terrain, fluids, rails, collisions, and player control.

- Dismounting from a moving vehicle carries vehicle momentum into the passenger.
- Airborne entities and vehicles retain ballistic motion with simple mass-vs-area drag.
- Fast vehicles, falling blocks, boats, and moving entities can injure entities or stress fragile blocks.
- Repeated movement over soft ground can compact surfaces into paths.
- Wider entities apply traffic wear over their contact patch.
- Traffic can trample crops and soft vegetation.

### Plants, Weather, And Biomes

Weather and climate affect the same shared environmental memory.

- Humid biome tags increase rain wetting and reduce drying.
- Arid/nether-like tags dry surfaces faster and increase heat stress.
- Stored soil moisture and ash residue can improve rain-assisted plant growth.
- Successful rain growth consumes a small amount of stored moisture and ash.
- Hot, dry exposure can stress vegetation; moisture can relieve that stress.
- Dropped plantable items can settle into suitable blocks.

### Dynamic XP And Sculk

Entity XP is derived from a simple body-energy model using max health, estimated body mass, armor, and toughness. The model treats raw XP points as the shared experience-energy unit and keeps vanilla levels as the display/storage curve. This modifies Minecraft's central living-entity XP query, so dropped XP and sculk catalyst charge use the same value.

Sculk shrieker summoning can also be widened beyond vanilla's default restrictions.

### Boundless Enchanting And Brewing

Optional systems can relax selected vanilla limits:

- Anvil prior-work penalties and the Too Expensive cap can be removed.
- Matching enchantments can combine past vanilla max levels.
- Mutually exclusive enchantments can coexist.
- Redstone can repeatedly extend potion duration.
- Glowstone can repeatedly raise potion strength.

## Configuration

The config is generated at:

```text
config/emergent.json
```

These toggles are broad feature gates, not one setting per emergent sub-system. Many newer interactions intentionally share existing gates such as `finiteWaterFlow`, `rainAccumulation`, `hydraulicErosion`, `kineticImpacts`, and `materialReactions` instead of adding a separate option for every small physical effect.

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

If Mod Menu and Cloth Config are installed, Emergent also provides an in-game config screen.

## Tags

Most categorical behavior is tag-driven for compatibility with vanilla and other mods.

Common tag paths:

```text
data/emergent/tags/item/low_explosives.json
data/emergent/tags/item/explosives.json
data/emergent/tags/item/high_explosives.json
data/emergent/tags/item/volatile_explosives.json
data/emergent/tags/item/plantables.json
data/emergent/tags/block/brittle.json
data/emergent/tags/block/burns_away_in_fire.json
data/emergent/tags/block/chars_in_fire.json
data/emergent/tags/block/compacts_under_traffic.json
data/emergent/tags/block/conductive.json
data/emergent/tags/block/erodes_in_water.json
data/emergent/tags/block/flash_burns_in_fire.json
data/emergent/tags/block/heat_sources.json
data/emergent/tags/block/rain_grows.json
data/emergent/tags/block/rain_oxidizes.json
data/emergent/tags/block/scorches_to_dirt_in_fire.json
data/emergent/tags/block/sustains_fire.json
data/emergent/tags/block/washes_away_in_water.json
```

## Compatibility

Emergent prefers vanilla APIs, block tags, item tags, block states, inventories, and standard placement/growth hooks instead of hardcoded block names.

- Most features use non-destructive injections where practical.
- Feature categories are broad enough for modpack use without adding a config toggle for every tiny interaction.
- Runtime environmental memory is not saved as world data unless a later system explicitly introduces persistence.
- Slow surface-weather work is batched and weighted so delayed samples still apply the physical rain/drying opportunity that accumulated.
- Dynamic XP uses the vanilla living-entity reward query, so sculk catalysts and XP orbs stay on the native path.

## Development

Build:

```powershell
./gradlew.bat build
```

Fast local smoke check:

```powershell
.\scripts\dev_smoke.ps1
```

The smoke script checks mixins, resources, config/docs/UI coverage, and jar contents. It also keeps full Gradle output under `build\reports\emergent-smoke` and prints compact success or failure lines, so failed GameTests can be diagnosed from the saved log without flooding the terminal.

Emergent targets Minecraft 26.1.x with Java 25, Fabric Loom 1.15.x, and Gradle 9.4.x. Development guidance lives in [CONTRIBUTING.md](CONTRIBUTING.md), repository maintenance and release settings live in [docs/REPOSITORY_MAINTENANCE.md](docs/REPOSITORY_MAINTENANCE.md), and versioning guidance lives in [docs/VERSIONING.md](docs/VERSIONING.md).

Current development version: `0.1.0`. GitHub Actions builds PRs to `main`, runs the local smoke checks, uploads the jar artifact, submits Gradle dependencies to GitHub's dependency graph on `main`, and creates releases from annotated `v*` tags.

Build, check mixin/resource hygiene, run server GameTests, inspect the jar, and copy the jar into the default Prism test instance:

```powershell
.\scripts\dev_smoke.ps1 -RequireMinecraftSources -CopyToPrism
```

By default, `-CopyToPrism` targets:

```text
C:\Users\edwar\AppData\Roaming\PrismLauncher\instances\Prism Launcher Thing for Emergent mod testing\minecraft\mods
```

When copying to Prism, the script removes older `emergent-*.jar` files from that mods folder before copying the current build. It also writes `mods\emergent-copy-info.txt` with the copied jar hash, jar timestamp, branch, commit, dirty-worktree state, and whether `-SkipBuild` was used, so manual launcher tests can be tied back to the exact build instead of only the reused mod version. This keeps manual tests from accidentally loading stale duplicate Emergent jars or ambiguous same-version builds.

Headless profiler run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev_perf.ps1 -SlowMs 10 -Top 12
```

This runs the server GameTests without opening Minecraft, enables the Emergent profiler and opt-in stress scenarios, saves the full log under `build\reports\emergent-profiler`, ignores the first 20 warmup ticks by default, and prints only the worst profiler ticks plus counter totals, finite-fluid diagnosis, lava heat pacing, and chunk hotspots. Use it for fast regression checks and subsystem diagnosis; use Prism afterward for large-world feel and player-visible validation. Pass `-SkipStressScenarios` to profile only the normal correctness GameTests, `-SlowMs 0` for microscope-mode counter totals from all instrumented ticks, `-TrackPositions` when a hot chunk needs exact finite-fluid block positions, `-MaxProfilerMs 25` to fail the run on a post-warmup performance regression, or `-ActiveFluidBudget 64 -ActiveFluidChunkBudget 32 -RequireInspectionDeferrals -RequireBudgetDeferrals -RequireChunkBudgetDeferrals` to verify the finite-fluid deferral paths under controlled pressure. Deferral assertions may temporarily lower the effective profiler threshold to `1 ms` so the required counter evidence is not hidden by a quiet tick.

The opt-in stress scenarios currently exercise stable finite-fluid wakeups, multi-chunk finite-water settling, broad shallow finite-water shelves, concentrated one-chunk finite-water and finite-lava hotspots, sloped finite-water channels, terraced finite-water cascades, queued surface-weather samples, repeated fire-reaction scans, traffic contact patches, and lava/water thermal reactions.

### Profiling In Prism

For lag investigations, add these JVM arguments to the Prism instance:

```text
-Demergent.profiler=true -Demergent.profiler.slowMs=25
```

When an Emergent tick exceeds the threshold, the log reports subsystem timings and counters such as finite fluid ticks, active fluid reschedules, inspection and active-work budget claims/deferrals, quiet schedule skips, quiet tick skips, quiet-cache hits/miss breakdowns/signature misses/invalidations/evictions, thermal-cache skips, water/lava split, surface-weather jobs, fire scans, traffic events, pending weather jobs, the hottest finite-fluid chunks, and the top heated block types. Add `-Demergent.profiler.positions=true` only for focused diagnosis when chunk hotspots are not enough; it reports exact hot finite-fluid block positions at the cost of extra per-tick bookkeeping and log text. If Minecraft still logs `Can't keep up!` but Emergent does not log a matching slow profiler line, the spike is probably outside the instrumented Emergent systems. The finite-fluid budget runs cheap settled/waterloggable checks first, admits scheduled ticks through deterministic global/per-chunk inspection budgets before thermal checks or neighbour scans, then reuses exact quiet proofs until nearby block/fluid or environmental heat/cold/moisture state changes invalidate that local cache. Quiet-cache misses are split into no-cache, entry, fluid, amount, and thermal-signature buckets so stale wakeups can be separated from ordinary first-time inspections. Excess neighbour-scan/active work is deferred through a second active-work budget instead of being dropped. Lava contact heating is part of that budgeted active-cell work, so large hot lava areas are paced with movement and neighbour scans instead of bypassing the scheduler. Per-chunk caps stop one hot chunk from monopolizing the whole world budget while other active cells starve. The default budget is `256` active cells per tick, `64` active cells per chunk, and an inspection budget eight times those active-work limits, chosen from headless stress runs as a conservative integrated-server default; for diagnosis only, `-Demergent.finiteFluid.activeTickBudget=8192`, `-Demergent.finiteFluid.activeChunkTickBudget=1536`, or `.\scripts\dev_perf.ps1 -ActiveFluidBudget 8192 -ActiveFluidChunkBudget 1536` can be used to compare throughput against server tick stability.

Saved Prism or launcher logs can be summarized without relaunching Minecraft:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\analyze_profiler_log.ps1 -Path "C:\path\to\latest.log" -Top 12
```

To scan a whole Prism log folder or `build\reports\emergent-profiler` without opening every file:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\analyze_profiler_log_directory.ps1 -Directory "C:\path\to\logs" -TopFiles 12
```

The saved-log analyzers accept plain `.log` files and archived `.log.gz` files. They summarize vanilla `Can't keep up!` warnings, profiler startup state, finite-fluid inspection and active-work budget values, profiler format age, finite-fluid deferrals, lava heat pacing, and top finite-fluid chunks.
If the finite-fluid diagnosis says schedule, inspection, budget, or quiet-cache counters are missing, the log was captured with an older test jar; launch the current copied jar once and analyze the new `latest.log` before making scheduler decisions.

See [FUTURE_FEATURES.md](FUTURE_FEATURES.md) for current tracking and likely next work.

## License

This project is provided under the repository's license.
