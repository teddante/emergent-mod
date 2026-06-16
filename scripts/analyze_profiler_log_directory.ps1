param(
    [Parameter(Mandatory = $true)]
    [string]$Directory,
    [int]$TopFiles = 12,
    [int]$TopChunks = 3,
    [int]$WarmupTicks = 0
)

$ErrorActionPreference = "Stop"

function Get-LogLines([string]$ResolvedPath) {
    if (!$ResolvedPath.EndsWith(".gz", [System.StringComparison]::OrdinalIgnoreCase)) {
        return @(Get-Content -LiteralPath $ResolvedPath)
    }

    $fileStream = [IO.File]::OpenRead($ResolvedPath)
    try {
        $gzipStream = [IO.Compression.GzipStream]::new($fileStream, [IO.Compression.CompressionMode]::Decompress)
        try {
            $reader = [IO.StreamReader]::new($gzipStream)
            try {
                $lines = New-Object System.Collections.Generic.List[string]
                while (!$reader.EndOfStream) {
                    $lines.Add($reader.ReadLine())
                }
                return @($lines)
            } finally {
                $reader.Dispose()
            }
        } finally {
            if ($gzipStream) {
                $gzipStream.Dispose()
            }
        }
    } finally {
        if ($fileStream) {
            $fileStream.Dispose()
        }
    }
}

