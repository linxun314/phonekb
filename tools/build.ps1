param(
    [switch]$Clean,
    [string]$Module = "app",
    [string]$PackageName = "com.phonekb",
    [string]$OutName = "PhoneKB",
    [string]$VersionName = "0.36.2",
    [int]$VersionCode = 59,
    [int]$MinSdk = 28,
    [int]$TargetSdk = 36
)
# Builds the Android module of this repo without Gradle:
#   app -> build\PhoneKB.apk
# Usage:
#   .\tools\build.ps1 -Clean
#   .\tools\build.ps1 -Clean -VersionName 0.36.2 -VersionCode 59
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path $sdk)) { throw "Android SDK not found: $sdk" }
$bt = (Get-ChildItem (Join-Path $sdk "build-tools") -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$platform = (Get-ChildItem (Join-Path $sdk "platforms") -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$androidJar = Join-Path $platform "android.jar"
$aapt2 = Join-Path $bt "aapt2.exe"
$d8 = Join-Path $bt "d8.bat"
$zipalign = Join-Path $bt "zipalign.exe"
$apksigner = Join-Path $bt "apksigner.bat"
$project = Join-Path $root $Module
$src = Join-Path $project "src\main\java"
$res = Join-Path $project "src\main\res"
$manifest = Join-Path $project "src\main\AndroidManifest.xml"
if (-not (Test-Path $manifest)) { throw "Manifest not found: $manifest" }
$build = Join-Path $root "build"
$work = Join-Path $build $Module
if ($Clean -and (Test-Path $work)) { Remove-Item -Recurse -Force $work }
$gen = Join-Path $work "gen"; $classes = Join-Path $work "classes"; $dexDir = Join-Path $work "dex"
foreach ($d in @($gen, $classes, $dexDir)) { if (Test-Path $d) { Remove-Item -Recurse -Force $d }; New-Item -ItemType Directory -Force -Path $d | Out-Null }
# Optional jars under <module>\libs are compiled against and packaged into the APK.
$libJars = @(Get-ChildItem -Path (Join-Path $project "libs") -Filter *.jar -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
# The source manifests have no package attribute (required by AGP 8+); aapt2 still needs one,
# so layer it into a build-local copy instead of keeping two manifests in the repo.
$manifestForAapt = Join-Path $work "AndroidManifest.xml"
$manifestText = [System.IO.File]::ReadAllText($manifest)
$manifestText = $manifestText -replace '<manifest ', ('<manifest package="' + $PackageName + '" ')
[System.IO.File]::WriteAllText($manifestForAapt, $manifestText, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "[$Module 1/8] aapt2 compile"
$resZip = Join-Path $work "res.zip"
& $aapt2 compile --dir $res -o $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
Write-Host "[$Module 2/8] aapt2 link"
$unsigned = Join-Path $work "base.apk"
& $aapt2 link -o $unsigned -I $androidJar --manifest $manifestForAapt --java $gen --min-sdk-version $MinSdk --target-sdk-version $TargetSdk --version-code $VersionCode --version-name $VersionName --replace-version $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }
Write-Host "[$Module 3/8] javac"
$javaFiles = @(Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$classpath = "$androidJar;$gen" + (($libJars | ForEach-Object { ";$_" }) -join "")
$ErrorActionPreference = "Continue"
$javacLog = & javac --release 17 -encoding UTF-8 -d $classes -classpath $classpath @javaFiles 2>&1 | Out-String
$javacExit = $LASTEXITCODE
$ErrorActionPreference = "Stop"
if ($javacExit -ne 0) { Write-Host $javacLog; throw "javac failed" }
Write-Host "[$Module 4/8] jar classes"
$classesJar = Join-Path $work "classes.jar"
& jar --create --file $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
Write-Host "[$Module 5/8] d8"
$d8Inputs = @($classesJar) + $libJars
& $d8 --release --min-api $MinSdk --lib $androidJar --output $dexDir @d8Inputs
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[$Module 6/8] jar update classes.dex"
$zip = Join-Path $work "base-unsigned.apk"
Copy-Item $unsigned $zip -Force
$dexFiles = @(Get-ChildItem -Path $dexDir -Filter *.dex | Sort-Object Name)
foreach ($dex in $dexFiles) {
    & jar --update --file $zip -C $dexDir $dex.Name
    if ($LASTEXITCODE -ne 0) { throw "jar update failed: $($dex.Name)" }
}

Write-Host "[$Module 7/8] zipalign"
$aligned = Join-Path $work "base-aligned.apk"
& $zipalign -f 4 $zip $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[$Module 8/8] sign"
$ks = Join-Path $root "keys\debug.keystore"
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ks) | Out-Null
    & keytool -genkeypair -keystore $ks -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}
$out = Join-Path $build "$OutName.apk"
& $apksigner sign --ks $ks --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out $out $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
$size = [math]::Round((Get-Item $out).Length / 1KB)
Write-Host "DONE: $out ($size KB)"