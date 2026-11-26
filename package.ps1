param([string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/release'))
$ErrorActionPreference='Stop'
[xml]$manifest=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'AndroidManifest.xml') -Raw
$version=$manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
New-Item -ItemType Directory -Path $OutputDirectory -Force|Out-Null
$apk=Join-Path $PSScriptRoot ("build/note-version-recorder-$version.apk")
Copy-Item -LiteralPath $apk -Destination (Join-Path $OutputDirectory ("note-version-recorder-$version.apk"))
$archive=Join-Path $OutputDirectory ("note-version-recorder-ai-ready-full-folder-$version.zip")
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream=[IO.File]::Open($archive,[IO.FileMode]::Create)
$zip=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create)
try {
  $files=@(git -C $PSScriptRoot ls-files --cached --others --exclude-standard)|Sort-Object -Unique
  foreach($relative in $files){
    if($relative -match '(^|/)(build|\.git|\.env[^/]*|password\.txt)(/|$)' -or $relative -match '\.(p12|keystore|apk|zip)$'){continue}
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $PSScriptRoot $relative),('note-version-recorder/'+$relative.Replace('\','/')))|Out-Null
  }
  [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$apk,("note-version-recorder/note-version-recorder-$version.apk"))|Out-Null
} finally {$zip.Dispose();$stream.Dispose()}
Write-Output $archive
