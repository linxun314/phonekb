param(
    [switch]$Clean,
    [string]$VersionName = "0.1.0",
    [int]$VersionCode = 1
)
# Builds the iPad-oriented app (separate module, separate package).
#   app-pad -> build\PhoneKB-Pad.apk
# Usage:
#   .\tools\build-pad.ps1 -Clean
#   .\tools\build-pad.ps1 -Clean -VersionName 0.1.0 -VersionCode 1
$ErrorActionPreference = "Stop"
$script = Join-Path $PSScriptRoot "build.ps1"
if ($Clean) {
    & $script -Clean -Module app-pad -PackageName com.phonekb.pad -OutName PhoneKB-Pad -VersionName $VersionName -VersionCode $VersionCode
} else {
    & $script -Module app-pad -PackageName com.phonekb.pad -OutName PhoneKB-Pad -VersionName $VersionName -VersionCode $VersionCode
}
if ($LASTEXITCODE -ne 0) { throw "build-pad failed" }
