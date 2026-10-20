$ErrorActionPreference='Stop'
$instance=Split-Path -Parent $PSScriptRoot
$arguments=Join-Path $PSScriptRoot 'ronova-jvm.args'
try {
    # PCL waits for this command but does not enforce its exit code. A failed
    # preparation must therefore leave no old @argfile for the game JVM.
    if(Test-Path -LiteralPath $arguments){Remove-Item -LiteralPath $arguments -Force}
    $config=Get-Content -Raw -LiteralPath (Join-Path $instance 'PCL/config.json') | ConvertFrom-Json
    if($null -eq $config.InstanceForcedJava -or $config.InstanceForcedJava.Version.Major -ne 17){
        throw 'Ronova requires this PCL instance to select Java 17.'
    }
    $java=Join-Path $config.InstanceForcedJava.Folder 'java.exe'
    $prelaunch=Join-Path $PSScriptRoot 'ronova-pro-prelaunch.jar'
    $core=Join-Path $instance 'mods/ronova-pro-core-1.5.0-dev.jar'
    Set-Location -LiteralPath $instance
    & $java -jar $prelaunch --java $java --core $core --prepare-args $arguments --gameDir $instance --launchTarget forgeclient
    if($LASTEXITCODE -ne 0){throw "Ronova preparation failed: $LASTEXITCODE"}
    if(-not(Test-Path -LiteralPath $arguments -PathType Leaf)){throw 'Ronova JVM arguments were not generated.'}
} catch {
    if(Test-Path -LiteralPath $arguments){Remove-Item -LiteralPath $arguments -Force}
    Write-Error $_ -ErrorAction Continue
    exit 1
}
exit 0
