param([ValidateSet('Build', 'Measure')][string]$Mode = 'Measure', [int]$Pairs = 5)
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$root = Join-Path $repository 'tests/conformance/build/sc'
$toolsDirectory = Join-Path $repository 'tests/conformance/build/sb/candidate-tools'
if (!(Test-Path -LiteralPath $toolsDirectory)) { throw 'Build the full-library substitution benchmark tool snapshot first.' }
function Run-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed ($LASTEXITCODE)" }
}
if ($Mode -eq 'Build') {
    New-Item -ItemType Directory -Force -Path "$root/classes", "$root/java/META-INF/jnative", "$root/cpp/META-INF/jnative", "$root/cpp-bounded/META-INF/jnative", "$root/empty/META-INF/jnative", "$root/harness" | Out-Null
    @'
import java.util.function.IntUnaryOperator;
public class SubstitutionCallBenchmark {
    public static class Calc { public static int value(int x) { return x * 31 + 17; } }
    static long run(int iterations, boolean callback) {
        IntUnaryOperator operation = Calc::value;
        long checksum = 0;
        for (int i = 0; i < iterations; i++) checksum += callback ? operation.applyAsInt(i) : Calc.value(i);
        return checksum;
    }
    public static void main(String[] args) {
        for (int size : new int[]{1, 1000000}) for (boolean callback : new boolean[]{false, true}) {
            int repetitions = size == 1 ? 100000 : 1;
            for (int warm = 0; warm < 3; warm++) run(size, callback);
            for (int sample = 0; sample < 5; sample++) {
                long start = System.nanoTime(), checksum = 0;
                for (int i = 0; i < repetitions; i++) checksum += run(size, callback);
                System.out.println("CALL," + size + "," + callback + "," + (System.nanoTime()-start) + "," + checksum);
            }
        }
    }
}
'@ | Set-Content -LiteralPath "$root/SubstitutionCallBenchmark.java"
    @'
import com.github.xpenatan.jnative.substitution.*;
public class JavaPatch {
    @SubstituteMethod(owner="SubstitutionCallBenchmark$Calc", name="value", descriptor="(I)I")
    public static int value(int x) { return x * 31 + 17; }
}
'@ | Set-Content -LiteralPath "$root/JavaPatch.java"
    @'
import com.github.xpenatan.jnative.substitution.*;
import com.github.xpenatan.jnative.interop.*;
@NativeInclude("call_benchmark.h")
public class CppPatch {
    @SubstituteMethod(owner="SubstitutionCallBenchmark$Calc", name="value", descriptor="(I)I")
    @NativeImport("benchmark_value") public static native int value(int x);
}
'@ | Set-Content -LiteralPath "$root/CppPatch.java"
    (Get-Content -Raw -LiteralPath "$root/CppPatch.java").Replace('class CppPatch', 'class BoundedPatch').Replace('@NativeImport("benchmark_value")', '@NativeImport(value="benchmark_value", managed=true, runtimeOnly=true, bounded=true)') | Set-Content -LiteralPath "$root/BoundedPatch.java"
    '#include <stdint.h>
extern "C" inline int32_t benchmark_value(int32_t x) { return static_cast<int32_t>(static_cast<uint32_t>(x) * 31u + 17u); }' | Set-Content -LiteralPath "$root/call_benchmark.h"
    Run-Checked javac @('--release', '17', '-d', "$root/classes", "$root/SubstitutionCallBenchmark.java")
    foreach ($variant in @('java', 'cpp', 'cpp-bounded')) {
        $donor = switch ($variant) { 'java' { 'JavaPatch' } 'cpp' { 'CppPatch' } 'cpp-bounded' { 'BoundedPatch' } }
        Run-Checked javac @('--release', '17', '-proc:none', '-cp', "$toolsDirectory/*", '-d', "$root/$variant", "$root/$donor.java")
        @{schemaVersion=1;providerId="bench.$variant";declarations=@($donor)} | ConvertTo-Json | Set-Content -LiteralPath "$root/$variant/META-INF/jnative/substitutions.json"
    }
    @{schemaVersion=1;providerId='bench.empty';declarations=@()} | ConvertTo-Json | Set-Content -LiteralPath "$root/empty/META-INF/jnative/substitutions.json"
    foreach ($variant in @('baseline', 'java', 'cpp', 'cpp-bounded')) {
        $options = @('-cp', "$toolsDirectory/*", 'com.github.xpenatan.jnative.cli.Main', 'build', '--classpath', "$root/classes", '--main', 'SubstitutionCallBenchmark', '--output', "$root/$variant-out", '--release', '--timeout-seconds', '600', '--cmake-build-arg=--parallel', '--cmake-build-arg=2')
        if ($variant -ne 'baseline') { $options += @('--substitution-path', "$root/$variant") }
        if ($variant.StartsWith('cpp')) { $options += @('--native', "$root/call_benchmark.h") }
        Run-Checked java $options
    }
    @'
import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;
public class GenerationBenchmark {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        var allocation = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocation.setThreadAllocatedMemoryEnabled(true);
        for (String variant : new String[]{"disabled", "default-minimal", "default", "empty", "java", "cpp", "cpp-bounded"}) {
            for (int run = 0; run < 7; run++) {
                var builder = NativeBuilder.create().classpath(root.resolve("classes")).mainClass("SubstitutionCallBenchmark")
                    .buildRoot(root.resolve("generation")).nativeFile(root.resolve("call_benchmark.h"));
                if (variant.equals("disabled")) {
                    builder.mainClass("Minimal").useBuiltinSubstitutions(false);
                } else if (variant.equals("default-minimal")) {
                    builder.mainClass("Minimal");
                } else if (!variant.equals("default")) builder.substitutionPath(root.resolve(variant));
                long bytes = allocation.getCurrentThreadAllocatedBytes(), start = System.nanoTime();
                builder.generate();
                long elapsed = System.nanoTime() - start, allocated = allocation.getCurrentThreadAllocatedBytes() - bytes;
                if (run >= 2) System.out.println("GEN," + variant + "," + elapsed + "," + allocated);
            }
        }
    }
}
'@ | Set-Content -LiteralPath "$root/GenerationBenchmark.java"
    'public class Minimal { public static void main(String[] args) {} }' | Set-Content -LiteralPath "$root/Minimal.java"
    Run-Checked javac @('--release', '17', '-d', "$root/classes", "$root/Minimal.java")
    Run-Checked javac @('-cp', "$toolsDirectory/*", '-d', "$root/harness", "$root/GenerationBenchmark.java")
    return
}
function Median($values) {
    $sorted = @($values | Sort-Object)
    return $sorted[[int][Math]::Floor($sorted.Count / 2)]
}
$records = [Collections.Generic.List[object]]::new()
$previousInterval = $env:JNATIVE_GC_INTERVAL
Remove-Item Env:JNATIVE_GC_INTERVAL -ErrorAction SilentlyContinue
try {
    foreach ($pair in 1..$Pairs) {
        $order = if ($pair % 2) { @('baseline', 'java', 'cpp', 'cpp-bounded') } else { @('cpp-bounded', 'cpp', 'java', 'baseline') }
        foreach ($variant in $order) {
            $output = @(& "$root/$variant-out/native/release/app.exe")
            if ($LASTEXITCODE -ne 0) { throw "$variant failed" }
            $output | Set-Content -LiteralPath "$root/$variant-$pair.log"
            foreach ($line in $output) {
                $parts = $line.Split(',')
                if ($parts[0] -ne 'CALL') { continue }
                $records.Add([pscustomobject]@{Pair=$pair;Variant=$variant;Size=[int]$parts[1];Callback=$parts[2];Nanoseconds=[long]$parts[3];Checksum=$parts[4]})
            }
        }
    }
} finally {
    if ($null -ne $previousInterval) { $env:JNATIVE_GC_INTERVAL = $previousInterval }
}
$records | Export-Csv -NoTypeInformation -LiteralPath "$root/call-samples.csv"
foreach ($group in ($records | Group-Object Size,Callback)) {
    if (@($group.Group.Checksum | Select-Object -Unique).Count -ne 1) { throw "Checksum mismatch: $($group.Name)" }
}
$summary = foreach ($group in ($records | Group-Object Variant,Size,Callback)) {
    [pscustomobject]@{Variant=$group.Group[0].Variant;Size=$group.Group[0].Size;Callback=$group.Group[0].Callback;MedianNs=Median $group.Group.Nanoseconds}
}
$summary | Export-Csv -NoTypeInformation -LiteralPath "$root/call-summary.csv"
$summary | Format-Table -AutoSize
& java -cp "$toolsDirectory/*;$root/harness" GenerationBenchmark $root > "$root/generation-samples.log"
if ($LASTEXITCODE -ne 0) { throw 'Generation benchmark failed' }
Get-ChildItem -LiteralPath $toolsDirectory -Filter '*.jar' | Get-FileHash | Select-Object Path,Hash | Export-Csv -NoTypeInformation -LiteralPath "$root/tool-hashes.csv"
