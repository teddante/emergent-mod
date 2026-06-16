param(
    [switch]$SkipBuild,
    [switch]$CopyToPrism,
    [switch]$RequireMinecraftSources,
    [string]$PrismMinecraftDir = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$MixinConfigPath = Join-Path $ProjectRoot "src\main\resources\emergent.mixins.json"
$MixinSourceDir = Join-Path $ProjectRoot "src\main\java\com\teddante\emergent\mixin"
$ResourcesDir = Join-Path $ProjectRoot "src\main\resources"
$McSourceDir = Join-Path $ProjectRoot "mc-src"
$GradlePropertiesPath = Join-Path $ProjectRoot "gradle.properties"
$GitHubDir = Join-Path $ProjectRoot ".github"
$ExtractSourcesScript = Join-Path $ProjectRoot "scripts\extract_sources.ps1"
$GradleWrapper = if ([System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT) {
    Join-Path $ProjectRoot "gradlew.bat"
} else {
    Join-Path $ProjectRoot "gradlew"
}

function Read-GradleProperties {
    if (-not (Test-Path -LiteralPath $GradlePropertiesPath)) {
        throw "Missing Gradle properties file: $GradlePropertiesPath"
    }

    $properties = @{}
    Get-Content -LiteralPath $GradlePropertiesPath | ForEach-Object {
        $line = $_.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
            return
        }

        $separator = $line.IndexOf("=")
        if ($separator -lt 1) {
            return
        }

        $key = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()
        $properties[$key] = $value
    }

    return $properties
}

$GradleProperties = Read-GradleProperties
$ArchiveBaseName = $GradleProperties["archives_base_name"]
$ModVersion = $GradleProperties["mod_version"]
if ([string]::IsNullOrWhiteSpace($ArchiveBaseName) -or [string]::IsNullOrWhiteSpace($ModVersion)) {
    throw "gradle.properties must define archives_base_name and mod_version."
}
if ($ModVersion -notmatch "^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z.-]+)?(\+[0-9A-Za-z.-]+)?$") {
    throw "mod_version must follow SemVer-style MAJOR.MINOR.PATCH, with optional pre-release/build metadata."
}
$JarFileName = "$ArchiveBaseName-$ModVersion.jar"
$JarPath = Join-Path $ProjectRoot "build\libs\$JarFileName"

function Write-Step {
    param([string]$Message)
    Write-Host "==> $Message"
}

function Test-MinecraftSourceCache {
    $requiredSources = @(
        "net\minecraft\block\Blocks.java",
        "net\minecraft\block\BlockKeys.java",
        "net\minecraft\item\Items.java",
        "net\minecraft\item\ItemKeys.java"
    )

    foreach ($relativePath in $requiredSources) {
        if (-not (Test-Path -LiteralPath (Join-Path $McSourceDir $relativePath))) {
            return $false
        }
    }

    return $true
}

function Ensure-MinecraftSourceCache {
    if (Test-MinecraftSourceCache) {
        return
    }

    if (-not (Test-Path -LiteralPath $ExtractSourcesScript)) {
        throw "Minecraft source cache is missing and extract_sources.ps1 was not found: $ExtractSourcesScript"
    }

    Write-Step "Generating Minecraft source cache"
    & $GradleWrapper genSources
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle genSources failed."
    }

    & $ExtractSourcesScript
    if ($LASTEXITCODE -ne 0) {
        throw "Minecraft source extraction failed."
    }

    if (-not (Test-MinecraftSourceCache)) {
        throw "Minecraft source cache is still missing required registry sources after extraction."
    }
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

    if (-not (Test-MinecraftSourceCache)) {
        if ($RequireMinecraftSources) {
            Ensure-MinecraftSourceCache
        } else {
            Write-Warning "Minecraft source cache is missing; skipping required vanilla registry ID validation. Run scripts/extract_sources.ps1 or pass -RequireMinecraftSources for the full local gate."
            return
        }
    }

    $blocksPath = Join-Path $McSourceDir "net\minecraft\block\Blocks.java"
    $blockKeysPath = Join-Path $McSourceDir "net\minecraft\block\BlockKeys.java"
    $itemsPath = Join-Path $McSourceDir "net\minecraft\item\Items.java"
    $itemKeysPath = Join-Path $McSourceDir "net\minecraft\item\ItemKeys.java"
    foreach ($path in @($blocksPath, $blockKeysPath, $itemsPath, $itemKeysPath)) {
        if (-not (Test-Path -LiteralPath $path)) {
            throw "Minecraft source cache is missing required registry source: $path"
        }
    }

    $blockIds = @{}
    foreach ($match in [regex]::Matches((Get-Content -LiteralPath $blocksPath -Raw), 'register\(\s*"([^"]+)"')) {
        $blockIds["minecraft:$($match.Groups[1].Value)"] = $true
    }
    foreach ($match in [regex]::Matches((Get-Content -LiteralPath $blockKeysPath -Raw), 'of\("([^"]+)"\)')) {
        $blockIds["minecraft:$($match.Groups[1].Value)"] = $true
    }

    $itemIds = @{}
    foreach ($match in [regex]::Matches((Get-Content -LiteralPath $itemsPath -Raw), 'register\(\s*"([^"]+)"')) {
        $itemIds["minecraft:$($match.Groups[1].Value)"] = $true
    }
    foreach ($match in [regex]::Matches((Get-Content -LiteralPath $itemKeysPath -Raw), 'of\("([^"]+)"\)')) {
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

function Assert-RepositoryWorkflowHygiene {
    Write-Step "Checking GitHub workflow hygiene"

    $requiredFiles = @(
        ".github\workflows\build.yml",
        ".github\workflows\release.yml",
        ".github\workflows\dependency-graph.yml",
        ".github\ISSUE_TEMPLATE\bug_report.yml",
        ".github\ISSUE_TEMPLATE\feature_request.yml",
        ".github\ISSUE_TEMPLATE\config.yml",
        ".github\pull_request_template.md",
        ".github\release.yml",
        "CHANGELOG.md",
        "CONTRIBUTING.md",
        "SECURITY.md",
        "docs\REPOSITORY_MAINTENANCE.md",
        "docs\VERSIONING.md",
        "AGENTS.md"
    )

    foreach ($relativePath in $requiredFiles) {
        $path = Join-Path $ProjectRoot $relativePath
        if (-not (Test-Path -LiteralPath $path)) {
            throw "Required repository workflow file is missing: $relativePath"
        }
    }

    $trackedFiles = & git -C $ProjectRoot ls-files
    if ($LASTEXITCODE -ne 0) {
        throw "git ls-files failed while checking repository workflow hygiene."
    }
    if ($trackedFiles | Where-Object { $_ -ceq "agents.md" }) {
        throw "Use AGENTS.md with this exact casing so GitHub/Linux tooling sees the agent instructions."
    }

    $workflowText = Get-ChildItem -LiteralPath (Join-Path $GitHubDir "workflows") -Filter "*.yml" | ForEach-Object {
        Get-Content -LiteralPath $_.FullName -Raw
    }
    $workflowText = $workflowText -join "`n"

    if ($workflowText -match "java-version:\s*['`"]?21") {
        throw "GitHub workflows must not use Java 21 for Minecraft 26.1.x. Use Java 25."
    }

    $buildWorkflow = Get-Content -LiteralPath (Join-Path $GitHubDir "workflows\build.yml") -Raw
    foreach ($required in @("actions/checkout@v6", "actions/setup-java@v5", "java-version: `"25`"", "gradle/actions/setup-gradle@v6", "./scripts/dev_smoke.ps1", "!build/libs/*-sources.jar")) {
        if (-not $buildWorkflow.Contains($required)) {
            throw "Build workflow is missing expected entry: $required"
        }
    }

    $releaseWorkflow = Get-Content -LiteralPath (Join-Path $GitHubDir "workflows\release.yml") -Raw
    foreach ($required in @("tags:", '"v*"', "gh release create", "--verify-tag", "--generate-notes")) {
        if (-not $releaseWorkflow.Contains($required)) {
            throw "Release workflow is missing expected entry: $required"
        }
    }

    $dependencyWorkflow = Get-Content -LiteralPath (Join-Path $GitHubDir "workflows\dependency-graph.yml") -Raw
    if (-not $dependencyWorkflow.Contains("gradle/actions/dependency-submission@v6")) {
        throw "Dependency graph workflow must submit Gradle dependencies to GitHub."
    }

    $gradleProperties = Get-Content -LiteralPath $GradlePropertiesPath -Raw
    if ($gradleProperties -match "loom_version=.*SNAPSHOT") {
        throw "gradle.properties should use a stable Fabric Loom version for release-ready development."
    }
    if ($gradleProperties -match "org\.gradle\.java\.installations\.paths=.*[A-Za-z]:") {
        throw "gradle.properties must not commit a machine-specific Windows Java installation path."
    }

    $wrapperProperties = Get-Content -LiteralPath (Join-Path $ProjectRoot "gradle\wrapper\gradle-wrapper.properties") -Raw
    if (-not $wrapperProperties.Contains("gradle-9.4.0-bin.zip")) {
        throw "Gradle wrapper should stay on the Fabric-recommended 9.4.0 baseline unless intentionally upgraded."
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
    Assert-RepositoryWorkflowHygiene
    Assert-ResourceHygiene

    if (-not $SkipBuild) {
        Write-Step "Running Gradle build"
        & $GradleWrapper build
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed."
        }
    }

    Assert-JarMixinContents

    if ($CopyToPrism) {
        if ([string]::IsNullOrWhiteSpace($PrismMinecraftDir)) {
            $PrismMinecraftDir = if ([string]::IsNullOrWhiteSpace($env:APPDATA)) {
                ""
            } else {
                Join-Path $env:APPDATA "PrismLauncher\instances\Fabulously Optimized(1)\minecraft"
            }
        }
        if ([string]::IsNullOrWhiteSpace($PrismMinecraftDir)) {
            throw "No Prism Minecraft directory was provided. Pass -PrismMinecraftDir when using -CopyToPrism."
        }

        $modsDir = Join-Path $PrismMinecraftDir "mods"
        if (-not (Test-Path -LiteralPath $modsDir)) {
            throw "Prism mods folder not found: $modsDir"
        }

        $targetJar = Join-Path $modsDir $JarFileName
        Write-Step "Copying jar to Prism mods folder"
        Copy-Item -LiteralPath $JarPath -Destination $targetJar -Force
        Write-Host "Copied: $targetJar"
    }

    Write-Host "Smoke checks passed."
} finally {
    Pop-Location
}
