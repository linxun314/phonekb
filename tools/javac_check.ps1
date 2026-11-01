$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$bt = (Get-ChildItem (Join-Path $sdk "build-tools") -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$platform = (Get-ChildItem (Join-Path $sdk "platforms") -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$androidJar = Join-Path $platform "android.jar"
$src = Join-Path (Get-Location) "app\src\main\java"
$classes = Join-Path (Get-Location) "build\classes"
$gen = Join-Path (Get-Location) "build\gen"
$outDir = Join-Path (Get-Location) "build"
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$files = @(Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$args2 = @("--release", "17", "-encoding", "UTF-8", "-d", $classes, "-classpath", ($androidJar + ";" + $gen)) + $files
$p = Start-Process -FilePath "javac" -ArgumentList $args2 -NoNewWindow -Wait -PassThru -RedirectStandardOutput (Join-Path $outDir "javac.out") -RedirectStandardError (Join-Path $outDir "javac.err")
Write-Output ("javac exit: " + $p.ExitCode)
Get-Content (Join-Path $outDir "javac.err") -Encoding Default | Select-Object -First 60
Get-Content (Join-Path $outDir "javac.out") -Encoding Default | Select-Object -First 60