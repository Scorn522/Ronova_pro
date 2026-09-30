param([string]$JdkHome=$env:JAVA_HOME,[string]$Zig='D:/桌面/MOD/.build-tools/zig-windows-x86_64-0.13.0/zig.exe')
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$includeRoot=Join-Path $JdkHome 'include'
if(-not(Test-Path -LiteralPath (Join-Path $includeRoot 'jni.h'))) { throw 'JNI headers required' }
if(-not $env:ZIG_GLOBAL_CACHE_DIR) { $env:ZIG_GLOBAL_CACHE_DIR=Join-Path $projectRoot 'build/zig-global' }
if(-not $env:ZIG_LOCAL_CACHE_DIR) { $env:ZIG_LOCAL_CACHE_DIR=Join-Path $projectRoot 'build/zig-local' }
$outputDirectory=Join-Path $projectRoot 'build/control-native/ronova-native/windows-x86_64'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$outputDll=Join-Path $outputDirectory 'ronova-pro-control.dll'
& $Zig cc '-O2' '-shared' '-target' 'x86_64-windows-gnu' (Join-Path $PSScriptRoot 'control.c') '-I' $includeRoot '-I' (Join-Path $includeRoot 'win32') '-o' $outputDll
if($LASTEXITCODE -ne 0) { throw "Control Native compilation failed: $LASTEXITCODE" }
$digest=[Security.Cryptography.SHA256]::Create()
try { [BitConverter]::ToString($digest.ComputeHash([IO.File]::ReadAllBytes($outputDll))).Replace('-','') } finally { $digest.Dispose() }
