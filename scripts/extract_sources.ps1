$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$LoomCache = Join-Path $ProjectRoot ".gradle\loom-cache\minecraftMaven"
$DestDir = Join-Path $ProjectRoot "mc-src"
$GradlePropertiesPath = Join-Path $ProjectRoot "gradle.properties"

function Read-GradleProperty {
    param([string]$Name)

    $line = Get-Content -LiteralPath $GradlePropertiesPath |
        Where-Object { $_ -match "^\s*$([regex]::Escape($Name))\s*=" } |
        Select-Object -First 1

    if ($null -eq $line) {
        throw "gradle.properties must define $Name."
    }

    return ($line -split "=", 2)[1].Trim()
}

$MinecraftVersion = Read-GradleProperty "minecraft_version"

Write-Host "Searching for source JAR in: $LoomCache"

# Find the current unobfuscated Mojang-name source JAR. Minecraft 26.1+ no
# longer uses Yarn as the normal source namespace for Fabric development.
$SourceJar = Get-ChildItem -Path $LoomCache -Recurse -Filter "*sources.jar" |
    Where-Object {
        $_.FullName -like "*\$MinecraftVersion\*" -and
        $_.FullName -notmatch "yarn"
    } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $SourceJar) {
    Write-Error "Could not find official Minecraft $MinecraftVersion sources JAR. Run './gradlew genSources' first."
}

Write-Host "Found sources JAR: $($SourceJar.FullName)"

if (Test-Path -LiteralPath $DestDir) {
    $resolvedDest = Resolve-Path -LiteralPath $DestDir
    if (-not $resolvedDest.Path.StartsWith($ProjectRoot.Path, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clear source cache outside project root: $resolvedDest"
    }

    Remove-Item -LiteralPath $DestDir -Recurse -Force
}

New-Item -ItemType Directory -Path $DestDir | Out-Null
Write-Host "Created clean directory: $DestDir"

# Extract
Write-Host "Extracting sources to: $DestDir"
Write-Host "This may take a moment..."

# Use the 'jar' command if available, otherwise fallback to Expand-Archive (slower but built-in)
if (Get-Command "jar" -ErrorAction SilentlyContinue) {
    Push-Location $DestDir
    try {
        # Only extract the net/minecraft folder to verify relevance and save time/space if possible, 
        # but for full context we usually want everything. The jar command extracts everything by default if no args.
        # We will extract everything to ensure we have all context.
        & jar xf $($SourceJar.FullName)
    }
    finally {
        Pop-Location
    }
} else {
    Write-Warning "'jar' command not found on PATH. Falling back to Expand-Archive (this is slower)."
    Expand-Archive -Path $SourceJar.FullName -DestinationPath $DestDir -Force
}

$ExpectedSource = Join-Path $DestDir "net\minecraft\world\level\material\FlowingFluid.java"
if (-not (Test-Path -LiteralPath $ExpectedSource)) {
    throw "Extracted sources do not look like Minecraft 26.1+ official names. Missing: $ExpectedSource"
}

Write-Host "Extraction complete."
Write-Host "You can now reference source code in: $DestDir"
