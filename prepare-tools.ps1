param([string]$Tools=(Join-Path $PSScriptRoot '../build-tools'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Path $Tools -Force | Out-Null
$packages=@(
 @{Name='platform35.zip';Url='https://dl.google.com/android/repository/platform-35_r02.zip';Sha1='0bb560a90a7a2cbd0dd8348224d518b638fe7949';Folder='platform35'},
 @{Name='build-tools35.zip';Url='https://dl.google.com/android/repository/build-tools_r35_windows.zip';Sha1='af059bb67cf7786f45ee0db85e2d24985df1b4b6';Folder='sdk-build-tools35'}
)
foreach($package in $packages){$file=Join-Path $Tools $package.Name;if(!(Test-Path -LiteralPath $file)){Invoke-WebRequest -Uri $package.Url -OutFile $file};if((Get-FileHash -LiteralPath $file -Algorithm SHA1).Hash.ToLowerInvariant() -ne $package.Sha1){throw 'Android SDK checksum mismatch'};Expand-Archive -LiteralPath $file -DestinationPath (Join-Path $Tools $package.Folder) -Force}
$ecj=Join-Path $Tools 'ecj.jar'
if(!(Test-Path -LiteralPath $ecj)){Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.40.0/ecj-3.40.0.jar' -OutFile $ecj}
Write-Output 'Build tools are ready. Run build.ps1 using a Java 21 runtime with keytool.'
