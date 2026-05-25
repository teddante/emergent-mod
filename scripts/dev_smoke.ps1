param(
    [switch]$SkipBuild,
    [switch]$CopyToPrism,
    [switch]$RequireMinecraftSources,
    [switch]$VerboseBuildOutput,
    [string]$PrismMinecraftDir = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$MixinConfigPath = Join-Path $ProjectRoot "src\main\resources\emergent.mixins.json"
$MixinSourceDir = Join-Path $ProjectRoot "src\main\java\com\teddante\emergent\mixin"
$GameTestModJsonPath = Join-Path $ProjectRoot "src\gametest\resources\fabric.mod.json"
$GameTestSourceDir = Join-Path $ProjectRoot "src\gametest\java"
$ResourcesDir = Join-Path $ProjectRoot "src\main\resources"
$McSourceDir = Join-Path $ProjectRoot "mc-src"
$GradlePropertiesPath = Join-Path $ProjectRoot "gradle.properties"
$ConfigSourcePath = Join-Path $ProjectRoot "src\main\java\com\teddante\emergent\EmergentConfig.java"
$ConfigScreenPath = Join-Path $ProjectRoot "src\main\java\com\teddante\emergent\client\EmergentClothConfigScreen.java"
$LangPath = Join-Path $ResourcesDir "assets\emergent\lang\en_us.json"
$ReadmePath = Join-Path $ProjectRoot "README.md"

function Read-GradleProperties {
    $properties = @{}
    Get-Content -LiteralPath $GradlePropertiesPath | ForEach-Object {
        if ($_ -match "^\s*([^#][^=]+?)\s*=\s*(.*?)\s*$") {
            $properties[$Matches[1].Trim()] = $Matches[2].Trim()
        }
    }

    return $properties
}

$GradleProperties = Read-GradleProperties
$ArchivesBaseName = $GradleProperties["archives_base_name"]
$ModVersion = $GradleProperties["mod_version"]
if ([string]::IsNullOrWhiteSpace($ArchivesBaseName) -or [string]::IsNullOrWhiteSpace($ModVersion)) {
    throw "gradle.properties must define archives_base_name and mod_version."
}
if ($ModVersion -notmatch "^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$") {
    throw "mod_version must follow SemVer-style MAJOR.MINOR.PATCH, with optional pre-release/build metadata."
}

$JarPath = Join-Path $ProjectRoot "build\libs\$ArchivesBaseName-$ModVersion.jar"

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message"
}

function Write-GradleFailureSummary {
    param(
        [string[]]$BuildOutput,
        [string]$LogPath
    )

    $patterns = @(
        "All \d+ required tests passed",
        "\d+ required tests failed",
        "Game test failed",
        "failed at",
        "> Task .* FAILED",
        "BUILD FAILED",
        "Execution failed",
        "Caused by:",
        "FAILURE:"
    )

    $hints = @($BuildOutput | Where-Object {
        $line = $_
        [bool]($patterns | Where-Object { $line -match $_ } | Select-Object -First 1)
    } | Select-Object -Last 60)

    Write-Host "Gradle build failed. Full log: $LogPath"
    if ($hints.Count -gt 0) {
        Write-Host "Relevant failure lines:"
        $hints | ForEach-Object { Write-Host $_ }
        return
    }

    Write-Host "Last build log lines:"
    $BuildOutput | Select-Object -Last 80 | ForEach-Object { Write-Host $_ }
}

function ConvertTo-ConfigTranslationName {
    param([string]$Name)

    return ([regex]::Replace($Name, "([a-z0-9])([A-Z])", '$1_$2')).ToLowerInvariant()
}

function Assert-MixinConfig {
    Write-Step "Checking mixin config hygiene"

    if (-not (Test-Path -LiteralPath $MixinConfigPath)) {
        throw "Missing mixin config: $MixinConfigPath"
    }

    $config = Get-Content -LiteralPath $MixinConfigPath -Raw | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($config.refmap)) {
        throw "emergent.mixins.json must define refmap."
    }

    $declaredMixins = @($config.mixins)
    $declaredSet = @{}
    foreach ($mixin in $declaredMixins) {
        $declaredSet[$mixin] = $true
        $sourcePath = Join-Path $MixinSourceDir "$mixin.java"
        if (-not (Test-Path -LiteralPath $sourcePath)) {
            throw "Mixin listed in emergent.mixins.json has no source file: $mixin"
        }

        $source = Get-Content -LiteralPath $sourcePath
        $sourceText = $source -join "`n"
        if ($sourceText.Contains("@Mixin({")) {
            for ($i = 0; $i -lt $source.Count; $i++) {
                if ($source[$i] -notmatch "@Unique") {
                    continue
                }

                for ($j = $i + 1; $j -lt $source.Count; $j++) {
                    $line = $source[$j].Trim()
                    if ([string]::IsNullOrWhiteSpace($line)) {
                        continue
                    }

                    if ($line -match "\)\s*\{") {
                        throw "Multi-target mixin $mixin declares an @Unique helper method. Move helper logic outside the mixin to avoid runtime NoSuchMethodError."
                    }

                    break
                }
            }
        }
    }

    Get-ChildItem -LiteralPath $MixinSourceDir -Filter "*.java" | ForEach-Object {
        $className = $_.BaseName
        if (-not $declaredSet.ContainsKey($className)) {
            throw "Non-mixin helper class is inside the mixin package: $($_.FullName). Move helpers/duck interfaces outside com.teddante.emergent.mixin."
        }
    }
}

