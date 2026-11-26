param([string]$Tools=(Join-Path $PSScriptRoot '../build-tools'),[string]$Java='C:/Program Files/Eclipse Adoptium/jre-21.0.10.7-hotspot/bin/java.exe')
$ErrorActionPreference='Stop'
$output=Join-Path $PSScriptRoot 'build/test-classes'
New-Item -ItemType Directory -Path $output -Force | Out-Null
& $Java -jar (Join-Path $Tools 'ecj.jar') -source 1.8 -target 1.8 -proc:none -encoding UTF-8 -d $output (Join-Path $PSScriptRoot 'src/io/github/neuronsarealsocool/noterecorder/RevisionTracker.java') (Join-Path $PSScriptRoot 'tests/RevisionTrackerTest.java')
if($LASTEXITCODE -ne 0){throw 'Recorder core test compile failed'}
& $Java -cp $output io.github.neuronsarealsocool.noterecorder.RevisionTrackerTest
if($LASTEXITCODE -ne 0){throw 'Recorder core tests failed'}
