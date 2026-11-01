param([switch]$Clean, [string]$VersionName = "0.12.1", [int]$VersionCode = 17)
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
$project = Join-Path $root "app"
$src = Join-Path $project "src\main\java"
$res = Join-Path $project "src\main\res"
$manifest = Join-Path $project "src\main\AndroidManifest.xml"
$build = Join-Path $root "build"
if ($Clean -and (Test-Path $build)) { Remove-Item -Recurse -Force $build }
$gen = Join-Path $build "gen"; $classes = Join-Path $build "classes"; $dexDir = Join-Path $build "dex"
foreach ($d in @($gen, $classes, $dexDir)) { if (Test-Path $d) { Remove-Item -Recurse -Force $d }; New-Item -ItemType Directory -Force -Path $d | Out-Null }
# The source manifest has no package attribute (required by AGP 8+); aapt2 still needs one,
# so layer it into a build-local copy instead of keeping two manifests in the repo.
$manifestForAapt = Join-Path $build "AndroidManifest.xml"
$manifestText = [System.IO.File]::ReadAllText($manifest)
$manifestText = $manifestText -replace '<manifest ', '<manifest package="com.phonekb" '
[System.IO.File]::WriteAllText($manifestForAapt, $manifestText, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "[1/8] aapt2 compile"
$resZip = Join-Path $build "res.zip"
& $aapt2 compile --dir $res -o $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
Write-Host "[2/8] aapt2 link"
$unsigned = Join-Path $build "base.apk"
& $aapt2 link -o $unsigned -I $androidJar --manifest $manifestForAapt --java $gen --min-sdk-version 28 --target-sdk-version 36 --version-code $VersionCode --version-name $VersionName --replace-version $resZip
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }
Write-Host "[3/8] javac"
$javaFiles = @(Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$classpath = "$androidJar;$gen"
$ErrorActionPreference = "Continue"
$javacLog = & javac --release 17 -encoding UTF-8 -d $classes -classpath $classpath @javaFiles 2>&1 | Out-String
$javacExit = $LASTEXITCODE
$ErrorActionPreference = "Stop"
if ($javacExit -ne 0) { Write-Host $javacLog; throw "javac failed" }
Write-Host "[4/8] jar classes"
$classesJar = Join-Path $build "classes.jar"
& jar --create --file $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
Write-Host "[5/8] d8"
& $d8 --release --min-api 28 --lib $androidJar --output $dexDir $classesJar
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[6/8] jar update classes.dex"
$zip = Join-Path $build "base-unsigned.apk"
Copy-Item $unsigned $zip -Force
& jar --update --file $zip -C $dexDir classes.dex
if ($LASTEXITCODE -ne 0) { throw "jar update failed" }

Write-Host "[7/8] zipalign"
$aligned = Join-Path $build "base-aligned.apk"
& $zipalign -f 4 $zip $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[8/8] sign"
$ks = Join-Path $root "keys\debug.keystore"
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ks) | Out-Null
    & keytool -genkeypair -keystore $ks -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}
$out = Join-Path $build "PhoneKB.apk"
& $apksigner sign --ks $ks --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out $out $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }
$size = [math]::Round((Get-Item $out).Length / 1KB)
Write-Host "DONE: $out ($size KB)"