function Assert-ConfigHygiene {
    Write-Step "Checking config surface hygiene"

    $configSource = Get-Content -LiteralPath $ConfigSourcePath -Raw
    $configScreen = Get-Content -LiteralPath $ConfigScreenPath -Raw
    $lang = Get-Content -LiteralPath $LangPath -Raw
    $readme = Get-Content -LiteralPath $ReadmePath -Raw

    $fields = @([regex]::Matches($configSource, 'public boolean ([A-Za-z0-9_]+)\s*=') |
        ForEach-Object { $_.Groups[1].Value })
    if ($fields.Count -eq 0) {
        throw "No public boolean config fields found in $ConfigSourcePath"
    }

    foreach ($field in $fields) {
        $translationName = ConvertTo-ConfigTranslationName $field
        if (-not $readme.Contains("`"$field`"")) {
            throw "README config example is missing EmergentConfig field: $field"
        }
        if (-not $configScreen.Contains("config.$field")) {
            throw "Cloth Config screen is missing EmergentConfig field: $field"
        }
        if (-not $lang.Contains("`"emergent.config.$translationName`"")) {
            throw "Language file is missing config label: emergent.config.$translationName"
        }
        if (-not $lang.Contains("`"emergent.config.$translationName.tooltip`"")) {
            throw "Language file is missing config tooltip: emergent.config.$translationName.tooltip"
        }
    }

    if (-not $lang.Contains("`"emergent.config.category.presets`"")) {
        throw "Language file is missing config preset category label: emergent.config.category.presets"
    }
    if (-not $lang.Contains("`"emergent.config.preset`"")) {
        throw "Language file is missing config preset selector label: emergent.config.preset"
    }
    if (-not $lang.Contains("`"emergent.config.preset.tooltip`"")) {
        throw "Language file is missing config preset selector tooltip: emergent.config.preset.tooltip"
    }

    $presetMatch = [regex]::Match($configSource, 'public enum Preset\s*\{(?<body>[\s\S]*?)\s*;')
    if (-not $presetMatch.Success) {
        throw "EmergentConfig.Preset enum was not found."
    }

    $presets = @([regex]::Matches($presetMatch.Groups["body"].Value, '\b([A-Z][A-Z0-9_]*)\b') |
        ForEach-Object { $_.Groups[1].Value })
    if ($presets.Count -eq 0) {
        throw "EmergentConfig.Preset has no enum values."
    }

    foreach ($preset in $presets) {
        $translationName = $preset.ToLowerInvariant()
        if (-not $lang.Contains("`"emergent.config.preset.$translationName`"")) {
            throw "Language file is missing preset label: emergent.config.preset.$translationName"
        }
    }

    foreach ($preset in $presets | Where-Object { $_ -ne "CUSTOM" }) {
        $caseMatch = [regex]::Match(
                $configSource,
                "case\s+$preset\s*->\s*\{(?<body>[\s\S]*?)\n\s*\}")
        if (-not $caseMatch.Success) {
            throw "EmergentConfig.Preset.$preset has no switch case in applyPreset."
        }

        foreach ($field in $fields) {
            if ($caseMatch.Groups["body"].Value -notmatch "\b$field\s*=") {
                throw "EmergentConfig.Preset.$preset does not assign config field: $field"
            }
        }
    }
}

function Assert-GameTestEntrypoints {
    Write-Step "Checking GameTest entrypoint hygiene"

    if (-not (Test-Path -LiteralPath $GameTestModJsonPath)) {
        throw "Missing GameTest mod metadata: $GameTestModJsonPath"
    }

    $modJson = Get-Content -LiteralPath $GameTestModJsonPath -Raw | ConvertFrom-Json
    $entrypoints = @($modJson.entrypoints.'fabric-gametest')
    if ($entrypoints.Count -eq 0) {
        throw "GameTest mod metadata must define at least one fabric-gametest entrypoint."
    }

    $entrypointSet = @{}
    foreach ($entrypoint in $entrypoints) {
        if ([string]::IsNullOrWhiteSpace($entrypoint)) {
            throw "GameTest mod metadata contains a blank fabric-gametest entrypoint."
        }

        $entrypointSet[$entrypoint] = $true
        $sourcePath = Join-Path $GameTestSourceDir (($entrypoint -replace '\.', '\') + ".java")
        if (-not (Test-Path -LiteralPath $sourcePath)) {
            throw "GameTest entrypoint has no source file: $entrypoint"
        }

        $sourceText = Get-Content -LiteralPath $sourcePath -Raw
        if (-not $sourceText.Contains("@GameTest")) {
            throw "GameTest entrypoint has no @GameTest methods: $entrypoint"
        }
    }

    Get-ChildItem -LiteralPath $GameTestSourceDir -Recurse -Filter "*.java" | ForEach-Object {
        $sourceText = Get-Content -LiteralPath $_.FullName -Raw
        if (-not $sourceText.Contains("@GameTest")) {
            return
        }

        if ($sourceText -notmatch "package\s+([A-Za-z0-9_.]+)\s*;") {
            throw "GameTest source has @GameTest methods but no package declaration: $($_.FullName)"
        }
        $packageName = $Matches[1]

        if ($sourceText -notmatch "public\s+class\s+([A-Za-z0-9_]+)") {
            throw "GameTest source has @GameTest methods but no public class declaration: $($_.FullName)"
        }
        $className = $Matches[1]
        $qualifiedName = "$packageName.$className"

        if (-not $entrypointSet.ContainsKey($qualifiedName)) {
            throw "GameTest class has @GameTest methods but is not registered as a fabric-gametest entrypoint: $qualifiedName"
        }
    }
}

function Assert-ResourceHygiene {
    Write-Step "Checking resource JSON hygiene"

    Get-ChildItem -LiteralPath $ResourcesDir -Recurse -Filter "*.json" | ForEach-Object {
        $raw = Get-Content -LiteralPath $_.FullName -Raw
        try {
            $raw | ConvertFrom-Json | Out-Null
        } catch {
            throw "Invalid JSON resource: $($_.FullName): $($_.Exception.Message)"
        }

        if ($raw.Contains("minecraft:wall_redstone_torch")) {
            throw "Resource references removed block id minecraft:wall_redstone_torch. Use minecraft:redstone_wall_torch in Minecraft 26.1.2."
        }
    }

    $blocksPath = Join-Path $McSourceDir "net\minecraft\world\level\block\Blocks.java"
    $itemsPath = Join-Path $McSourceDir "net\minecraft\world\item\Items.java"
    $blockIdsPath = Join-Path $McSourceDir "net\minecraft\references\BlockIds.java"
    $itemIdsPath = Join-Path $McSourceDir "net\minecraft\references\ItemIds.java"
    $fluidPath = Join-Path $McSourceDir "net\minecraft\world\level\material\FlowingFluid.java"
    foreach ($path in @($blocksPath, $itemsPath, $blockIdsPath, $itemIdsPath, $fluidPath)) {
        if (-not (Test-Path -LiteralPath $path)) {
            if ($RequireMinecraftSources) {
                throw "Minecraft source cache is missing required official 26.1+ source: $path. Run scripts/extract_sources.ps1 after ./gradlew genSources."
            }

            Write-Warning "Minecraft source cache is missing $path; skipping vanilla registry ID validation."
            return
        }
    }

    $blockIds = @{}
    foreach ($match in [regex]::Matches((Get-Content -LiteralPath $blocksPath -Raw), 'register\(\s*"([^"]+)"')) {
        $blockIds["minecraft:$($match.Groups[1].Value)"] = $true
    }
    foreach ($match in [regex]::Matches((Get-Content -LiteralPath $blockIdsPath -Raw), 'createKey\("([^"]+)"\)')) {
        $blockIds["minecraft:$($match.Groups[1].Value)"] = $true
    }

    $itemIds = @{}
    foreach ($match in [regex]::Matches((Get-Content -LiteralPath $itemsPath -Raw), 'register(?:Item|Block)?\(\s*"([^"]+)"')) {
        $itemIds["minecraft:$($match.Groups[1].Value)"] = $true
    }
    foreach ($match in [regex]::Matches((Get-Content -LiteralPath $itemIdsPath -Raw), 'createKey\("([^"]+)"\)')) {
        $itemIds["minecraft:$($match.Groups[1].Value)"] = $true
    }
    foreach ($id in $blockIds.Keys) {
        $itemIds[$id] = $true
    }

    Get-ChildItem -LiteralPath (Join-Path $ResourcesDir "data\emergent\tags") -Recurse -Filter "*.json" | ForEach-Object {
        $json = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
        $kind = if ($_.FullName -like "*\tags\block\*") { "block" } elseif ($_.FullName -like "*\tags\item\*") { "item" } else { "other" }
        foreach ($value in @($json.values)) {
            $id = $null
            $required = $true
            if ($value -is [string]) {
                $id = $value
            } else {
                $id = $value.id
                if ($null -ne $value.required) {
                    $required = [bool]$value.required
                }
            }

            if ([string]::IsNullOrWhiteSpace($id) -or $id.StartsWith("#") -or -not $id.StartsWith("minecraft:") -or -not $required) {
                continue
            }

            if ($kind -eq "block" -and -not $blockIds.ContainsKey($id)) {
                throw "Required vanilla block tag entry does not exist in local Minecraft sources: $($_.FullName): $id"
            }
            if ($kind -eq "item" -and -not $itemIds.ContainsKey($id)) {
                throw "Required vanilla item tag entry does not exist in local Minecraft sources: $($_.FullName): $id"
            }
        }
    }
}

function Assert-JarMixinContents {
    Write-Step "Checking built jar mixin package contents"

    if (-not (Test-Path -LiteralPath $JarPath)) {
        throw "Expected built jar not found: $JarPath"
    }

    $config = Get-Content -LiteralPath $MixinConfigPath -Raw | ConvertFrom-Json
    $declaredSet = @{}
    foreach ($mixin in @($config.mixins)) {
        $declaredSet[$mixin] = $true
    }

    $entries = & jar tf $JarPath
    if ($LASTEXITCODE -ne 0) {
        throw "jar tf failed for $JarPath"
    }

    foreach ($entry in $entries) {
        if ($entry -match "^com/teddante/emergent/mixin/([^/]+)\.class$") {
            $baseName = $Matches[1] -replace "\$.*$", ""
            if (-not $declaredSet.ContainsKey($baseName)) {
                throw "Built jar contains a directly loadable helper in the mixin package: $entry"
            }
        }
    }
}

Push-Location $ProjectRoot
try {
    Assert-MixinConfig
    Assert-ResourceHygiene
    Assert-ConfigHygiene
    Assert-GameTestEntrypoints

    if (-not $SkipBuild) {
        Write-Step "Running Gradle build"
        $gradleWrapper = if ($env:OS -eq "Windows_NT") { ".\gradlew.bat" } else { "./gradlew" }
        if ($VerboseBuildOutput) {
            & $gradleWrapper build
            $gradleExitCode = $LASTEXITCODE
        } else {
            $reportDir = Join-Path $ProjectRoot "build\reports\emergent-smoke"
            New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
            $logPath = Join-Path $reportDir ("smoke-build-{0}.log" -f (Get-Date -Format "yyyyMMdd-HHmmss"))

            $oldErrorActionPreference = $ErrorActionPreference
            try {
                $ErrorActionPreference = "Continue"
                $buildOutput = @(& $gradleWrapper build *>&1 | ForEach-Object { $_.ToString() })
                $gradleExitCode = $LASTEXITCODE
            } finally {
                $ErrorActionPreference = $oldErrorActionPreference
            }

            $buildOutput | Set-Content -Path $logPath -Encoding UTF8
            if ($gradleExitCode -eq 0) {
                $buildOutput | Where-Object {
                    $_ -match "All \d+ required tests passed" -or $_ -match "BUILD SUCCESSFUL"
                } | ForEach-Object {
                    Write-Host $_
                }
            } else {
                Write-GradleFailureSummary -BuildOutput $buildOutput -LogPath $logPath
            }
        }

        if ($gradleExitCode -ne 0) {
            throw "Gradle build failed."
        }
    }

    Assert-JarMixinContents

    if ($CopyToPrism) {
        if ([string]::IsNullOrWhiteSpace($PrismMinecraftDir)) {
            $PrismMinecraftDir = Join-Path $env:APPDATA "PrismLauncher\instances\Prism Launcher Thing for Emergent mod testing\minecraft"
        }

        $modsDir = Join-Path $PrismMinecraftDir "mods"
        if (-not (Test-Path -LiteralPath $modsDir)) {
            throw "Prism mods folder not found: $modsDir"
        }

        $targetJar = Join-Path $modsDir "$ArchivesBaseName-$ModVersion.jar"
        Write-Step "Copying jar to Prism mods folder"
        Copy-Item -LiteralPath $JarPath -Destination $targetJar -Force
        Write-Host "Copied: $targetJar"
    }

    Write-Host "Smoke checks passed."
} finally {
    Pop-Location
}
