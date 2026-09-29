param(
    [ValidateSet('Build', 'Measure')][string]$Mode = 'Measure',
    [ValidateRange(3, 100)][int]$Pairs = 9
)
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$benchmark = Join-Path $repository 'tests/conformance/build/library-stress-benchmark'
$fullBenchmark = Join-Path $repository 'tests/conformance/build/full-library-benchmark'
$source = Join-Path $repository 'tests/conformance/src/test/resources/fixtures/LibraryStressBenchmark.java'
$savedSource = Join-Path $benchmark 'LibraryStressBenchmark.java'
$classes = Join-Path $benchmark 'classes'
$baseline = Join-Path $benchmark 'baseline/native/release/app.exe'
$candidate = Join-Path $benchmark 'candidate/native/release/app.exe'

function Median($values) {
    $sorted = @($values | Sort-Object)
    if ($sorted.Count % 2) { return $sorted[[int][Math]::Floor($sorted.Count / 2)] }
    return ($sorted[$sorted.Count / 2 - 1] + $sorted[$sorted.Count / 2]) / 2.0
}
function ClassManifest {
    Get-ChildItem -LiteralPath $classes -Filter '*.class' | Sort-Object Name | ForEach-Object {
        [pscustomobject]@{Name=$_.Name;Hash=(Get-FileHash -LiteralPath $_.FullName).Hash}
    }
}
if ($Mode -eq 'Build') {
    New-Item -ItemType Directory -Force -Path $benchmark | Out-Null
    if (Test-Path -LiteralPath $savedSource) {
        if ((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $savedSource).Hash) {
            throw 'Saved fixture differs. Use a fresh retained benchmark directory for a new fixture.'
        }
    } else { Copy-Item -LiteralPath $source -Destination $savedSource }
    if (!(Test-Path -LiteralPath (Join-Path $classes 'LibraryStressBenchmark.class'))) {
        New-Item -ItemType Directory -Force -Path $classes | Out-Null
        & javac --release 17 -d $classes $savedSource
        if ($LASTEXITCODE -ne 0) { throw 'Fixture compilation failed.' }
        ClassManifest | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $benchmark 'fixture-classes.csv')
    }
    $savedClasses = @(Import-Csv -LiteralPath (Join-Path $benchmark 'fixture-classes.csv'))
    if (Compare-Object $savedClasses @(ClassManifest) -Property Name,Hash) { throw 'Compiled fixture classes have changed.' }
    $baselineManifest = Join-Path $fullBenchmark 'baseline-tools.csv'
    if (!(Test-Path -LiteralPath $baselineManifest)) { throw 'Full-library baseline tool manifest is required.' }
    $baselineTools = @(Import-Csv -LiteralPath $baselineManifest | Where-Object { $_.Path -notmatch '-(sources|javadoc)\.jar$' })
    $candidateTools = Join-Path $fullBenchmark 'candidate-tools'
    if (!(Test-Path -LiteralPath $candidateTools)) { throw 'Current full-library candidate-tools are required.' }
    foreach ($variant in @('baseline', 'candidate')) {
        $tools = Join-Path $benchmark "$variant-tools"
        New-Item -ItemType Directory -Force -Path $tools | Out-Null
        $expectedNames = if ($variant -eq 'baseline') {
            @($baselineTools | ForEach-Object { [IO.Path]::GetFileName($_.Path) })
        } else {
            @(Get-ChildItem -LiteralPath $candidateTools -Filter '*.jar' |
                Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } | Select-Object -ExpandProperty Name)
        }
        if (@(Get-ChildItem -LiteralPath $tools -Filter '*.jar' | Where-Object { $_.Name -notin $expectedNames }).Count) {
            throw "$variant-tools contains stale jars outside the selected toolchain; use a fresh retained directory."
        }
        if ($variant -eq 'baseline') {
            foreach ($jar in $baselineTools) {
                if (!(Test-Path -LiteralPath $jar.Path) -or (Get-FileHash -LiteralPath $jar.Path).Hash -ne $jar.Hash) {
                    throw "Retained baseline jar changed or is missing: $($jar.Path)"
                }
                Copy-Item -LiteralPath $jar.Path -Destination $tools
            }
        } else {
            Get-ChildItem -LiteralPath $candidateTools -Filter '*.jar' |
                Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } | Copy-Item -Destination $tools
        }
        Get-ChildItem -LiteralPath $tools -Filter '*.jar' | Sort-Object Name | Get-FileHash |
            Select-Object Path,Hash | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $benchmark "$variant-tools.csv")
        $clock = [Diagnostics.Stopwatch]::StartNew()
        & java -cp "$tools/*" com.github.xpenatan.jnative.cli.Main build `
            --classpath $classes --main LibraryStressBenchmark --output (Join-Path $benchmark $variant) `
            --release --timeout-seconds 600 --cmake-build-arg=--parallel --cmake-build-arg=2
        if ($LASTEXITCODE -ne 0) { throw "$variant native build failed." }
        $clock.Stop()
        $executable = if ($variant -eq 'baseline') { $baseline } else { $candidate }
        [pscustomobject]@{BuildSeconds=$clock.Elapsed.TotalSeconds;ExecutableBytes=(Get-Item -LiteralPath $executable).Length;
            FixtureSha256=(Get-FileHash -LiteralPath $savedSource).Hash;Utc=[DateTime]::UtcNow.ToString('o')} |
            ConvertTo-Json | Set-Content -LiteralPath (Join-Path $benchmark "$variant-build.json")
    }
    return
}
if (!(Test-Path -LiteralPath $baseline) -or !(Test-Path -LiteralPath $candidate)) { throw 'Run -Mode Build first.' }
if ((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $savedSource).Hash) {
    throw 'Fixture has changed since the retained builds.'
}
$retainedClasses = @(Import-Csv -LiteralPath (Join-Path $benchmark 'fixture-classes.csv'))
$currentClasses = @(ClassManifest)
if (Compare-Object $retainedClasses $currentClasses -Property Name,Hash) { throw 'Compiled fixture classes have changed.' }
$records = [Collections.Generic.List[object]]::new()
$previousInterval = $env:JNATIVE_GC_INTERVAL
Remove-Item Env:JNATIVE_GC_INTERVAL -ErrorAction SilentlyContinue
Push-Location $benchmark
try {
    foreach ($pair in 1..$Pairs) {
        $order = if ($pair % 2) { @('baseline', 'candidate') } else { @('candidate', 'baseline') }
        foreach ($variant in $order) {
            $executable = if ($variant -eq 'baseline') { $baseline } else { $candidate }
            $output = @(& $executable 2>&1)
            if ($LASTEXITCODE -ne 0) { throw "$variant exited $LASTEXITCODE : $output" }
            $output | Set-Content -LiteralPath "$variant-pair-$pair.log"
            $rows = @($output | Where-Object { $_ -like 'BENCH,*' })
            if ($rows.Count -ne 72) { throw "Unexpected benchmark row count: $($rows.Count)" }
            foreach ($line in $rows) {
                $fields = ([string]$line).Split(',')
                if ($fields.Count -ne 6) { throw "Malformed benchmark row: $line" }
                $records.Add([pscustomobject]@{Pair=$pair;Variant=$variant;Operation=$fields[1];Size=[int]$fields[2];
                    Iterations=[int]$fields[3];Nanoseconds=[long]$fields[4];Checksum=$fields[5]})
            }
        }
        Write-Output "Measured alternating pair $pair/$Pairs"
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
    if ($before.Count -ne 3 -or $after.Count -ne 3 -or $checksums.Count -ne 1 -or
            @($group.Group.Iterations | Select-Object -Unique).Count -ne 1) { throw "Sample/checksum mismatch: $($group.Name)" }
    $baseNs = Median $before.Nanoseconds
    $newNs = Median $after.Nanoseconds
    [pscustomobject]@{Pair=$before[0].Pair;Operation=$before[0].Operation;Size=$before[0].Size;
        BaselineNs=$baseNs;CandidateNs=$newNs;Speedup=$baseNs/[double][Math]::Max(1,$newNs)}
}
$paired | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $benchmark 'paired.csv')
$summary = foreach ($group in ($paired | Group-Object Operation,Size)) {
    $allChecksums = @($records | Where-Object { $_.Operation -eq $group.Group[0].Operation -and $_.Size -eq $group.Group[0].Size } |
        Select-Object -ExpandProperty Checksum -Unique)
    if ($allChecksums.Count -ne 1) { throw "Checksum changed between processes: $($group.Name)" }
    [pscustomobject]@{Operation=$group.Group[0].Operation;Size=$group.Group[0].Size;
        BaselineNs=Median $group.Group.BaselineNs;CandidateNs=Median $group.Group.CandidateNs;
        MedianSpeedup=[Math]::Round((Median $group.Group.Speedup),3);
        MinimumSpeedup=[Math]::Round(($group.Group.Speedup | Measure-Object -Minimum).Minimum,3);
        MaximumSpeedup=[Math]::Round(($group.Group.Speedup | Measure-Object -Maximum).Maximum,3);
        BaselineMinimumNs=($group.Group.BaselineNs | Measure-Object -Minimum).Minimum;
        BaselineMaximumNs=($group.Group.BaselineNs | Measure-Object -Maximum).Maximum;
        CandidateMinimumNs=($group.Group.CandidateNs | Measure-Object -Minimum).Minimum;
        CandidateMaximumNs=($group.Group.CandidateNs | Measure-Object -Maximum).Maximum;Checksum=$allChecksums[0]}
}
$summary | Sort-Object Operation,Size | Export-Csv -NoTypeInformation -LiteralPath (Join-Path $benchmark 'summary.csv')
$summary | Sort-Object Operation,Size | Format-Table -AutoSize
[pscustomobject]@{Utc=[DateTime]::UtcNow.ToString('o');Pairs=$Pairs;
    FixtureSha256=(Get-FileHash -LiteralPath $savedSource).Hash;
    FixtureClassSha256=(Get-FileHash -LiteralPath (Join-Path $classes 'LibraryStressBenchmark.class')).Hash;
    BaselineExeSha256=(Get-FileHash -LiteralPath $baseline).Hash;CandidateExeSha256=(Get-FileHash -LiteralPath $candidate).Hash;
    BaselineToolsManifestSha256=(Get-FileHash -LiteralPath (Join-Path $benchmark 'baseline-tools.csv')).Hash;
    CandidateToolsManifestSha256=(Get-FileHash -LiteralPath (Join-Path $benchmark 'candidate-tools.csv')).Hash;
    BaselineBytes=(Get-Item -LiteralPath $baseline).Length;CandidateBytes=(Get-Item -LiteralPath $candidate).Length;
    OS=[Environment]::OSVersion.VersionString;LogicalProcessors=[Environment]::ProcessorCount;
    Configuration='Release; same compiled fixture; default GC; one warmup and three samples per operation per process'} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $benchmark 'measurement.json')
