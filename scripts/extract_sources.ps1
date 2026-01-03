$ErrorActionPreference = "Stop"

$ProjectRoot = Get-Location
$LoomCache = Join-Path $ProjectRoot ".gradle\loom-cache\minecraftMaven"
$DestDir = Join-Path $ProjectRoot "mc-src"

Write-Host "Searching for source JAR in: $LoomCache"

# Find the sources JAR
$SourceJar = Get-ChildItem -Path $LoomCache -Recurse -Filter "*sources.jar" | Select-Object -First 1

if ($null -eq $SourceJar) {
    Write-Error "Could not find Minecraft sources JAR. Make sure to run './gradlew genSources' first."
}

Write-Host "Found sources JAR: $($SourceJar.FullName)"

# Create destination directory
if (-not (Test-Path $DestDir)) {
    New-Item -ItemType Directory -Path $DestDir | Out-Null
    Write-Host "Created directory: $DestDir"
}

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

Write-Host "Extraction complete."
Write-Host "You can now reference source code in: $DestDir"
