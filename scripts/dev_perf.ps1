param(
    [int]$SlowMs = 10,
    [int]$Top = 12,
    [int]$WarmupTicks = 20,
    [int]$ActiveFluidBudget = 0,
    [switch]$RequireBudgetDeferrals,
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
    if ($Line -notmatch "counters=(.*?)(?= chunks=| heat=|\))") {
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
    if ($Line -notmatch "chunks=(.*?)(?= heat=|\))") {
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

function Add-FiniteFluidDiagnosis([System.Collections.Generic.List[string]]$Summary, [hashtable]$CounterTotals, [hashtable]$ChunkTotals, [int]$Top) {
    $finiteTicks = Get-CounterTotal $CounterTotals "finite_fluid_ticks"
    if ($finiteTicks -le 0) {
        return
    }

    $waterTicks = Get-CounterTotal $CounterTotals "finite_fluid_water_ticks"
    $lavaTicks = Get-CounterTotal $CounterTotals "finite_fluid_lava_ticks"
    $activeSchedules = Get-CounterTotal $CounterTotals "finite_fluid_active_schedules"
    $quietSkips = Get-CounterTotal $CounterTotals "finite_fluid_quiet_schedule_skips"
    $budgetClaims = Get-CounterTotal $CounterTotals "finite_fluid_budget_claims"
    $budgetDeferrals = Get-CounterTotal $CounterTotals "finite_fluid_budget_deferrals"
    $horizontalMoves = Get-CounterTotal $CounterTotals "finite_fluid_horizontal_moves"
    $downwardMoves = Get-CounterTotal $CounterTotals "finite_fluid_downward_moves"
    $thermalReactions = Get-CounterTotal $CounterTotals "finite_fluid_thermal_reactions"
    $thermalQuietSkips = Get-CounterTotal $CounterTotals "finite_fluid_water_thermal_quiet_skips"
    $thinSettled = Get-CounterTotal $CounterTotals "finite_fluid_thin_settled"
    $stableSources = Get-CounterTotal $CounterTotals "finite_fluid_stable_sources"
    $quietTickSkips = Get-CounterTotal $CounterTotals "finite_fluid_quiet_tick_skips"
    $quietCacheHits = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_hits"

    $workEvents = $horizontalMoves + $downwardMoves + $thermalReactions
    $quietDenominator = [Math]::Max(1L, $activeSchedules + $quietSkips)
    $quietPercent = ($quietSkips * 100.0) / $quietDenominator
    $workPercent = ($workEvents * 100.0) / [Math]::Max(1L, $finiteTicks)

    $Summary.Add("")
    $Summary.Add("Finite fluid diagnosis:")
    $Summary.Add(("  ticks={0} water={1} lava={2} activeSchedules={3} quietSkips={4} quietRatio={5:N1}% workEvents={6} workPerTick={7:N1}%" -f `
                $finiteTicks, $waterTicks, $lavaTicks, $activeSchedules, $quietSkips, $quietPercent, $workEvents, $workPercent))
    $Summary.Add(("  budgetClaims={0} budgetDeferrals={1}" -f $budgetClaims, $budgetDeferrals))
    $Summary.Add(("  settledThin={0} stableSources={1} quietTickSkips={2} quietCacheHits={3} thermalQuietSkips={4} horizontalMoves={5} downwardMoves={6} thermalReactions={7}" -f `
                $thinSettled, $stableSources, $quietTickSkips, $quietCacheHits, $thermalQuietSkips, $horizontalMoves, $downwardMoves, $thermalReactions))

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

    Add-TopChunkSummary $Summary $ChunkTotals "finite_fluids" "hottestFiniteFluidChunks" $Top
    Add-TopChunkSummary $Summary $ChunkTotals "finite_water" "hottestFiniteWaterChunks" $Top
    Add-TopChunkSummary $Summary $ChunkTotals "finite_lava" "hottestFiniteLavaChunks" $Top

    if ($budgetDeferrals -gt 0) {
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

Write-Step "Running headless GameTests with Emergent profiler slowMs=$SlowMs stress=$stressScenariosEnabled"
$oldJavaToolOptions = $env:JAVA_TOOL_OPTIONS
$activeFluidBudgetOption = if ($ActiveFluidBudget -gt 0) { " -Demergent.finiteFluid.activeTickBudget=$ActiveFluidBudget" } else { "" }
$env:JAVA_TOOL_OPTIONS = "-Demergent.profiler=true -Demergent.profiler.slowMs=$SlowMs -Demergent.perfScenarios=$($stressScenariosEnabled.ToString().ToLowerInvariant())$activeFluidBudgetOption"
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
foreach ($line in $profilerLines) {
    Add-Counters $line $counterTotals
    Add-Chunks $line $chunkTotals
}

$worstProfilerLines = @($profilerLines |
    Sort-Object -Property @{ Expression = { Get-ProfilerValue $_ }; Descending = $true } |
    Select-Object -First $Top)

$summary = New-Object System.Collections.Generic.List[string]
$summary.Add("Emergent headless perf summary")
$summary.Add("Log: $logPath")
$summary.Add("Profiler slowMs: $SlowMs")
$summary.Add("Warmup ticks ignored: $WarmupTicks")
$summary.Add("Stress scenarios: $stressScenariosEnabled")
if ($ActiveFluidBudget -gt 0) {
    $summary.Add("Forced finite fluid work budget: $ActiveFluidBudget")
}
if ($RequireBudgetDeferrals) {
    $summary.Add("Required budget deferrals: True")
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

if ($RequireBudgetDeferrals -and (Get-CounterTotal $counterTotals "finite_fluid_budget_deferrals") -le 0) {
    throw "Expected finite fluid budget deferrals, but none were recorded. Full log: $logPath"
}
