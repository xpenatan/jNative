param(
    [ValidateSet('BuildCandidate', 'Measure')][string]$Mode = 'Measure',
    [ValidateRange(3, 100)][int]$Pairs = 9
)
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$benchmark = Join-Path $repository 'tests/conformance/build/full-library-benchmark'
$source = Join-Path $repository 'tests/conformance/src/test/resources/fixtures/FullLibraryBenchmark.java'
$savedSource = Join-Path $benchmark 'FullLibraryBenchmark.java'
if (!(Test-Path -LiteralPath $savedSource)) { throw 'Build the retained baseline fixture first; see the migration plan.' }
if ((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $savedSource).Hash) {
    throw 'Fixture differs from the retained baseline. Rebuild both variants with identical compiled classes.'
}
$baseline = Join-Path $benchmark 'baseline/native/release/app.exe'
$candidate = Join-Path $benchmark 'candidate/native/release/app.exe'
if ($Mode -eq 'BuildCandidate') {
    $catalog = Get-Content -Raw -LiteralPath (Join-Path $repository 'gradle/libs.versions.toml')
    $versionMatch = [regex]::Match($catalog, '(?m)^jNativeSnapshot\s*=\s*"([^"]+)"')
    if (!$versionMatch.Success) { throw 'Cannot resolve the current Gradle artifact version.' }
    $version = $versionMatch.Groups[1].Value
    $toolsDirectory = Join-Path $benchmark 'candidate-tools'
    New-Item -ItemType Directory -Force -Path $toolsDirectory | Out-Null
    $retainedTools = Join-Path $repository 'tests/conformance/build/native-library-benchmark/baseline-tools'
    Get-ChildItem -LiteralPath $retainedTools -Filter 'asm*.jar' |
        Where-Object { $_.Name -notlike '*-sources.jar' } |
        Copy-Item -Destination $toolsDirectory
    foreach ($module in @('api', 'backend/cpp', 'classlib', 'compiler', 'core', 'interop', 'runtime', 'toolchain/cmake', 'cli')) {
        $jars = @(Get-ChildItem -LiteralPath (Join-Path $repository "jNative/$module/build/libs") -Filter "*-$version.jar" |
            Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' })
        if ($jars.Count -ne 1) { throw "Expected one built jar for $module. Run the Gradle conformance testClasses task first." }
        Copy-Item -LiteralPath $jars[0].FullName -Destination $toolsDirectory
    }
    $clock = [Diagnostics.Stopwatch]::StartNew()
    & java -cp "$toolsDirectory/*" com.github.xpenatan.jnative.cli.Main build `
        --classpath (Join-Path $benchmark 'classes') --main FullLibraryBenchmark `
        --output (Join-Path $benchmark 'candidate') --release --timeout-seconds 600 `
        --cmake-build-arg=--parallel --cmake-build-arg=2
    if ($LASTEXITCODE -ne 0) { throw 'Candidate build failed.' }
    $clock.Stop()
    Get-ChildItem -LiteralPath $toolsDirectory -Filter '*.jar' | Get-FileHash |
        Select-Object Path,Hash | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $benchmark 'candidate-tools.csv')
    [pscustomobject]@{BuildSeconds=$clock.Elapsed.TotalSeconds; ExecutableBytes=(Get-Item -LiteralPath $candidate).Length} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $benchmark 'candidate-build.json')
    return
}
if (!(Test-Path -LiteralPath $baseline) -or !(Test-Path -LiteralPath $candidate)) { throw 'Both executables are required.' }
function Median($values) {
    $sorted = @($values | Sort-Object)
    if ($sorted.Count % 2) { return $sorted[[int][Math]::Floor($sorted.Count / 2)] }
    return ($sorted[$sorted.Count / 2 - 1] + $sorted[$sorted.Count / 2]) / 2.0
}
$records = [Collections.Generic.List[object]]::new()
$previousInterval = $env:JNATIVE_GC_INTERVAL
Remove-Item Env:JNATIVE_GC_INTERVAL -ErrorAction SilentlyContinue
Push-Location $benchmark
try {
    foreach ($pair in 1..$Pairs) {
        $order = if ($pair % 2) { @('baseline','candidate') } else { @('candidate','baseline') }
        foreach ($variant in $order) {
            $executable = if ($variant -eq 'baseline') { $baseline } else { $candidate }
            $output = @(& $executable 2>&1)
            if ($LASTEXITCODE -ne 0) { throw "$variant exited $LASTEXITCODE : $output" }
            $output | Set-Content -LiteralPath "$variant-pair-$pair.log"
            $rows = @($output | Where-Object { $_ -like 'BENCH,*' })
            if ($rows.Count -ne 345) { throw "Unexpected row count $($rows.Count)" }
            foreach ($line in $rows) {
                $fields = $line.Split(',')
                $records.Add([pscustomobject]@{Pair=$pair;Variant=$variant;Operation=$fields[1];Size=[int]$fields[2];Iterations=[int]$fields[3];Nanoseconds=[long]$fields[4];Checksum=$fields[5]})
            }
        }
        Write-Output "Measured pair $pair/$Pairs"
    }
} finally {
    Pop-Location
    if ($null -eq $previousInterval) { Remove-Item Env:JNATIVE_GC_INTERVAL -ErrorAction SilentlyContinue }
    else { $env:JNATIVE_GC_INTERVAL = $previousInterval }
}
$records | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $benchmark 'samples.csv')
$paired = foreach ($group in ($records | Group-Object Operation,Size,Pair)) {
    $before = @($group.Group | Where-Object Variant -eq baseline)
    $after = @($group.Group | Where-Object Variant -eq candidate)
    $checksums = @($group.Group.Checksum | Select-Object -Unique)
    if ($checksums.Count -ne 1) { throw "Checksum mismatch for $($group.Name): $checksums" }
    $baseNs = Median $before.Nanoseconds
    $newNs = Median $after.Nanoseconds
    [pscustomobject]@{Pair=$before[0].Pair;Operation=$before[0].Operation;Size=$before[0].Size;BaselineNs=$baseNs;CandidateNs=$newNs;Speedup=$baseNs/[double][Math]::Max(1,$newNs)}
}
$paired | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $benchmark 'paired.csv')
$summary = foreach ($group in ($paired | Group-Object Operation,Size)) {
    [pscustomobject]@{Operation=$group.Group[0].Operation;Size=$group.Group[0].Size;BaselineNs=Median $group.Group.BaselineNs;CandidateNs=Median $group.Group.CandidateNs;MedianSpeedup=[Math]::Round((Median $group.Group.Speedup),3);MinimumSpeedup=[Math]::Round(($group.Group.Speedup | Measure-Object -Minimum).Minimum,3);MaximumSpeedup=[Math]::Round(($group.Group.Speedup | Measure-Object -Maximum).Maximum,3)}
}
$summary | Sort-Object Operation,Size | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $benchmark 'summary.csv')
$summary | Sort-Object Operation,Size | Format-Table -AutoSize
[pscustomobject]@{Utc=[DateTime]::UtcNow.ToString('o');Pairs=$Pairs;FixtureSha256=(Get-FileHash -LiteralPath $savedSource).Hash;BaselineExeSha256=(Get-FileHash -LiteralPath $baseline).Hash;CandidateExeSha256=(Get-FileHash -LiteralPath $candidate).Hash;BaselineBytes=(Get-Item -LiteralPath $baseline).Length;CandidateBytes=(Get-Item -LiteralPath $candidate).Length;OS=[Environment]::OSVersion.VersionString;Configuration='Release; same compiled fixture; default GC; three warmed samples per operation in each process'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $benchmark 'measurement.json')
