param([ValidateRange(1, 100)][int]$Pairs = 7)
$ErrorActionPreference = 'Stop'
$benchmarkDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../build/native-library-benchmark'))
$baselineExe = Join-Path $benchmarkDirectory 'baseline/out/native/release/app.exe'
$candidateExe = Join-Path $benchmarkDirectory 'candidate/out/native/release/app.exe'
if (!(Test-Path -LiteralPath $baselineExe) -or !(Test-Path -LiteralPath $candidateExe)) {
    throw 'Build both retained NativeLibraryBenchmark variants before measuring.'
}
$fixturePath = Join-Path $benchmarkDirectory 'NativeLibraryBenchmark.java'
$fixtureHash = (Get-FileHash -LiteralPath $fixturePath).Hash
$currentFixture = Join-Path $PSScriptRoot '../src/test/resources/fixtures/NativeLibraryBenchmark.java'
if ((Get-FileHash -LiteralPath $currentFixture).Hash -ne $fixtureHash) {
    throw 'The benchmark fixture has changed since the retained baseline was built.'
}
$previousInterval = $env:JNATIVE_GC_INTERVAL
Remove-Item Env:JNATIVE_GC_INTERVAL -ErrorAction SilentlyContinue
$records = [Collections.Generic.List[object]]::new()
function Median($values) {
    $ordered = @($values | Sort-Object)
    return $ordered[[int][Math]::Floor($ordered.Count / 2)]
}
try {
    foreach ($pair in 1..$Pairs) {
        $labels = if ($pair % 2 -eq 1) { @('baseline', 'candidate') } else { @('candidate', 'baseline') }
        foreach ($label in $labels) {
            $executable = if ($label -eq 'baseline') { $baselineExe } else { $candidateExe }
            $output = @(& $executable 2>&1)
            if ($LASTEXITCODE -ne 0) { throw "$label benchmark failed: $output" }
            $output | Set-Content -LiteralPath (Join-Path $benchmarkDirectory "$label-pair-$pair.log")
            $rows = @($output | Where-Object { $_ -like 'BENCH,*' })
            if ($rows.Count -ne 54) { throw "Unexpected benchmark row count: $($rows.Count)" }
            foreach ($row in $rows) {
                $fields = $row.Split(',')
                $records.Add([pscustomobject]@{Pair=$pair; Variant=$label; Operation=$fields[1]; Size=[int]$fields[2]; Iterations=[long]$fields[3]; Nanoseconds=[long]$fields[4]; Checksum=$fields[5]})
            }
        }
        Write-Output "Measured alternating pair $pair/$Pairs"
    }
} finally {
    if ($null -ne $previousInterval) { $env:JNATIVE_GC_INTERVAL = $previousInterval }
    else { Remove-Item Env:JNATIVE_GC_INTERVAL -ErrorAction SilentlyContinue }
}
$records | Export-Csv -LiteralPath (Join-Path $benchmarkDirectory 'samples.csv') -NoTypeInformation
$paired = foreach ($group in ($records | Group-Object Operation,Size,Pair)) {
    $baselineRows = @($group.Group | Where-Object Variant -eq baseline)
    $candidateRows = @($group.Group | Where-Object Variant -eq candidate)
    if (($baselineRows.Checksum | Select-Object -Unique) -ne ($candidateRows.Checksum | Select-Object -Unique)) { throw "Checksum mismatch: $($group.Name)" }
    $baseline = Median $baselineRows.Nanoseconds
    $candidate = Median $candidateRows.Nanoseconds
    [pscustomobject]@{Pair=$baselineRows[0].Pair; Operation=$baselineRows[0].Operation; Size=$baselineRows[0].Size; BaselineNs=$baseline; CandidateNs=$candidate; Speedup=$baseline/[double]$candidate}
}
$paired | Export-Csv -LiteralPath (Join-Path $benchmarkDirectory 'paired.csv') -NoTypeInformation
$summary = foreach ($group in ($paired | Group-Object Operation,Size)) {
    [pscustomobject]@{Operation=$group.Group[0].Operation; Size=$group.Group[0].Size; MedianSpeedup=[Math]::Round((Median $group.Group.Speedup),3); MinimumSpeedup=[Math]::Round(($group.Group.Speedup | Measure-Object -Minimum).Minimum,3); MaximumSpeedup=[Math]::Round(($group.Group.Speedup | Measure-Object -Maximum).Maximum,3)}
}
$summary | Sort-Object Operation,Size | Export-Csv -LiteralPath (Join-Path $benchmarkDirectory 'summary.csv') -NoTypeInformation
$summary | Sort-Object Operation,Size | Format-Table -AutoSize
[pscustomobject]@{Utc=[DateTime]::UtcNow.ToString('o'); Pairs=$Pairs; FixtureSha256=$fixtureHash; FixtureClassSha256=(Get-FileHash -LiteralPath (Join-Path $benchmarkDirectory 'classes/NativeLibraryBenchmark.class')).Hash; BaselineExeSha256=(Get-FileHash -LiteralPath $baselineExe).Hash; CandidateExeSha256=(Get-FileHash -LiteralPath $candidateExe).Hash; OS=[Environment]::OSVersion.VersionString; LogicalProcessors=[Environment]::ProcessorCount; Configuration='Release; same compiled fixture; default GC settings'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $benchmarkDirectory 'measurement.json')