function Get-PrismCopyInfo([string]$Directory) {
    $resolved = (Resolve-Path -LiteralPath $Directory).Path
    if ((Split-Path -Leaf $resolved) -ne "logs") {
        return $null
    }

    $minecraftDir = Split-Path -Parent $resolved
    $copyInfoPath = Join-Path $minecraftDir "mods\emergent-copy-info.txt"
    if (-not (Test-Path -LiteralPath $copyInfoPath)) {
        return $null
    }

    $lines = @(Get-Content -LiteralPath $copyInfoPath | Select-Object -First 16)
    $values = @{}
    foreach ($line in $lines) {
        $separator = $line.IndexOf("=")
        if ($separator -le 0) {
            continue
        }
        $values[$line.Substring(0, $separator)] = $line.Substring($separator + 1)
    }

    $copiedUtc = $null
    if ($values.ContainsKey("copiedUtc")) {
        $parsed = [datetime]::MinValue
        if ([datetime]::TryParse(
                $values["copiedUtc"],
                [Globalization.CultureInfo]::InvariantCulture,
                [Globalization.DateTimeStyles]::AssumeUniversal -bor [Globalization.DateTimeStyles]::AdjustToUniversal,
                [ref]$parsed)) {
            $copiedUtc = $parsed
        }
    }

    [PSCustomObject]@{
        Lines = $lines
        CopiedUtc = $copiedUtc
        Path = $copyInfoPath
    }
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

    foreach ($match in [regex]::Matches($Matches[1], "([A-Za-z0-9_]+):([0-9]+)")) {
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

    foreach ($match in [regex]::Matches($Matches[1], "([A-Za-z0-9_]+)@(-?[0-9]+,-?[0-9]+):([0-9]+)")) {
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

    foreach ($match in [regex]::Matches($Matches[1], "([A-Za-z0-9_]+)@(-?[0-9]+,-?[0-9]+,-?[0-9]+):([0-9]+)")) {
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

function Get-TopChunkText([hashtable]$ChunkTotals, [int]$TopChunks) {
    $top = @($ChunkTotals.GetEnumerator() |
        Where-Object { $_.Name -like "finite_fluids@*" } |
        Sort-Object -Property @{ Expression = { $_.Value }; Descending = $true }, Name |
        Select-Object -First $TopChunks)
    if ($top.Count -eq 0) {
        return "-"
    }
    return ($top | ForEach-Object { "$($_.Name):$($_.Value)" }) -join " "
}

function Get-TopPositionText([hashtable]$PositionTotals, [int]$TopChunks) {
    $top = @($PositionTotals.GetEnumerator() |
        Where-Object { $_.Name -like "finite_fluids@*" } |
        Sort-Object -Property @{ Expression = { $_.Value }; Descending = $true }, Name |
        Select-Object -First $TopChunks)
    if ($top.Count -eq 0) {
        return "-"
    }

    return ($top | ForEach-Object { "$($_.Name):$($_.Value)" }) -join " "
}

function Get-TopInvalidationText([hashtable]$CounterTotals) {
    $reasons = @(
        @{ Name = "environmental_memory_update"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_environmental_memory_update" },
        @{ Name = "environmental_memory_stale"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_environmental_memory_stale" },
        @{ Name = "environmental_memory_decay"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_environmental_memory_decay" },
        @{ Name = "environmental_memory_clear"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_environmental_memory_clear" },
        @{ Name = "block_update"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_block_update" },
        @{ Name = "neighbor_update"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_neighbor_update" },
        @{ Name = "unknown"; Value = Get-CounterTotal $CounterTotals "finite_fluid_quiet_cache_invalidation_unknown" }
    ) | Sort-Object -Property @{ Expression = { $_.Value }; Descending = $true }
    $top = $reasons | Where-Object { $_.Value -gt 0 } | Select-Object -First 1
    if (!$top) {
        return "-"
    }

    return "$($top.Name):$($top.Value)"
}

function Get-StartupDiagnostics([array]$LogLines) {
    $profilerEnabled = $false
    $slowMs = ""
    $activeBudget = ""
    $chunkBudget = ""
    $inspectionBudget = ""
    $inspectionChunkBudget = ""
    $positionHotspots = ""

    foreach ($line in $LogLines) {
        if ($line -match "Emergent profiler enabled\. Slow tick threshold: (.+?) ms") {
            $profilerEnabled = $true
            $slowMs = $Matches[1]
        } elseif ($line -match "Emergent profiler position hotspots: (enabled|disabled)") {
            $positionHotspots = $Matches[1]
        } elseif ($line -match "Emergent finite fluid work budget: ([0-9]+) cells/tick") {
            $activeBudget = $Matches[1]
        } elseif ($line -match "Emergent finite fluid chunk work budget: ([0-9]+) cells/chunk/tick") {
            $chunkBudget = $Matches[1]
        } elseif ($line -match "Emergent finite fluid inspection budget: ([0-9]+) cells/tick and ([0-9]+) cells/chunk/tick") {
            $inspectionBudget = $Matches[1]
            $inspectionChunkBudget = $Matches[2]
        }
    }

    [PSCustomObject]@{
        ProfilerEnabled = $profilerEnabled
        SlowMs = $slowMs
        ActiveBudget = $activeBudget
        ChunkBudget = $chunkBudget
        InspectionBudget = $inspectionBudget
        InspectionChunkBudget = $inspectionChunkBudget
        PositionHotspots = $positionHotspots
    }
}

function Measure-Log($File, [int]$WarmupTicks, [int]$TopChunks) {
    $lines = Get-LogLines $File.FullName
    $startup = Get-StartupDiagnostics $lines
    $profilerLines = @($lines | Where-Object {
        $_ -like "*Emergent profiler:*" -and (Get-ProfilerTick $_) -gt $WarmupTicks
    })
    $lagLines = @($lines | Where-Object { $_ -like "*Can't keep up!*" })
    $counterTotals = @{}
    $chunkTotals = @{}
    $positionTotals = @{}
    $maxProfilerMs = 0.0

    foreach ($line in $profilerLines) {
        $maxProfilerMs = [Math]::Max($maxProfilerMs, (Get-ProfilerValue $line))
        Add-Counters $line $counterTotals
        Add-Chunks $line $chunkTotals
        Add-Positions $line $positionTotals
    }

    $maxRunningMs = 0L
    $maxBehindTicks = 0L
    foreach ($line in $lagLines) {
        if ($line -match "Running ([0-9]+)ms or ([0-9]+) ticks behind") {
            $maxRunningMs = [Math]::Max($maxRunningMs, [long]$Matches[1])
            $maxBehindTicks = [Math]::Max($maxBehindTicks, [long]$Matches[2])
        }
    }

    $hasBudgetCounters = $counterTotals.ContainsKey("finite_fluid_budget_claims") -or
            $counterTotals.ContainsKey("finite_fluid_budget_deferrals") -or
            $counterTotals.ContainsKey("finite_fluid_budget_chunk_claims") -or
            $counterTotals.ContainsKey("finite_fluid_budget_chunk_deferrals")
    $hasInspectionCounters = $counterTotals.ContainsKey("finite_fluid_inspection_claims") -or
            $counterTotals.ContainsKey("finite_fluid_inspection_deferrals") -or
            $counterTotals.ContainsKey("finite_fluid_inspection_chunk_claims") -or
            $counterTotals.ContainsKey("finite_fluid_inspection_chunk_deferrals")
    $hasQuietCacheCounters = $counterTotals.ContainsKey("finite_fluid_quiet_cache_hits") -or
            $counterTotals.ContainsKey("finite_fluid_quiet_cache_entry_misses") -or
            $counterTotals.ContainsKey("finite_fluid_quiet_cache_no_cache_misses") -or
            $counterTotals.ContainsKey("finite_fluid_quiet_cache_fluid_misses") -or
            $counterTotals.ContainsKey("finite_fluid_quiet_cache_amount_misses") -or
            $counterTotals.ContainsKey("finite_fluid_quiet_cache_signature_misses") -or
            $counterTotals.ContainsKey("finite_fluid_quiet_cache_invalidations")
    $finiteTicks = Get-CounterTotal $counterTotals "finite_fluid_ticks"
    $lavaTicks = Get-CounterTotal $counterTotals "finite_fluid_lava_ticks"
    $lavaHeat = Get-CounterTotal $counterTotals "finite_fluid_lava_heat"
    $budgetDeferrals = Get-CounterTotal $counterTotals "finite_fluid_budget_deferrals"
    $chunkDeferrals = Get-CounterTotal $counterTotals "finite_fluid_budget_chunk_deferrals"
    $inspectionClaims = Get-CounterTotal $counterTotals "finite_fluid_inspection_claims"
    $quietCacheHits = Get-CounterTotal $counterTotals "finite_fluid_quiet_cache_hits"
    $quietCacheMisses = [Math]::Max(0L, $inspectionClaims - $quietCacheHits)
    $quietCacheNoCacheMisses = Get-CounterTotal $counterTotals "finite_fluid_quiet_cache_no_cache_misses"
    $quietCacheEntryMisses = Get-CounterTotal $counterTotals "finite_fluid_quiet_cache_entry_misses"
    $quietCacheFluidMisses = Get-CounterTotal $counterTotals "finite_fluid_quiet_cache_fluid_misses"
    $quietCacheAmountMisses = Get-CounterTotal $counterTotals "finite_fluid_quiet_cache_amount_misses"
    $quietCacheSignatureMisses = Get-CounterTotal $counterTotals "finite_fluid_quiet_cache_signature_misses"
    $quietCacheInvalidations = Get-CounterTotal $counterTotals "finite_fluid_quiet_cache_invalidations"
    $quietCacheInvalidatedEntries = Get-CounterTotal $counterTotals "finite_fluid_quiet_cache_invalidated_entries"
    $quietCacheEvictions = Get-CounterTotal $counterTotals "finite_fluid_quiet_cache_evictions"

    $format = if ($profilerLines.Count -eq 0) {
        "no-profiler"
    } elseif (!$hasBudgetCounters) {
        "pre-budget"
    } elseif (!$hasQuietCacheCounters) {
        "pre-cache"
    } elseif (!$hasInspectionCounters) {
        "pre-inspection-budget"
    } else {
        "current"
    }

    [PSCustomObject]@{
        Name = $File.Name
        LastWriteTime = $File.LastWriteTime
        LastWriteTimeUtc = $File.LastWriteTimeUtc
        ProfilerLines = $profilerLines.Count
        LagWarnings = $lagLines.Count
        MaxProfilerMs = $maxProfilerMs
        MaxLagMs = $maxRunningMs
        MaxBehindTicks = $maxBehindTicks
        FiniteTicks = $finiteTicks
        LavaTicks = $lavaTicks
        LavaHeat = $lavaHeat
        BudgetDeferrals = $budgetDeferrals
        ChunkDeferrals = $chunkDeferrals
        QuietCacheHits = $quietCacheHits
        EstimatedQuietCacheMisses = $quietCacheMisses
        QuietCacheNoCacheMisses = $quietCacheNoCacheMisses
        QuietCacheEntryMisses = $quietCacheEntryMisses
        QuietCacheFluidMisses = $quietCacheFluidMisses
        QuietCacheAmountMisses = $quietCacheAmountMisses
        QuietCacheSignatureMisses = $quietCacheSignatureMisses
        QuietCacheInvalidations = $quietCacheInvalidations
        QuietCacheInvalidatedEntries = $quietCacheInvalidatedEntries
        QuietCacheEvictions = $quietCacheEvictions
        Format = $format
        StartupProfilerEnabled = $startup.ProfilerEnabled
        StartupSlowMs = $startup.SlowMs
        StartupActiveBudget = $startup.ActiveBudget
        StartupChunkBudget = $startup.ChunkBudget
        StartupInspectionBudget = $startup.InspectionBudget
        StartupInspectionChunkBudget = $startup.InspectionChunkBudget
        StartupPositionHotspots = $startup.PositionHotspots
        TopChunks = Get-TopChunkText $chunkTotals $TopChunks
        TopPositions = Get-TopPositionText $positionTotals $TopChunks
        TopInvalidation = Get-TopInvalidationText $counterTotals
    }
}

$resolvedDirectory = (Resolve-Path -LiteralPath $Directory).Path
$files = @(Get-ChildItem -LiteralPath $resolvedDirectory -File |
    Where-Object { $_.Name -like "*.log" -or $_.Name -like "*.log.gz" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First $TopFiles)

$summary = New-Object System.Collections.Generic.List[string]
$summary.Add("Emergent profiler log directory summary")
$summary.Add("Directory: $resolvedDirectory")
$copyInfo = Get-PrismCopyInfo $resolvedDirectory
if ($null -ne $copyInfo -and $copyInfo.Lines.Count -gt 0) {
    $summary.Add("Latest Prism copy: $($copyInfo.Lines -join ' ')")
}
$summary.Add("Files scanned: $($files.Count)")
$summary.Add("Warmup ticks ignored: $WarmupTicks")
$summary.Add("")

if ($files.Count -eq 0) {
    $summary.Add("No Prism log files found.")
} else {
    $measurements = @($files | ForEach-Object { Measure-Log $_ $WarmupTicks $TopChunks })
    foreach ($item in $measurements) {
        $startupText = "profiler=" + $(if ($item.StartupProfilerEnabled) { "on" } else { "off" }) +
                " slowMs=" + $(if ($item.StartupSlowMs -ne "") { $item.StartupSlowMs } else { "-" }) +
                " positions=" + $(if ($item.StartupPositionHotspots -ne "") { $item.StartupPositionHotspots } else { "-" }) +
                " budget=" + $(if ($item.StartupActiveBudget -ne "") { $item.StartupActiveBudget } else { "-" }) +
                "/" + $(if ($item.StartupChunkBudget -ne "") { $item.StartupChunkBudget } else { "-" }) +
                " inspection=" + $(if ($item.StartupInspectionBudget -ne "") { $item.StartupInspectionBudget } else { "-" }) +
                "/" + $(if ($item.StartupInspectionChunkBudget -ne "") { $item.StartupInspectionChunkBudget } else { "-" })
        $lavaHeatPercent = ($item.LavaHeat * 100.0) / [Math]::Max(1L, $item.LavaTicks)
        $lavaText = if ($item.LavaTicks -gt 0 -or $item.LavaHeat -gt 0) {
            " lavaTicks=$($item.LavaTicks) lavaHeat=$($item.LavaHeat) lavaHeatPerLavaTick=$($lavaHeatPercent.ToString('N1'))%"
        } else {
            ""
        }
        $summary.Add(("{0} [{1}] startup=({2}) profiler={3} maxMs={4:N3} lag={5} maxLagMs={6} behind={7} finiteTicks={8} budgetDeferrals={9} chunkDeferrals={10} quietCache={11}/{12} missBreakdown(noCache/entry/fluid/amount/signature)={13}/{14}/{15}/{16}/{17} invalidations={18}/{19} evictions={20}{21}" -f `
                    $item.Name,
                    $item.Format,
                    $startupText,
                    $item.ProfilerLines,
                    $item.MaxProfilerMs,
                    $item.LagWarnings,
                    $item.MaxLagMs,
                    $item.MaxBehindTicks,
                    $item.FiniteTicks,
                    $item.BudgetDeferrals,
                    $item.ChunkDeferrals,
                    $item.QuietCacheHits,
                    $item.EstimatedQuietCacheMisses,
                    $item.QuietCacheNoCacheMisses,
                    $item.QuietCacheEntryMisses,
                    $item.QuietCacheFluidMisses,
                    $item.QuietCacheAmountMisses,
                    $item.QuietCacheSignatureMisses,
                    $item.QuietCacheInvalidations,
                    $item.QuietCacheInvalidatedEntries,
                    $item.QuietCacheEvictions,
                    $lavaText))
        if ($item.TopChunks -ne "-") {
            $summary.Add("  topChunks=$($item.TopChunks)")
        }
        if ($item.TopPositions -ne "-") {
            $summary.Add("  topPositions=$($item.TopPositions)")
        }
        if ($item.TopInvalidation -ne "-") {
            $summary.Add("  topInvalidationReason=$($item.TopInvalidation)")
        }
    }

    $currentLogs = @($measurements | Where-Object { $_.Format -eq "current" })
    $preBudgetLogs = @($measurements | Where-Object { $_.Format -eq "pre-budget" })
    $preInspectionLogs = @($measurements | Where-Object { $_.Format -eq "pre-inspection-budget" })
    $lagOnlyLogs = @($measurements | Where-Object { $_.LagWarnings -gt 0 -and $_.ProfilerLines -eq 0 })
    $latestMeasurement = $measurements | Select-Object -First 1
    $latestLog = $measurements | Where-Object { $_.Name -eq "latest.log" } | Select-Object -First 1
    $summary.Add("")
    $summary.Add(("Format counts: current={0} pre-inspection-budget={1} pre-budget={2} pre-cache={3} no-profiler={4}" -f `
                $currentLogs.Count,
                $preInspectionLogs.Count,
                $preBudgetLogs.Count,
                @($measurements | Where-Object { $_.Format -eq "pre-cache" }).Count,
                @($measurements | Where-Object { $_.Format -eq "no-profiler" }).Count))
    if ($latestLog -and $copyInfo -and $copyInfo.CopiedUtc -and $latestLog.LastWriteTimeUtc -lt $copyInfo.CopiedUtc) {
        $summary.Add(("interpretation=latest.log is older than the latest copied Emergent jar ({0:u} < {1:u}); launch Prism once before using these logs to judge the copied build." -f `
                    $latestLog.LastWriteTimeUtc,
                    $copyInfo.CopiedUtc))
    } elseif ($latestMeasurement -and $latestMeasurement.LagWarnings -gt 0 -and !$latestMeasurement.StartupProfilerEnabled) {
        $summary.Add("interpretation=latest scanned log has lag warnings but no Emergent profiler startup line; enable -Demergent.profiler=true -Demergent.profiler.slowMs=25 on the Prism instance and retest the latest jar before tuning budgets.")
    } elseif ($currentLogs.Count -eq 0 -and $preBudgetLogs.Count -gt 0) {
        $summary.Add("interpretation=no current-format finite-fluid profiler log was found; retest with the latest copied jar before tuning budgets.")
    } elseif ($currentLogs.Count -eq 0 -and $preInspectionLogs.Count -gt 0) {
        $summary.Add("interpretation=logs have active-work budgets but not inspection-budget counters; retest with the latest copied jar before tuning scheduled tick admission.")
    } elseif ($lagOnlyLogs.Count -gt 0 -and @($measurements | Where-Object { $_.StartupProfilerEnabled }).Count -eq 0) {
        $summary.Add("interpretation=lag warnings exist, but no scanned log shows the Emergent profiler startup line; enable -Demergent.profiler=true before using these logs for Emergent budget tuning.")
    } elseif ($lagOnlyLogs.Count -gt 0) {
        $summary.Add("interpretation=some lag warnings have no Emergent profiler line; compare with Minecraft/other-mod load before changing Emergent budgets.")
    } elseif ($currentLogs.Count -gt 0) {
        $summary.Add("interpretation=current-format logs are available; inspect the highest maxMs file with analyze_profiler_log.ps1 before changing simulation pacing.")
    }
}

$summary | ForEach-Object { Write-Host $_ }
