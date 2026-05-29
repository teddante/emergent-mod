param(
    [int]$SlowMs = 10,
    [int]$Top = 12,
    [int]$WarmupTicks = 20,
    [double]$MaxProfilerMs = 0,
    [int]$ActiveFluidBudget = 0,
    [int]$ActiveFluidChunkBudget = 0,
    [switch]$RequireInspectionDeferrals,
    [switch]$RequireBudgetDeferrals,
    [switch]$RequireChunkBudgetDeferrals,
    [switch]$TrackPositions,
    [switch]$SkipStressScenarios
)

$ErrorActionPreference = "Stop"

function Write-Step($Message) {
    Write-Host "==> $Message"
}

function Get-RepoRoot {
    $scriptDir = Split-Path -Parent $PSCommandPath
    return (Resolve-Path (Join-Path $scriptDir "..")).Path
}

function Get-ProfilerValue($Line) {
    if ($Line -match "Emergent profiler: ([0-9.]+) ms") {
        return [double]::Parse($Matches[1], [Globalization.CultureInfo]::InvariantCulture)
    }
    return 0.0
}

function Get-ProfilerTick($Line) {
    if ($Line -match " at tick ([0-9]+) ") {
        return [long]$Matches[1]
    }
    return [long]::MaxValue
}

function Add-Counters($Line, [hashtable]$Totals) {
    if ($Line -notmatch "counters=(.*?)(?= chunks=| positions=| heat=|\))") {
        return
    }

    $counterText = $Matches[1]
    foreach ($match in [regex]::Matches($counterText, "([A-Za-z0-9_]+):([0-9]+)")) {
        $name = $match.Groups[1].Value
        $value = [long]$match.Groups[2].Value
        if (!$Totals.ContainsKey($name)) {
            $Totals[$name] = 0L
        }
        $Totals[$name] += $value
    }
}

function Add-Chunks($Line, [hashtable]$Totals) {
    if ($Line -notmatch "chunks=(.*?)(?= positions=| heat=|\))") {
        return
    }

    $chunkText = $Matches[1]
    foreach ($match in [regex]::Matches($chunkText, "([A-Za-z0-9_]+)@(-?[0-9]+,-?[0-9]+):([0-9]+)")) {
        $name = "$($match.Groups[1].Value)@$($match.Groups[2].Value)"
        $value = [long]$match.Groups[3].Value
        if (!$Totals.ContainsKey($name)) {
            $Totals[$name] = 0L
        }
        $Totals[$name] += $value
    }
}

function Add-Positions($Line, [hashtable]$Totals) {
    if ($Line -notmatch "positions=(.*?)(?= heat=|\))") {
        return
    }

    $positionText = $Matches[1]
    foreach ($match in [regex]::Matches($positionText, "([A-Za-z0-9_]+)@(-?[0-9]+,-?[0-9]+,-?[0-9]+):([0-9]+)")) {
        $name = "$($match.Groups[1].Value)@$($match.Groups[2].Value)"
        $value = [long]$match.Groups[3].Value
        if (!$Totals.ContainsKey($name)) {
            $Totals[$name] = 0L
        }
        $Totals[$name] += $value
    }
}

function Get-CounterTotal([hashtable]$Totals, [string]$Name) {
    if ($Totals.ContainsKey($Name)) {
        return [long]$Totals[$Name]
    }
    return 0L
}

function Add-TopChunkSummary([System.Collections.Generic.List[string]]$Summary, [hashtable]$ChunkTotals, [string]$Prefix, [string]$Label, [int]$Top) {
    $topChunks = @($ChunkTotals.GetEnumerator() |
        Where-Object { $_.Name -like "$Prefix@*" } |
        Sort-Object -Property @{ Expression = { $_.Value }; Descending = $true }, Name |
        Select-Object -First ([Math]::Min(3, $Top)))
    if ($topChunks.Count -gt 0) {
        $chunkText = ($topChunks | ForEach-Object { "$($_.Name):$($_.Value)" }) -join " "
        $Summary.Add("  ${Label}=$chunkText")
    }
}

function Add-TopPositionSummary([System.Collections.Generic.List[string]]$Summary, [hashtable]$PositionTotals, [string]$Prefix, [string]$Label, [int]$Top) {
    $topPositions = @($PositionTotals.GetEnumerator() |
        Where-Object { $_.Name -like "$Prefix@*" } |
        Sort-Object -Property @{ Expression = { $_.Value }; Descending = $true }, Name |
        Select-Object -First ([Math]::Min(3, $Top)))
    if ($topPositions.Count -gt 0) {
        $positionText = ($topPositions | ForEach-Object { "$($_.Name):$($_.Value)" }) -join " "
        $Summary.Add("  ${Label}=$positionText")
    }
}

