[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^10\.0\.[0-9]+\.[0-9]+$')]
    [string] $KitVersion,
    [ValidateSet('Release', 'Debug')]
    [string] $Configuration = 'Release',
    [string] $MSBuildPath,
    [string] $KitsRoot,
    [string] $OutputRoot = (Join-Path $PSScriptRoot 'out'),
    [string] $ObAltitude,
    [string] $FilterAltitude,
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$')]
    [string] $DriverVersion = '1.0.0.0',
    [datetime] $DriverDate = [datetime]::Today,
    [switch] $GenerateCatalog,
    [string] $Inf2CatPath,
    [string] $CatalogOs
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# This entry builds source and, with actual assigned altitudes, stages a package.
# It never signs, generates certificates, installs, loads, elevates or restarts.
if ($PSVersionTable.PSVersion.Major -lt 5) { throw 'Windows PowerShell 5.1 or PowerShell 7 is required.' }
if (-not [Environment]::Is64BitProcess) { throw 'Run the x64 PowerShell host.' }
$stagePackage = -not [string]::IsNullOrWhiteSpace($ObAltitude) -or -not [string]::IsNullOrWhiteSpace($FilterAltitude)
if ($stagePackage) {
    foreach ($altitude in @($ObAltitude, $FilterAltitude)) {
        if ($altitude -notmatch '^[0-9]+(\.[0-9]+)?$' -or $altitude.Length -gt 254) {
            throw 'Provide both actual assigned numeric ObAltitude and FilterAltitude; no fallback altitude is chosen.'
        }
    }
}
if ($GenerateCatalog -and (-not $stagePackage -or [string]::IsNullOrWhiteSpace($CatalogOs))) {
    throw '-GenerateCatalog requires both configured altitudes and the explicit Inf2Cat -CatalogOs target list.'
}
foreach ($part in $DriverVersion.Split('.')) {
    if ([uint64]::Parse($part) -gt 65535) { throw 'Each DriverVersion component must be between 0 and 65535.' }
}
if ([string]::IsNullOrWhiteSpace($MSBuildPath)) {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    if (-not (Test-Path -LiteralPath $vswhere -PathType Leaf)) {
        throw 'Visual Studio 2022 C++/WDK MSBuild was not found. Supply -MSBuildPath after installing those tools.'
    }
    $candidates = @(& $vswhere -latest -version '[17.0,18.0)' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -find 'MSBuild\**\Bin\MSBuild.exe')
    if ($LASTEXITCODE -ne 0 -or $candidates.Count -eq 0) { throw 'No Visual Studio 2022 C++ MSBuild installation was found.' }
    $MSBuildPath = $candidates[0]
}
$MSBuildPath = (Resolve-Path -LiteralPath $MSBuildPath).ProviderPath
if ([string]::IsNullOrWhiteSpace($KitsRoot)) {
    $KitsRoot = (Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Microsoft\Windows Kits\Installed Roots' -Name KitsRoot10).KitsRoot10
}
$KitsRoot = [IO.Path]::GetFullPath($KitsRoot).TrimEnd('\') + '\'
$OutputRoot = [IO.Path]::GetFullPath($OutputRoot).TrimEnd('\') + '\'
if ([string]::Equals($OutputRoot, ([IO.Path]::GetFullPath($PSScriptRoot).TrimEnd('\') + '\'), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputRoot must be separate from the driver source directory.'
}
if (-not (Test-Path -LiteralPath (Join-Path $KitsRoot "Include\$KitVersion\km\fltKernel.h") -PathType Leaf)) {
    throw "The matching WDK kernel headers for $KitVersion were not found under KitsRoot. Install the SDK, WDK and VS WDK extension."
}
if ($GenerateCatalog -and [string]::IsNullOrWhiteSpace($Inf2CatPath)) {
    foreach ($candidate in @((Join-Path $KitsRoot "bin\$KitVersion\x86\Inf2Cat.exe"), (Join-Path $KitsRoot 'bin\x86\Inf2Cat.exe'))) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $Inf2CatPath = $candidate; break }
    }
    if ([string]::IsNullOrWhiteSpace($Inf2CatPath)) { throw 'Inf2Cat was not found. Supply -Inf2CatPath from the selected WDK.' }
}
if ($GenerateCatalog) { $Inf2CatPath = (Resolve-Path -LiteralPath $Inf2CatPath).ProviderPath }

# Escape values consumed by the MSBuild property parser; invocation uses argv,
# never a composed shell command or command substitution.
function ConvertTo-MSBuildValue([string] $Value) {
    $Value.Replace('%', '%25').Replace('$', '%24').Replace('@', '%40').Replace(';', '%3B').Replace(',', '%2C')
}
$arguments = @(
    (Join-Path $PSScriptRoot 'RonovaHost.proj'), '/t:Build', '/nologo', '/m:1',
    "/p:Configuration=$Configuration", '/p:Platform=x64',
    "/p:WindowsTargetPlatformVersion=$KitVersion", "/p:WindowsSdkDir=$(ConvertTo-MSBuildValue $KitsRoot)",
    "/p:RonovaHostOutputRoot=$(ConvertTo-MSBuildValue $OutputRoot)"
)
& $MSBuildPath @arguments
if ($LASTEXITCODE -ne 0) { throw "RonovaHost source build failed: MSBuild exit $LASTEXITCODE. No deployment action was taken." }
$configurationRoot = Join-Path $OutputRoot "x64\$Configuration"
Write-Output "Driver: $(Join-Path $configurationRoot 'kernel\RonovaHost.sys')"
Write-Output "Management tool: $(Join-Path $configurationRoot 'tools\RonovaHostManage.exe')"
if (-not $stagePackage) {
    Write-Output 'Source build only. RonovaHost.inf retains empty altitude values; no configured deployment package was produced.'
    return
}

$packageRoot = Join-Path $configurationRoot 'package'
New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null
$catalogFile = Join-Path $packageRoot 'RonovaHost.cat'
# A stale catalog must not attest a different SYS/INF from an earlier build.
if (Test-Path -LiteralPath $catalogFile -PathType Leaf) { Remove-Item -LiteralPath $catalogFile }
Copy-Item -LiteralPath (Join-Path $configurationRoot 'kernel\RonovaHost.sys') -Destination (Join-Path $packageRoot 'RonovaHost.sys') -Force
$template = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'RonovaHost.inf'))
$driverVer = $DriverDate.ToString('MM/dd/yyyy', [Globalization.CultureInfo]::InvariantCulture) + ',' + $DriverVersion
$configured = $template.Replace('ObAltitude=""', ('ObAltitude="' + $ObAltitude + '"')).Replace('FilterAltitude=""', ('FilterAltitude="' + $FilterAltitude + '"'))
$configured = [regex]::Replace($configured, '(?m)^DriverVer=.*$', ('DriverVer=' + $driverVer))
[IO.File]::WriteAllText((Join-Path $packageRoot 'RonovaHost.inf'), $configured, [Text.Encoding]::ASCII)
if ($GenerateCatalog) {
    & $Inf2CatPath "/driver:$packageRoot" "/os:$CatalogOs"
    if ($LASTEXITCODE -ne 0) { throw "Inf2Cat failed: exit $LASTEXITCODE. The package has not been signed or installed." }
}
Write-Output "Configured package: $packageRoot"
Write-Output 'The SYS and any generated CAT are unsigned outputs. Obtain Windows-accepted signatures externally before explicit installation.'
