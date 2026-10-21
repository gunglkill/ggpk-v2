$ErrorActionPreference = 'Stop'
$SDK = 'F:\AndroidBuild\sdk'
$BT = "$SDK\build-tools\34.0.0"
$PLATFORM = "$SDK\platforms\android-34\android.jar"
$JDK = 'F:\AndroidBuild\jdk\jdk17.0.20_12'
$env:JAVA_HOME = $JDK
$env:PATH = "$JDK\bin;$env:PATH"

$APP = 'C:\Users\N1zelix\AppData\Roaming\AutoClaw-oversea-official\accounts\a70b9889721dd7b4333ec1fabf4c04adf6de9528f3ea4d6e20a83a727d0f91b5\projects\Default\cloud\app'
Set-Location $APP

$OUT = "$APP\out"
if (Test-Path $OUT) { Remove-Item $OUT -Recurse -Force }
New-Item -ItemType Directory -Path $OUT | Out-Null
New-Item -ItemType Directory -Path "$OUT\gen" | Out-Null
New-Item -ItemType Directory -Path "$OUT\classes" | Out-Null
New-Item -ItemType Directory -Path "$OUT\dex" | Out-Null

Write-Host 'step 1: aapt2 compile'
& "$BT\aapt2.exe" compile --dir res -o "$OUT\res.zip"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host 'step 2: aapt2 link'
& "$BT\aapt2.exe" link -o "$OUT\base.apk" -I $PLATFORM --manifest AndroidManifest.xml -R "$OUT\res.zip" --java "$OUT\gen" --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name 1.0 --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host 'step 3: javac'
$src = Get-ChildItem "$APP\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$rjava = Get-ChildItem "$OUT\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& "$JDK\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $PLATFORM -d "$OUT\classes" $src $rjava
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host 'step 4: d8'
$classes = Get-ChildItem "$OUT\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& "$BT\d8.bat" --lib $PLATFORM --min-api 26 --output "$OUT\dex" $classes
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host 'step 5: pack dex'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Copy-Item "$OUT\base.apk" "$OUT\app.apk" -Force
$zip = [System.IO.Compression.ZipFile]::Open("$OUT\app.apk", 'Update')
$dex = "$OUT\dex\classes.dex"
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dex, 'classes.dex', 'Optimal') | Out-Null
$zip.Dispose()

Write-Host 'step 6: zipalign + sign'
& "$BT\zipalign.exe" -f 4 "$OUT\app.apk" "$OUT\app-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$KS = "$APP\ggpk.jks"
if (-not (Test-Path $KS)) {
    & "$JDK\bin\keytool.exe" -genkeypair -keystore $KS -alias ggpk -keyalg RSA -keysize 2048 -validity 10000 -storepass ggpk12345 -keypass ggpk12345 -dname "CN=GGPK Schedule, O=GGPK, C=RU" | Out-Null
}

& "$BT\apksigner.bat" sign --ks $KS --ks-pass pass:ggpk12345 --key-pass pass:ggpk12345 --ks-key-alias ggpk --out "$APP\RaspisanieGGPK.apk" "$OUT\app-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

& "$BT\apksigner.bat" verify --print-certs "$APP\RaspisanieGGPK.apk" | Select-Object -First 4
Get-Item "$APP\RaspisanieGGPK.apk" | Select-Object Name, Length
