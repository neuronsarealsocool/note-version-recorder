param([string]$Tools = (Join-Path $PSScriptRoot '../build-tools'), [string]$JavaBin = 'C:/Program Files/Eclipse Adoptium/jre-21.0.10.7-hotspot/bin')
$ErrorActionPreference = 'Stop'
$Tools = [IO.Path]::GetFullPath($Tools)
$sdk = Join-Path $Tools 'sdk-build-tools35/android-15'
$platform = Join-Path $Tools 'platform35/android-35/android.jar'
$java = Join-Path $JavaBin 'java.exe'
$out = Join-Path $PSScriptRoot 'build'
$classes = Join-Path $out 'classes'
$dex = Join-Path $out 'dex'
New-Item -ItemType Directory -Path $classes,$dex -Force | Out-Null
function Check-Exit([string]$Step) {if ($LASTEXITCODE -ne 0) {throw "$Step failed ($LASTEXITCODE)"}}
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
& $java -jar (Join-Path $Tools 'ecj.jar') -source 1.8 -target 1.8 -proc:none -encoding UTF-8 -classpath $platform -d $classes @sources
Check-Exit 'Java compile'
$classZip = Join-Path $out 'classes.zip'
if (Test-Path -LiteralPath $classZip) {Remove-Item -LiteralPath $classZip}
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory($classes,$classZip)
& $java -cp (Join-Path $sdk 'lib/d8.jar') com.android.tools.r8.D8 --min-api 26 --lib $platform --output $dex $classZip
Check-Exit 'DEX compile'
$unsigned = Join-Path $out 'unsigned.apk'
& (Join-Path $sdk 'aapt2.exe') compile --dir (Join-Path $PSScriptRoot 'res') -o (Join-Path $out 'resources.zip')
Check-Exit 'Resource compile'
& (Join-Path $sdk 'aapt2.exe') link -I $platform --manifest (Join-Path $PSScriptRoot 'AndroidManifest.xml') --min-sdk-version 26 --target-sdk-version 35 -R (Join-Path $out 'resources.zip') -o $unsigned
Check-Exit 'APK package'
$zip = [IO.Compression.ZipFile]::Open($unsigned,[IO.Compression.ZipArchiveMode]::Update)
try {[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $dex 'classes.dex'),'classes.dex') | Out-Null} finally {$zip.Dispose()}
$aligned = Join-Path $out 'aligned.apk'
& (Join-Path $sdk 'zipalign.exe') -p -f 4 $unsigned $aligned
Check-Exit 'Zip alignment'
$signing = Join-Path $Tools 'signing'
New-Item -ItemType Directory -Path $signing -Force | Out-Null
$keystore = Join-Path $signing 'note-recorder.p12'
$password = Join-Path $signing 'password.txt'
if (!(Test-Path -LiteralPath $keystore)) {
  [IO.File]::WriteAllText($password,([guid]::NewGuid().ToString('N')))
  & (Join-Path $JavaBin 'keytool.exe') -genkeypair -keystore $keystore -storetype PKCS12 -alias recorder -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Personal Note Version Recorder' -storepass:file $password
  Check-Exit 'Signing key creation'
}
[xml]$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'AndroidManifest.xml') -Raw
$version = $manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
$apk = Join-Path $out ("note-version-recorder-$version.apk")
& $java -jar (Join-Path $sdk 'lib/apksigner.jar') sign --ks $keystore --ks-key-alias recorder --ks-pass "file:$password" --out $apk $aligned
Check-Exit 'APK signing'
& $java -jar (Join-Path $sdk 'lib/apksigner.jar') verify --verbose --print-certs $apk
Check-Exit 'Signature verification'
& (Join-Path $sdk 'aapt.exe') dump badging $apk
Check-Exit 'Manifest verification'
Write-Output "APK: $apk"
