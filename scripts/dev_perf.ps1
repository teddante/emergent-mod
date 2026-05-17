param(
    [int]$SlowMs = 10,
    [int]$Top = 12,
    [int]$WarmupTicks = 20,
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
$env:JAVA_TOOL_OPTIONS = "-Demergent.profiler=true -Demergent.profiler.slowMs=$SlowMs -Demergent.perfScenarios=$($stressScenariosEnabled.ToString().ToLowerInvariant())"
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
