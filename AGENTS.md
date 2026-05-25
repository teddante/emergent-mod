# AI Development Guidelines

## Project Direction

Emergent is a Minecraft mod about physically motivated, system-driven gameplay. Prefer interactions that arise from shared world state rather than isolated one-off rules. If a player would reasonably expect fire, water, heat, impact, pressure, moisture, mass, or material properties to matter, the systems should connect in a logical Minecraft-scale way.

Keep the mod readable, compatible, and efficient. Use simple mechanisms that compose well before adding broad abstractions or config surfaces.

## Continuous Process Improvement

- If repeated work reveals a better project workflow, update this file with the generalized lesson. Keep additions short, durable, and broadly useful; avoid recording one-off fixes, temporary preferences, or details that belong in code comments, README usage notes, tests, or PR descriptions.
- Prefer improvements that reduce future mistakes, tool calls, token use, manual testing, branch confusion, stale Minecraft-version assumptions, or unverifiable claims.
- Improve the improvement loop itself when friction repeats: make the guidance clearer, faster, easier to verify, or easier to prune. Treat process edits like code edits by keeping them evidence-based, scoped, and useful to future agents rather than aspirational.
- When adding process guidance, keep it current for the active Minecraft/Fabric toolchain and verify against local project reality before codifying it.

## Implementation Principles

- Use vanilla mechanics, APIs, tags, registries, and block/entity behavior wherever practical.
- Prefer tags and material/profile helpers over hardcoded name checks.
- Extend vanilla behavior with small, non-destructive mixins where possible. Use invasive mixins only when the timing of vanilla logic leaves no safer hook.
- Keep changes scoped to the feature or bug being worked on. Avoid unrelated refactors.
- Model values in Minecraft-scale units where useful: blocks are roughly `1 m^3`, fluids are finite amounts, and time-sensitive behavior should be explainable in ticks or seconds.
- Avoid arbitrary caps that erase expected outcomes. If work must be limited for performance, defer and integrate it over elapsed time rather than deleting it.
- Make slow environmental systems sparse, deterministic, and event-driven where possible. Wake affected cells when state changes; do not rely on constant global ticking.

## Source And Mapping Rules

- Do not guess Minecraft method names or signatures. Inspect the local `mc-src` cache or use `javap` before writing mixins against vanilla code.
- For current Minecraft/Fabric versions, expect Mojang official names and package paths. If `mc-src` is missing, stale, or looks like an older mapping layout, regenerate sources with Gradle and repopulate the cache with `scripts/extract_sources.ps1`.
- Keep mixin configs, refmaps, entrypoints, access wideners, tags, resources, and generated-source assumptions synchronized with code changes.

## Testing And Verification

- Prefer repeatable command-line validation before manual Minecraft testing.
- Run `scripts/dev_smoke.ps1` for build, config/resource/config-surface hygiene, jar inspection, server GameTests, and optional Prism copy. It keeps full Gradle logs under `build/reports/emergent-smoke` and prints compact failure hints, so inspect the log path before rerunning noisy commands.
- Use `scripts/dev_perf.ps1` for token-efficient headless profiler runs. It enables opt-in stress GameTests by default, saves full logs under `build/reports/emergent-profiler`, and prints only the compact summary needed to diagnose subsystem cost. Use `-SlowMs 0` only when microscope-level counter totals are worth the extra log volume, `-TrackPositions` only when hot chunks need exact block positions, and `-ActiveFluidBudget 64 -ActiveFluidChunkBudget 32 -RequireInspectionDeferrals -RequireBudgetDeferrals -RequireChunkBudgetDeferrals` when changing finite-fluid scheduled-tick inspection or active-work budget behavior.
- For real Prism performance reports, run `scripts/analyze_profiler_log_directory.ps1` on the instance logs before changing code. If it reports no current-format finite-fluid logs, copy/test the latest jar again before tuning budgets or drawing conclusions from stale profiler output.
- Run Gradle-backed smoke/perf commands sequentially on Windows. Parallel Gradle runs can contend for build or log file locks and produce noisy failures.
- Treat first-tick profiler spikes as warmup unless they reproduce after warmup or in a real loaded world.
- Put deterministic gameplay and physics coverage in `src/gametest` when behavior can be checked without a manual client session.
- Tests should assert real invariants, not implementation trivia. Good tests cover conservation, thresholds, state transitions, negative cases, and cross-system interactions.
- Manual in-game validation is still needed for feel, pacing, visual clarity, and large-world performance.

## GitHub Workflow

- Start work by checking branch, tracking state, and dirty files.
- Keep `main` buildable. Do feature and fix work on short-lived branches with clear names.
- A branch/PR should have one reviewable purpose. If the current branch name no longer describes the next change, stop and create a new branch or explicitly choose a stacked PR.
- Large integration PRs are acceptable while draft when systems are tightly coupled, but stop adding unrelated features once the scope starts drifting.
- For stacked PRs, merge from the lowest base upward, then retarget or update dependent PRs onto the newly merged base. Keep dependent branches until their PRs are merged or intentionally closed.
- Commit coherent working chunks after verification when practical. Use clear commit messages. Do not tag every commit.
- Keep draft PRs updated with summary, gameplay/config impact, compatibility impact, and verification. Mark ready only after automated checks pass and required manual validation is done.
- When editing long PR bodies from shell scripts, prefer a markdown body file and verify the result does not contain literal escape sequences such as `` `n`` or `` `r``.
- Do not block every inner-loop push on `gh pr checks --watch`. Prefer local smoke/perf gates first, then check CI synchronously when build tooling changed, a failure is suspected, the PR is being readied, or the user needs a definitive remote status now.
- Use issues for reproducible bugs and concrete feature ideas with versions, logs, mod lists, and reproduction steps when relevant.
- Use SemVer in `gradle.properties`: patch for compatible fixes/tuning, minor for compatible feature systems, major for breaking config/data behavior or dropping a supported Minecraft line. `0.y.z` still means early development.
- Only tag tested public builds. Releases should come from annotated `vMAJOR.MINOR.PATCH` tags after smoke checks and release notes are ready.
- Never rewrite public history, force-push, delete branches, close issues, publish releases, or alter repo settings unless the user clearly asks for that exact action.

## Known Project Lessons

- Vanilla items do not generally expose an explosive-power value. Represent item explosive categories with tags or project data, not name matching.
- If a mixin changes a block state and later logic in the same tick reads cached state from that object, update the local cached state as well when required by the vanilla class.
- When actions can mutate collections being iterated, iterate over a copy.
- Prefer local source verification over memory or online examples, especially across Minecraft version changes.
