param(
    [Parameter(Mandatory=$true)][string]$CandidateDirectory,
    [Parameter(Mandatory=$true)][string]$OutputPath
)
$ErrorActionPreference='Stop'
$projectRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$candidate=(Resolve-Path -LiteralPath $CandidateDirectory).Path.TrimEnd([IO.Path]::DirectorySeparatorChar)
$archive=[IO.Path]::GetFullPath($OutputPath)
if(Test-Path -LiteralPath $archive){throw "保留已有包，换一个输出文件名：$archive"}
if(-not (Test-Path -LiteralPath (Join-Path $candidate 'ronova-pro-core-0.1.0-stage-b-dev.jar') -PathType Leaf)){
    throw '候选目录缺少本次构建的 Core JAR。'
}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$packageName=[IO.Path]::GetFileNameWithoutExtension($archive)
$sourceRoots=@('src','native','gradle','docs/design','docs/architecture',
    'validation/checks','validation/fixture','validation/resources','validation/agent-check','validation/agent-policy')
$sourceFiles=@('AGENTS.md','README.md','PROGRESS.md','.gitignore','build.gradle','settings.gradle',
    'gradle.properties','gradlew','gradlew.bat','.vscode/c_cpp_properties.json','docs/README.md','docs/A-批验收状态.md',
    'validation/a-final-20260928/REPORT.md','validation/b-final-20261007/REPORT.md','distribution/package-current.ps1')
$entries=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
foreach($relative in $sourceFiles){
    $path=Join-Path $projectRoot $relative
    if(Test-Path -LiteralPath $path -PathType Leaf){$entries[$relative.Replace('\','/')]=(Get-Item -LiteralPath $path).FullName}
}
foreach($relative in $sourceRoots){
    $directory=Join-Path $projectRoot $relative
    if(-not (Test-Path -LiteralPath $directory -PathType Container)){continue}
    foreach($file in (Get-ChildItem -LiteralPath $directory -Recurse -File)){
        $name=$file.FullName.Substring($projectRoot.Length+1).Replace('\','/')
        if($name -match '(^|/)(build|out|__pycache__|main|test|stateChecks)/' -and $relative -eq 'src'){continue}
        if($name -match '(^|/)(build|out|__pycache__)/'){continue}
        if($file.Extension -in @('.dll','.exe','.sys','.obj','.pdb','.lib','.class','.log')){continue}
        $entries[$name]=$file.FullName
    }
}
foreach($file in (Get-ChildItem -LiteralPath $candidate -Recurse -File)){
    $relative=$file.FullName.Substring($candidate.Length+1).Replace('\','/')
    $entries["distribution/current/$relative"]=$file.FullName
}
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($archive)) | Out-Null
$stream=[IO.File]::Open($archive,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
try {
    $zip=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create,$false,[Text.Encoding]::UTF8)
    try {
        foreach($entry in ($entries.GetEnumerator() | Sort-Object Key)){
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$entry.Value,
                "$packageName/$($entry.Key)",[IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally {$zip.Dispose()}
} finally {$stream.Dispose()}
Get-Item -LiteralPath $archive | Select-Object FullName,Length
