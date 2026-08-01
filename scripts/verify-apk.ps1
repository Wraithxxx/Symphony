[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath
)

$ErrorActionPreference = "Stop"
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path

$sdkRoot = $env:ANDROID_SDK_ROOT
if (-not $sdkRoot) {
    $localProperties = Join-Path $repositoryRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1
        if ($sdkLine) {
            $sdkRoot = ($sdkLine -split '=', 2)[1].Replace('\:', ':').Replace('\\', '\')
        }
    }
}
if (-not $sdkRoot) {
    $sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

$buildToolsRoot = Join-Path $sdkRoot "build-tools"
$buildTools = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
if (-not $buildTools) { throw "Android build tools not found under $buildToolsRoot" }

$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
$aapt = Join-Path $buildTools.FullName "aapt.exe"
$zipalign = Join-Path $buildTools.FullName "zipalign.exe"

& $apksigner verify --verbose --print-certs $resolvedApk
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed" }
& $zipalign -c -P 16 -v 4 $resolvedApk
if ($LASTEXITCODE -ne 0) { throw "APK alignment verification failed" }
& $aapt dump badging $resolvedApk
if ($LASTEXITCODE -ne 0) { throw "APK manifest inspection failed" }

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedApk).Hash.ToLowerInvariant()
Write-Output "SHA-256: $hash"
