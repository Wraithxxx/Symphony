[CmdletBinding()]
param(
    [ValidateSet("debug", "release")]
    [string]$Variant = "debug",
    [string]$SigningEnvironment = "secrets/symphony-release.env",
    [switch]$Unsigned,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path

function Import-SigningEnvironment([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Signing environment not found: $path"
    }

    foreach ($rawLine in Get-Content -LiteralPath $path) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#")) { continue }
        $parts = $line.Split("=", 2)
        if ($parts.Count -ne 2 -or -not $parts[0].Trim()) {
            throw "Invalid signing environment entry. Expected NAME=VALUE."
        }
        [Environment]::SetEnvironmentVariable(
            $parts[0].Trim(),
            $parts[1].Trim().Trim('"').Trim("'"),
            "Process"
        )
    }
}

Push-Location $repositoryRoot
try {
    if ($Variant -eq "release" -and -not $Unsigned) {
        Import-SigningEnvironment (Join-Path $repositoryRoot $SigningEnvironment)
        $required = @(
            "SIGNING_KEYSTORE_FILE",
            "SIGNING_KEYSTORE_PASSWORD",
            "SIGNING_KEY_ALIAS",
            "SIGNING_KEY_PASSWORD"
        )
        $missing = $required | Where-Object {
            [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, "Process"))
        }
        if ($missing) {
            throw "Signing environment is incomplete: $($missing -join ', ')"
        }
    }

    $tasks = @()
    if ($Clean) { $tasks += "clean" }
    $tasks += ":app:testDebugUnitTest"
    $tasks += ":app:lintDebug"
    if ($Variant -eq "release") {
        $tasks += ":app:assembleRelease"
    } else {
        $tasks += ":app:assembleDebug"
    }

    & .\gradlew.bat @tasks --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
