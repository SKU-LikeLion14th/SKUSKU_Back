param(
    [ValidateSet("summary", "config", "validate")]
    [string]$Mode = "summary"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")
$javaRoot = Join-Path $repoRoot "src\main\java"
$resourceRoot = Join-Path $repoRoot "src\main\resources"
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$buildFile = Join-Path $repoRoot "build.gradle"

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host ("== " + $Title + " ==")
}

function Get-JavaInfo {
    try {
        $javaVersion = & java -version 2>&1
        return ($javaVersion | Select-Object -First 2)
    } catch {
        return @("java command not found")
    }
}

function Get-ConfigKeys {
    $valueMatches = Get-ChildItem -Path $javaRoot -Recurse -Filter *.java |
        Select-String -Pattern '@Value\("\$\{([^}]+)\}"\)' -AllMatches |
        ForEach-Object {
            foreach ($match in $_.Matches) {
                $match.Groups[1].Value
            }
        }

    $prefixMatches = Get-ChildItem -Path $javaRoot -Recurse -Filter *.java |
        Select-String -Pattern '@ConfigurationProperties\(prefix = "([^"]+)"\)' -AllMatches |
        ForEach-Object {
            foreach ($match in $_.Matches) {
                $match.Groups[1].Value + ".*"
            }
        }

    $knownFamilies = @(
        "spring.datasource.*",
        "spring.data.redis.*",
        "spring.mail.*",
        "spring.security.oauth2.client.*"
    )

    return ($valueMatches + $prefixMatches + $knownFamilies | Sort-Object -Unique)
}

function Get-ToolchainTarget {
    if (-not (Test-Path $buildFile)) {
        return $null
    }

    $match = Select-String -Path $buildFile -Pattern 'JavaLanguageVersion\.of\((\d+)\)' | Select-Object -First 1
    if ($null -eq $match) {
        return $null
    }

    return $match.Matches[0].Groups[1].Value
}

function Get-ResourceFiles {
    if (Test-Path $resourceRoot) {
        Get-ChildItem -Path $resourceRoot -File -Include "application*.yml", "application*.yaml", "application*.properties" |
            Select-Object -ExpandProperty FullName
    }
}

function Get-RootConfigFiles {
    Get-ChildItem -Path $repoRoot -File -Include ".env", ".env.*", "*.properties", "*.yml", "*.yaml" |
        Select-Object -ExpandProperty FullName
}

function Test-Port {
    param([int]$Port)
    try {
        Test-NetConnection -ComputerName "127.0.0.1" -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue
    } catch {
        $false
    }
}

function Write-Summary {
    Write-Section "Repository"
    Write-Host ("Root: " + $repoRoot)
    Write-Host ("Gradle wrapper: " + (Test-Path $gradleWrapper))
    Write-Host ("Resources dir exists: " + (Test-Path $resourceRoot))

    Write-Section "Java"
    Write-Host ("JAVA_HOME: " + ($(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "<not set>" })))
    $toolchainTarget = Get-ToolchainTarget
    if ($toolchainTarget) {
        Write-Host ("Configured toolchain target: Java " + $toolchainTarget)
    }
    Get-JavaInfo | ForEach-Object { Write-Host $_ }

    Write-Section "Application Config Files"
    $resourceFiles = @(Get-ResourceFiles)
    $rootFiles = @(Get-RootConfigFiles)
    if ($resourceFiles.Count -eq 0 -and $rootFiles.Count -eq 0) {
        Write-Host "No application config files detected in repo root or src/main/resources."
    } else {
        foreach ($file in ($resourceFiles + $rootFiles | Sort-Object -Unique)) {
            Write-Host $file
        }
    }

    Write-Section "Likely Required Config Keys"
    Get-ConfigKeys | ForEach-Object { Write-Host $_ }

    Write-Section "Local Infra Reachability"
    Write-Host ("Redis 127.0.0.1:6379 reachable: " + (Test-Port -Port 6379))
    Write-Host ("MySQL 127.0.0.1:3306 reachable: " + (Test-Port -Port 3306))

    Write-Section "Codebase Size"
    $controllers = @(Get-ChildItem -Path (Join-Path $javaRoot "com\sku_sku\backend\controller") -Recurse -Filter *.java -ErrorAction SilentlyContinue)
    $services = @(Get-ChildItem -Path (Join-Path $javaRoot "com\sku_sku\backend\service") -Recurse -Filter *.java -ErrorAction SilentlyContinue)
    $domains = @(Get-ChildItem -Path (Join-Path $javaRoot "com\sku_sku\backend\domain") -Recurse -Filter *.java -ErrorAction SilentlyContinue)
    Write-Host ("Controllers: " + $controllers.Count)
    Write-Host ("Services: " + $services.Count)
    Write-Host ("Domain classes: " + $domains.Count)
}

function Write-ConfigOnly {
    Write-Section "Likely Required Config Keys"
    Get-ConfigKeys | ForEach-Object { Write-Host $_ }
}

function Invoke-Validation {
    Write-Summary
    Write-Section "Gradle Validation"
    if (-not (Test-Path $gradleWrapper)) {
        Write-Host "gradlew.bat not found."
        return
    }

    Push-Location $repoRoot
    try {
        & $gradleWrapper "test" "--no-daemon"
        $exitCode = $LASTEXITCODE
        Write-Host ("Gradle exit code: " + $exitCode)
        if ($exitCode -ne 0) {
            exit $exitCode
        }
    } finally {
        Pop-Location
    }
}

switch ($Mode) {
    "summary" { Write-Summary }
    "config" { Write-ConfigOnly }
    "validate" { Invoke-Validation }
}
