param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$Zig = 'D:/桌面/MOD/.build-tools/zig-windows-x86_64-0.13.0/zig.exe'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$includeRoot = Join-Path $JdkHome 'include'
if (-not (Test-Path -LiteralPath (Join-Path $includeRoot 'jni.h'))) {
    throw 'The selected JDK must contain JNI headers.'
}
if (-not (Test-Path -LiteralPath $Zig)) { throw 'Zig compiler not found.' }
$outputDirectory = Join-Path $projectRoot 'build/native'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$outputDll = Join-Path $outputDirectory 'ronova-pro-storage.dll'
& $Zig c++ '-std=c++17' '-O2' '-shared' '-target' 'x86_64-windows-gnu' `
    (Join-Path $PSScriptRoot 'storage.cpp') '-I' $includeRoot '-I' (Join-Path $includeRoot 'win32') `
    '-lbcrypt' '-lkernel32' '-o' $outputDll
if ($LASTEXITCODE -ne 0) { throw "Native backend build failed: $LASTEXITCODE" }
Get-FileHash -LiteralPath $outputDll -Algorithm SHA256
