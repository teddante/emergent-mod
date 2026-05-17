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
    if ($Line -notmatch "chunks=(.*?)(?= heat=|\))") {
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

function Measure-Log($File, [int]$WarmupTicks, [int]$TopChunks) {
    $lines = Get-LogLines $File.FullName
    $profilerLines = @($lines | Where-Object {
        $_ -like "*Emergent profiler:*" -and (Get-ProfilerTick $_) -gt $WarmupTicks
    })
    $lagLines = @($lines | Where-Object { $_ -like "*Can't keep up!*" })
    $counterTotals = @{}
    $chunkTotals = @{}
    $maxProfilerMs = 0.0

    foreach ($line in $profilerLines) {
        $maxProfilerMs = [Math]::Max($maxProfilerMs, (Get-ProfilerValue $line))
        Add-Counters $line $counterTotals
        Add-Chunks $line $chunkTotals
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
    $hasQuietCacheCounters = $counterTotals.ContainsKey("finite_fluid_quiet_cache_hits")
    $finiteTicks = Get-CounterTotal $counterTotals "finite_fluid_ticks"
    $budgetDeferrals = Get-CounterTotal $counterTotals "finite_fluid_budget_deferrals"
    $chunkDeferrals = Get-CounterTotal $counterTotals "finite_fluid_budget_chunk_deferrals"

    $format = if ($profilerLines.Count -eq 0) {
        "no-profiler"
    } elseif (!$hasBudgetCounters) {
        "pre-budget"
    } elseif (!$hasQuietCacheCounters) {
        "pre-cache"
    } else {
        "current"
    }

    [PSCustomObject]@{
        Name = $File.Name
        LastWriteTime = $File.LastWriteTime
        ProfilerLines = $profilerLines.Count
        LagWarnings = $lagLines.Count
        MaxProfilerMs = $maxProfilerMs
        MaxLagMs = $maxRunningMs
        MaxBehindTicks = $maxBehindTicks
        FiniteTicks = $finiteTicks
        BudgetDeferrals = $budgetDeferrals
        ChunkDeferrals = $chunkDeferrals
        Format = $format
        TopChunks = Get-TopChunkText $chunkTotals $TopChunks
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
$summary.Add("Files scanned: $($files.Count)")
$summary.Add("Warmup ticks ignored: $WarmupTicks")
$summary.Add("")

if ($files.Count -eq 0) {
    $summary.Add("No Prism log files found.")
} else {
    $measurements = @($files | ForEach-Object { Measure-Log $_ $WarmupTicks $TopChunks })
    foreach ($item in $measurements) {
        $summary.Add(("{0} [{1}] profiler={2} maxMs={3:N3} lag={4} maxLagMs={5} behind={6} finiteTicks={7} budgetDeferrals={8} chunkDeferrals={9}" -f `
                    $item.Name,
                    $item.Format,
                    $item.ProfilerLines,
                    $item.MaxProfilerMs,
                    $item.LagWarnings,
                    $item.MaxLagMs,
                    $item.MaxBehindTicks,
                    $item.FiniteTicks,
                    $item.BudgetDeferrals,
                    $item.ChunkDeferrals))
        if ($item.TopChunks -ne "-") {
            $summary.Add("  topChunks=$($item.TopChunks)")
        }
    }

    $currentLogs = @($measurements | Where-Object { $_.Format -eq "current" })
    $preBudgetLogs = @($measurements | Where-Object { $_.Format -eq "pre-budget" })
    $lagOnlyLogs = @($measurements | Where-Object { $_.LagWarnings -gt 0 -and $_.ProfilerLines -eq 0 })
    $summary.Add("")
    $summary.Add(("Format counts: current={0} pre-budget={1} pre-cache={2} no-profiler={3}" -f `
                $currentLogs.Count,
                $preBudgetLogs.Count,
                @($measurements | Where-Object { $_.Format -eq "pre-cache" }).Count,
                @($measurements | Where-Object { $_.Format -eq "no-profiler" }).Count))
    if ($currentLogs.Count -eq 0 -and $preBudgetLogs.Count -gt 0) {
        $summary.Add("interpretation=no current-format finite-fluid profiler log was found; retest with the latest copied jar before tuning budgets.")
    } elseif ($lagOnlyLogs.Count -gt 0) {
        $summary.Add("interpretation=some lag warnings have no Emergent profiler line; compare with Minecraft/other-mod load before changing Emergent budgets.")
    } elseif ($currentLogs.Count -gt 0) {
        $summary.Add("interpretation=current-format logs are available; inspect the highest maxMs file with analyze_profiler_log.ps1 before changing simulation pacing.")
    }
}

$summary | ForEach-Object { Write-Host $_ }