function Add-FiniteFluidDiagnosis([System.Collections.Generic.List[string]]$Summary, [hashtable]$CounterTotals, [hashtable]$ChunkTotals, [int]$Top) {
    $finiteTicks = Get-CounterTotal $CounterTotals "finite_fluid_ticks"
    if ($finiteTicks -le 0) {
        return
    }

    $waterTicks = Get-CounterTotal $CounterTotals "finite_fluid_water_ticks"
    $lavaTicks = Get-CounterTotal $CounterTotals "finite_fluid_lava_ticks"
    $lavaHeat = Get-CounterTotal $CounterTotals "finite_fluid_lava_heat"
    $activeSchedules = Get-CounterTotal $CounterTotals "finite_fluid_active_schedules"
    $quietSkips = Get-CounterTotal $CounterTotals "finite_fluid_quiet_schedule_skips"
    $inspectionClaims = Get-CounterTotal $CounterTotals "finite_fluid_inspection_claims"
    $inspectionDeferrals = Get-CounterTotal $CounterTotals "finite_fluid_inspection_deferrals"
    $chunkInspectionClaims = Get-CounterTotal $CounterTotals "finite_fluid_inspection_chunk_claims"
    $globalInspectionDeferrals = Get-CounterTotal $CounterTotals "finite_fluid_inspection_global_deferrals"
    $chunkInspectionDeferrals = Get-CounterTotal $CounterTotals "finite_fluid_inspection_chunk_deferrals"
    $budgetClaims = Get-CounterTotal $CounterTotals "finite_fluid_budget_claims"
    $budgetDeferrals = Get-CounterTotal $CounterTotals "finite_fluid_budget_deferrals"
    $chunkBudgetClaims = Get-CounterTotal $CounterTotals "finite_fluid_budget_chunk_claims"
    $globalBudgetDeferrals = Get-CounterTotal $CounterTotals "finite_fluid_budget_global_deferrals"
    $chunkBudgetDeferrals = Get-CounterTotal $CounterTotals "finite_fluid_budget_chunk_deferrals"
    $horizontalMoves = Get-CounterTotal $CounterTotals "finite_fluid_horizontal_moves"
    $downwardMoves = Get-CounterTotal $CounterTotals "finite_fluid_downward_moves"
    $thermalReactions = Get-CounterTotal $CounterTotals "finite_fluid_thermal_reactions"
    $thermalQuietSkips = Get-CounterTotal $CounterTotals "finite_fluid_water_thermal_quiet_skips"
    $thermalCacheSkips = Get-CounterTotal $CounterTotals "finite_fluid_water_thermal_cache_skips"
    $thinSettled = Get-CounterTotal $CounterTotals "finite_fluid_thin_settled"
    $stableSources = Get-CounterTotal $CounterTotals "finite_fluid_stable_sources"
    $quietTickSkips = Get-CounterTotal $CounterTotals "finite_fluid_quiet_tick_skips"
    $quietCacheHits = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_hits"
    $quietCacheMisses = [Math]::Max(0L, $inspectionClaims - $quietCacheHits)
    $quietCacheNoCacheMisses = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_no_cache_misses"
    $quietCacheEntryMisses = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_entry_misses"
    $quietCacheFluidMisses = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_fluid_misses"
    $quietCacheAmountMisses = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_amount_misses"
    $quietCacheSignatureMisses = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_signature_misses"
    $quietCacheInvalidations = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidations"
    $quietCacheInvalidatedEntries = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidated_entries"
    $quietCacheEvictions = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_evictions"

    $workEvents = $horizontalMoves + $downwardMoves + $thermalReactions + $lavaHeat
    $quietDenominator = [Math]::Max(1L, $activeSchedules + $quietSkips)
    $quietPercent = ($quietSkips * 100.0) / $quietDenominator
    $workPercent = ($workEvents * 100.0) / [Math]::Max(1L, $finiteTicks)
    $lavaHeatPercent = ($lavaHeat * 100.0) / [Math]::Max(1L, $lavaTicks)

    $Summary.Add("")
    $Summary.Add("Finite fluid diagnosis:")
    $Summary.Add(("  ticks={0} water={1} lava={2} activeSchedules={3} quietSkips={4} quietRatio={5:N1}% workEvents={6} workPerTick={7:N1}%" -f `
                $finiteTicks, $waterTicks, $lavaTicks, $activeSchedules, $quietSkips, $quietPercent, $workEvents, $workPercent))
    $Summary.Add(("  inspectionClaims={0} chunkInspectionClaims={1} inspectionDeferrals={2} globalInspectionDeferrals={3} chunkInspectionDeferrals={4}" -f `
                $inspectionClaims, $chunkInspectionClaims, $inspectionDeferrals, $globalInspectionDeferrals, $chunkInspectionDeferrals))
    $Summary.Add(("  budgetClaims={0} chunkBudgetClaims={1} budgetDeferrals={2} globalDeferrals={3} chunkDeferrals={4}" -f `
                $budgetClaims, $chunkBudgetClaims, $budgetDeferrals, $globalBudgetDeferrals, $chunkBudgetDeferrals))
    $Summary.Add(("  settledThin={0} stableSources={1} quietTickSkips={2} quietCacheHits={3} estimatedQuietCacheMisses={4} noCacheMisses={5} entryMisses={6} fluidMisses={7} amountMisses={8} signatureMisses={9} quietCacheInvalidations={10} quietCacheInvalidatedEntries={11} quietCacheEvictions={12} thermalQuietSkips={13} thermalCacheSkips={14} horizontalMoves={15} downwardMoves={16} thermalReactions={17}" -f `
                $thinSettled, $stableSources, $quietTickSkips, $quietCacheHits, $quietCacheMisses, $quietCacheNoCacheMisses, $quietCacheEntryMisses, $quietCacheFluidMisses, $quietCacheAmountMisses, $quietCacheSignatureMisses, $quietCacheInvalidations, $quietCacheInvalidatedEntries, $quietCacheEvictions, $thermalQuietSkips, $thermalCacheSkips, $horizontalMoves, $downwardMoves, $thermalReactions))
    if ($lavaTicks -gt 0 -or $lavaHeat -gt 0) {
        $Summary.Add(("  lavaHeat={0} lavaHeatPerLavaTick={1:N1}%" -f $lavaHeat, $lavaHeatPercent))
    }

    $quietReasons = @(
        @{ Name = "no_work"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_no_work_skips" },
        @{ Name = "thin"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_thin_skips" },
        @{ Name = "stable_source"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_stable_source_skips" },
        @{ Name = "waterloggable"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_waterloggable_skips" }
    ) | Sort-Object -Property @{ Expression = { $_.Value }; Descending = $true }
    $topQuiet = $quietReasons | Where-Object { $_.Value -gt 0 } | Select-Object -First 1
    if ($topQuiet) {
        $Summary.Add("  topQuietReason=$($topQuiet.Name):$($topQuiet.Value)")
    }

    $invalidationReasons = @(
        @{ Name = "environmental_memory_update"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_environmental_memory_update" },
        @{ Name = "environmental_memory_stale"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_environmental_memory_stale" },
        @{ Name = "environmental_memory_decay"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_environmental_memory_decay" },
        @{ Name = "environmental_memory_clear"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_environmental_memory_clear" },
        @{ Name = "block_update"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_block_update" },
        @{ Name = "neighbor_update"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_neighbor_update" },
        @{ Name = "unknown"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_unknown" }
    ) | Sort-Object -Property @{ Expression = { $_.Value }; Descending = $true }
    $topInvalidation = $invalidationReasons | Where-Object { $_.Value -gt 0 } | Select-Object -First 1
    if ($topInvalidation) {
        $Summary.Add("  topInvalidationReason=$($topInvalidation.Name):$($topInvalidation.Value)")
    }

    Add-TopChunkSummary $Summary $ChunkTotals "finite_fluids" "hottestFiniteFluidChunks" $Top
    Add-TopChunkSummary $Summary $ChunkTotals "finite_water" "hottestFiniteWaterChunks" $Top
    Add-TopChunkSummary $Summary $ChunkTotals "finite_lava" "hottestFiniteLavaChunks" $Top

    if ($inspectionDeferrals -gt 0 -and $budgetDeferrals -gt 0) {
        $Summary.Add("  interpretation=finite-fluid inspection and active work both exceeded budget and were deferred fairly; inspect chunk hotspots before raising either budget.")
    } elseif ($inspectionDeferrals -gt 0) {
        $Summary.Add("  interpretation=finite-fluid scheduled tick inspection exceeded the per-tick budget and was deferred fairly before expensive thermal/neighbour checks.")
    } elseif ($budgetDeferrals -gt 0) {
        $Summary.Add("  interpretation=finite-fluid neighbour-scan/active work exceeded the per-tick budget and was deferred fairly; inspect chunk hotspots before raising the budget.")
    } elseif ($quietPercent -ge 65.0 -and $workPercent -ge 35.0) {
        $Summary.Add("  interpretation=mixed active movement plus many quiet wakeups; inspect hotspot chunks before changing simulation pacing.")
    } elseif ($quietPercent -ge 65.0 -and $activeSchedules -gt 0) {
        $Summary.Add("  interpretation=mostly quiet wakeups; inspect top quiet reason and chunk hotspots for stale rescheduling.")
    } elseif ($workPercent -ge 35.0) {
        $Summary.Add("  interpretation=mostly active movement/reactions; optimize the hotspot geometry or movement algorithm before adding caps.")
    } else {
        $Summary.Add("  interpretation=mixed workload; compare Prism chunk hotspots with these synthetic scenarios.")
    }
}

$repoRoot = Get-RepoRoot
Set-Location $repoRoot

$reportDir = Join-Path $repoRoot "build\reports\emergent-profiler"
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logPath = Join-Path $reportDir "headless-perf-$stamp.log"
$summaryPath = Join-Path $reportDir "headless-perf-$stamp.summary.txt"

$gradleArgs = @("--no-daemon", "runGameTest")

$stressScenariosEnabled = !$SkipStressScenarios
$effectiveSlowMs = $SlowMs
if ($MaxProfilerMs -gt 0 -and $MaxProfilerMs -lt $effectiveSlowMs) {
    $effectiveSlowMs = $MaxProfilerMs
}
if (($RequireInspectionDeferrals -or $RequireBudgetDeferrals -or $RequireChunkBudgetDeferrals) -and $effectiveSlowMs -gt 1) {
    $effectiveSlowMs = 1
}

Write-Step "Running headless GameTests with Emergent profiler slowMs=$effectiveSlowMs stress=$stressScenariosEnabled"
$oldJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$activeFluidBudgetOption = if ($ActiveFluidBudget -gt 0) { " -Demergent.finiteFluid.activeTickBudget=$ActiveFluidBudget" } else { "" }
$activeFluidChunkBudgetOption = if ($ActiveFluidChunkBudget -gt 0) { " -Demergent.finiteFluid.activeChunkTickBudget=$ActiveFluidChunkBudget" } else { "" }
$trackPositionsOption = if ($TrackPositions) { " -Demergent.profiler.positions=true" } else { "" }
$env:JAVA_TOOL_OPTIONS = "-Demergent.profiler=true -Demergent.profiler.slowMs=$effectiveSlowMs -Demergent.perfScenarios=$($stressScenariosEnabled.ToString().ToLowerInvariant())$activeFluidBudgetOption$activeFluidChunkBudgetOption$trackPositionsOption"
$oldErrorActionPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = "Continue"
    & .\gradlew.bat @gradleArgs *>&1 | Set-Content -Path $logPath -Encoding UTF8
    $exitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $oldErrorActionPreference
    $env:JAVA_TOOL_OPTIONS = $oldJavaToolOptions
}

$logLines = Get-Content -Path $logPath
$allProfilerLines = @($logLines | Where-Object { $_ -like "*Emergent profiler:*" })
$profilerLines = @($allProfilerLines | Where-Object { (Get-ProfilerTick $_) -gt $WarmupTicks })
$testPassLine = @($logLines | Where-Object { $_ -like "*required tests passed*" } | Select-Object -Last 1)
$failureLines = @($logLines | Where-Object {
    $_ -like "*BUILD FAILED*" -or
    $_ -like "*Game test failed*" -or
    $_ -like "*required tests failed*" -or
    $_ -like "*Exception*"
} | Select-Object -First 12)

$counterTotals = @{}
$chunkTotals = @{}
$positionTotals = @{}
foreach ($line in $profilerLines) {
    Add-Counters $line $counterTotals
    Add-Chunks $line $chunkTotals
    Add-Positions $line $positionTotals
}

$worstProfilerLines = @($profilerLines |
    Sort-Object -Property @{ Expression = { Get-ProfilerValue $_ }; Descending = $true } |
    Select-Object -First $Top)

$summary = New-Object System.Collections.Generic.List[string]
$summary.Add("Emergent headless perf summary")
$summary.Add("Log: $logPath")
$summary.Add("Profiler slowMs: $effectiveSlowMs")
if ($effectiveSlowMs -ne $SlowMs) {
    $summary.Add("Requested profiler slowMs: $SlowMs")
}
$summary.Add("Warmup ticks ignored: $WarmupTicks")
$summary.Add("Stress scenarios: $stressScenariosEnabled")
$summary.Add("Position hotspots: $($TrackPositions.IsPresent)")
if ($ActiveFluidBudget -gt 0) {
    $summary.Add("Forced finite fluid work budget: $ActiveFluidBudget")
}
if ($ActiveFluidChunkBudget -gt 0) {
    $summary.Add("Forced finite fluid chunk work budget: $ActiveFluidChunkBudget")
}
if ($RequireBudgetDeferrals) {
    $summary.Add("Required budget deferrals: True")
}
if ($RequireInspectionDeferrals) {
    $summary.Add("Required inspection deferrals: True")
}
if ($RequireChunkBudgetDeferrals) {
    $summary.Add("Required chunk budget deferrals: True")
}
if ($MaxProfilerMs -gt 0) {
    $summary.Add(("Required max profiler ms after warmup: {0:N3}" -f $MaxProfilerMs))
}
$summary.Add("Profiler lines: $($profilerLines.Count) after warmup ($($allProfilerLines.Count) total)")
if ($testPassLine.Count -gt 0) {
    $summary.Add("Tests: $($testPassLine[-1])")
}
$summary.Add("")
$summary.Add("Worst profiler ticks:")
if ($worstProfilerLines.Count -eq 0) {
    $summary.Add("  none")
} else {
    foreach ($line in $worstProfilerLines) {
        $summary.Add("  $line")
    }
}
$summary.Add("")
$summary.Add("Counter totals:")
if ($counterTotals.Count -eq 0) {
    $summary.Add("  none")
} else {
    $counterTotals.GetEnumerator() |
        Sort-Object -Property Name |
        ForEach-Object { $summary.Add("  $($_.Name): $($_.Value)") }
}

Add-FiniteFluidDiagnosis $summary $counterTotals $chunkTotals $Top
Add-TopPositionSummary $summary $positionTotals "finite_fluids" "hottestFiniteFluidPositions" $Top

$summary.Add("")
$summary.Add("Top chunk hotspots:")
if ($chunkTotals.Count -eq 0) {
    $summary.Add("  none")
} else {
    $chunkTotals.GetEnumerator() |
        Sort-Object -Property @{ Expression = { $_.Value }; Descending = $true }, Name |
        Select-Object -First $Top |
        ForEach-Object { $summary.Add("  $($_.Name): $($_.Value)") }
}

$summary.Add("")
$summary.Add("Top position hotspots:")
if ($positionTotals.Count -eq 0) {
    $summary.Add("  none")
} else {
    $positionTotals.GetEnumerator() |
        Sort-Object -Property @{ Expression = { $_.Value }; Descending = $true }, Name |
        Select-Object -First $Top |
        ForEach-Object { $summary.Add("  $($_.Name): $($_.Value)") }
}

if ($failureLines.Count -gt 0) {
    $summary.Add("")
    $summary.Add("Failure hints:")
    foreach ($line in $failureLines) {
        $summary.Add("  $line")
    }
}

$summary | Set-Content -Path $summaryPath -Encoding UTF8
$summary | ForEach-Object { Write-Host $_ }

if ($exitCode -ne 0) {
    throw "Headless perf run failed with exit code $exitCode. Full log: $logPath"
}

if ($RequireInspectionDeferrals -and (Get-CounterTotal $counterTotals "finite_fluid_inspection_deferrals") -le 0) {
    throw "Expected finite fluid inspection deferrals, but none were recorded. Full log: $logPath"
}

if ($RequireBudgetDeferrals -and (Get-CounterTotal $counterTotals "finite_fluid_budget_deferrals") -le 0) {
    throw "Expected finite fluid budget deferrals, but none were recorded. Full log: $logPath"
}

if ($RequireChunkBudgetDeferrals -and (Get-CounterTotal $counterTotals "finite_fluid_budget_chunk_deferrals") -le 0) {
    throw "Expected finite fluid chunk budget deferrals, but none were recorded. Full log: $logPath"
}

$worstProfilerMs = 0.0
if ($profilerLines.Count -gt 0) {
    $worstProfilerMs = [double](@($profilerLines | ForEach-Object { Get-ProfilerValue $_ } | Sort-Object -Descending | Select-Object -First 1)[0])
}
if ($MaxProfilerMs -gt 0 -and $worstProfilerMs -gt $MaxProfilerMs) {
    throw ("Expected profiler max <= {0:N3} ms after warmup, but saw {1:N3} ms. Full log: {2}" -f $MaxProfilerMs, $worstProfilerMs, $logPath)
}
