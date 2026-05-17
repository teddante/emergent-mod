param(
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [int]$Top = 12,
    [int]$WarmupTicks = 0,
    [string]$SummaryPath = ""
)

$ErrorActionPreference = "Stop"

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
    $hasScheduleCounters = $activeSchedules -gt 0 -or $quietSkips -gt 0 -or
            $CounterTotals.ContainsKey("finite_fluid_active_schedules") -or
            $CounterTotals.ContainsKey("finite_fluid_quiet_schedule_skips")
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
    if (!$hasScheduleCounters) {
        $Summary.Add("  scheduleCounters=missing; this log was probably captured before active/quiet schedule counters were added.")
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

    Add-TopChunkSummary $Summary $ChunkTotals "finite_fluids" "hottestFiniteFluidChunks" $Top
    Add-TopChunkSummary $Summary $ChunkTotals "finite_water" "hottestFiniteWaterChunks" $Top
    Add-TopChunkSummary $Summary $ChunkTotals "finite_lava" "hottestFiniteLavaChunks" $Top

    if (!$hasScheduleCounters) {
        $Summary.Add("  interpretation=older profiler format; retest with the latest jar before deciding whether wakeups are stale or active.")
    } elseif ($budgetDeferrals -gt 0) {
        $Summary.Add("  interpretation=finite-fluid neighbour-scan/active work exceeded the per-tick budget and was deferred fairly; inspect chunk hotspots before raising the budget.")
    } elseif ($quietPercent -ge 65.0 -and $workPercent -ge 35.0) {
        $Summary.Add("  interpretation=mixed active movement plus many quiet wakeups; inspect hotspot chunks before changing simulation pacing.")
    } elseif ($quietPercent -ge 65.0 -and $activeSchedules -gt 0) {
        $Summary.Add("  interpretation=mostly quiet wakeups; inspect top quiet reason and chunk hotspots for stale rescheduling.")
    } elseif ($workPercent -ge 35.0) {
        $Summary.Add("  interpretation=mostly active movement/reactions; optimize the hotspot geometry or movement algorithm before adding caps.")
    } else {
        $Summary.Add("  interpretation=mixed workload; compare Prism chunk hotspots with headless synthetic scenarios.")
    }
}

function Add-LagCorrelation([System.Collections.Generic.List[string]]$Summary, [array]$LagLines, [array]$ProfilerLines) {
    $Summary.Add("")
    $Summary.Add("Minecraft lag warnings:")
    if ($LagLines.Count -eq 0) {
        $Summary.Add("  none")
        return
    }

    $maxRunningMs = 0L
    $maxBehindTicks = 0L
    foreach ($line in $LagLines) {
        if ($line -match "Running ([0-9]+)ms or ([0-9]+) ticks behind") {
            $maxRunningMs = [Math]::Max($maxRunningMs, [long]$Matches[1])
            $maxBehindTicks = [Math]::Max($maxBehindTicks, [long]$Matches[2])
        }
    }

    $maxProfilerMs = 0.0
    foreach ($line in $ProfilerLines) {
        $maxProfilerMs = [Math]::Max($maxProfilerMs, (Get-ProfilerValue $line))
    }

    $Summary.Add(("  count={0} maxRunningMs={1} maxBehindTicks={2} maxEmergentProfilerMs={3:N3}" -f `
                $LagLines.Count, $maxRunningMs, $maxBehindTicks, $maxProfilerMs))

    if ($ProfilerLines.Count -eq 0) {
        $Summary.Add("  interpretation=lag warnings exist but no Emergent profiler lines were found; lower slowMs or investigate non-Emergent work.")
    } elseif ($maxRunningMs -gt 0 -and ($maxProfilerMs * 4.0) -lt $maxRunningMs) {
        $Summary.Add("  interpretation=lag warnings are much larger than Emergent profiler spikes; likely outside instrumented Emergent systems or hidden inside unprofiled code.")
    } else {
        $Summary.Add("  interpretation=Emergent profiler spikes are in the same order as lag warnings; inspect subsystem and chunk summaries above.")
    }
}

$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
$logLines = Get-Content -LiteralPath $resolvedPath
$allProfilerLines = @($logLines | Where-Object { $_ -like "*Emergent profiler:*" })
$profilerLines = @($allProfilerLines | Where-Object { (Get-ProfilerTick $_) -gt $WarmupTicks })
$lagLines = @($logLines | Where-Object { $_ -like "*Can't keep up!*" })
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
$summary.Add("Emergent profiler log summary")
$summary.Add("Log: $resolvedPath")
$summary.Add("Warmup ticks ignored: $WarmupTicks")
$summary.Add("Profiler lines: $($profilerLines.Count) after warmup ($($allProfilerLines.Count) total)")
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
Add-LagCorrelation $summary $lagLines $profilerLines

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

if ($SummaryPath -ne "") {
    $summary | Set-Content -Path $SummaryPath -Encoding UTF8
}
$summary | ForEach-Object { Write-Host $_ }
