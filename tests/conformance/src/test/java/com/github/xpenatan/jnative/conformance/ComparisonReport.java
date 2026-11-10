package com.github.xpenatan.jnative.conformance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Presents every selected case from a completed all-suite run, including failures. */
public final class ComparisonReport {
    private static final List<String> COLUMNS = List.of("Suite", "Operation", "Size", "Rounds",
            "jNativeMs", "GraalVMMs", "CppMs", "CppStackMs", "CppSmallMs", "CppSmall32Ms",
            "TargetSampleMs", "DurationStatus", "SuiteStatus", "CaseId", "LookupCount",
            "jNativeNsPerLookup", "GraalVMNsPerLookup", "CppNsPerLookup");

    private ComparisonReport() {
    }

    public static void main(String[] args) throws IOException {
        if(args.length != 2 || !args[1].matches("[a-zA-Z0-9_-]+"))
            throw new IllegalArgumentException("Use reportComparison -PcomparisonRun=<completed-all-suite-run>");
        write(Path.of(args[0]).resolve(args[1]).toAbsolutePath().normalize());
    }

    static void write(Path root) throws IOException {
        if(!Files.isRegularFile(root.resolve("metadata.txt")))
            throw new IOException("The full run has not finished: " + root);
        var metadata = new Properties();
        try(var input = Files.newBufferedReader(root.resolve("metadata.txt"))) {
            metadata.load(input);
        }
        var rows = new ArrayList<Map<String, String>>();
        var statuses = readCsv(Files.readString(root.resolve("suite-status.csv")));
        var seenSuites = new HashSet<String>();
        for(var status : statuses) {
            String suite = required(status, "Suite");
            if(!seenSuites.add(suite)) throw new IOException("Duplicate suite: " + suite);
            ProjectComparison.selectedSuites(suite);
            String result = required(status, "Status");
            if(!List.of("PASS", "FAIL").contains(result)) throw new IOException("Unknown suite status: " + result);
            int index = seenSuites.size() - 1;
            Path suiteRoot = root.resolve(String.format(Locale.ROOT, "%02d", index));
            var selection = new Properties();
            Path configuration = suiteRoot.resolve("benchmark.properties");
            if(Files.isRegularFile(configuration)) {
                try(var input = Files.newBufferedReader(configuration)) {
                    selection.load(input);
                }
            } else {
                selection.setProperty("cases", suite + ":.*");
            }
            List<BenchmarkScores.Case> expected = BenchmarkScores.cases(selection);
            if(expected.isEmpty() || expected.stream().anyMatch(work -> !work.suite().equals(suite))
                    || expected.size() != Integer.parseInt(required(status, "Cases")))
                throw new IOException("Cannot reconstruct all selected cases for " + suite);
            var timings = new HashMap<String, Map<String, String>>();
            if(result.equals("PASS")) {
                Path source = root.resolve(required(status, "TimingsFile")).normalize();
                if(!source.startsWith(root)) throw new IOException("Timing path leaves run directory");
                for(var row : readCsv(Files.readString(source))) {
                    validateTiming(row);
                    String key = key(row);
                    if(timings.putIfAbsent(key, row) != null) throw new IOException("Duplicate timing case: " + key);
                }
                if(timings.size() != expected.size()) throw new IOException("Incomplete timings for " + suite);
            }
            for(var work : expected) {
                Map<String, String> timing = timings.remove(work.key());
                if(result.equals("PASS") && timing == null) throw new IOException("Missing timing: " + work.key());
                var row = new LinkedHashMap<String, String>();
                for(String column : COLUMNS) row.put(column, timing == null ? "" : timing.getOrDefault(column, ""));
                row.put("Suite", suite);
                row.put("Operation", work.operation());
                row.put("Size", Integer.toString(work.size()));
                row.put("CaseId", work.key());
                if(timing == null) row.put("DurationStatus", "not measured: suite failed");
                row.put("SuiteStatus", result);
                addLookupMetrics(row, work);
                rows.add(row);
            }
            if(!timings.isEmpty()) throw new IOException("Unexpected timing cases for " + suite);
        }
        if(rows.isEmpty()) throw new IOException("No selected cases");
        long passed = rows.stream().filter(row -> row.get("SuiteStatus").equals("PASS")).count();
        if(!Integer.toString(rows.size()).equals(metadata.getProperty("Cases"))
                || !Integer.toString(statuses.size()).equals(metadata.getProperty("Suites"))
                || !Long.toString(passed).equals(metadata.getProperty("CasesInCompletedSuites")))
            throw new IOException("Selected-case totals differ from completed run metadata");
        StringBuilder csv = new StringBuilder(String.join(",", COLUMNS)).append('\n');
        for(var row : rows) {
            for(int i = 0; i < COLUMNS.size(); i++) {
                if(i != 0) csv.append(',');
                csv.append('"').append(row.get(COLUMNS.get(i)).replace("\"", "\"\"")).append('"');
            }
            csv.append('\n');
        }
        Files.writeString(root.resolve("all-cases.csv"), csv);
        Files.writeString(root.resolve("all-cases.html"), html(root.getFileName().toString(), rows, statuses));
        System.out.println("All " + rows.size() + " cases: " + root.resolve("all-cases.html"));
        System.out.println("Combined raw timings: " + root.resolve("all-cases.csv"));
    }

    private static String required(Map<String, String> row, String name) throws IOException {
        String value = row.get(name);
        if(value == null || value.isBlank()) throw new IOException("Missing CSV value: " + name);
        return value;
    }

    private static String key(Map<String, String> row) throws IOException {
        return required(row, "Suite") + ":" + required(row, "Operation") + ":" + required(row, "Size");
    }

    private static void validateTiming(Map<String, String> row) throws IOException {
        try {
            if(Integer.parseInt(required(row, "Rounds")) < 1) throw new NumberFormatException("Nonpositive rounds");
            for(String column : COLUMNS) {
                if(!column.endsWith("Ms")) continue;
                String value = row.getOrDefault(column, "");
                if(value.isBlank()) continue;
                double number = Double.parseDouble(value);
                if(!Double.isFinite(number) || number <= 0) throw new NumberFormatException("Nonpositive or nonfinite timing");
            }
        } catch(NumberFormatException failure) {
            throw new IOException("Invalid timing data", failure);
        }
    }

    private static void addLookupMetrics(Map<String, String> row, BenchmarkScores.Case work) {
        String rounds = row.get("Rounds");
        if(rounds.isEmpty() || !work.suite().equals("map-probe") || !work.operation().startsWith("get-")) return;
        long lookups = (long) work.size() * Integer.parseInt(rounds);
        row.put("LookupCount", Long.toString(lookups));
        for(String variant : List.of("jNative", "GraalVM", "Cpp")) {
            String elapsed = row.get(variant + "Ms");
            if(!elapsed.isBlank()) {
                double nanosPerLookup = Double.parseDouble(elapsed) * 1_000_000 / lookups;
                row.put(variant + "NsPerLookup", String.format(Locale.ROOT, "%.4f", nanosPerLookup));
            }
        }
    }

    static List<Map<String, String>> readCsv(String source) throws IOException {
        var records = new ArrayList<List<String>>();
        var record = new ArrayList<String>();
        var cell = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        for(int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if(quoted) {
                if(value == '"' && index + 1 < source.length() && source.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else if(value == '"') {
                    quoted = false;
                    closed = true;
                } else cell.append(value);
            } else if(value == ',' || value == '\n' || value == '\r') {
                record.add(cell.toString());
                cell.setLength(0);
                closed = false;
                if(value != ',') {
                    records.add(List.copyOf(record));
                    record.clear();
                    if(value == '\r' && index + 1 < source.length() && source.charAt(index + 1) == '\n') index++;
                }
            } else if(value == '"') {
                if(cell.length() != 0 || closed) throw new IOException("Malformed CSV quote");
                quoted = true;
            } else {
                if(closed) throw new IOException("Text after closed CSV quote");
                cell.append(value);
            }
        }
        if(quoted) throw new IOException("Unclosed CSV quote");
        if(!record.isEmpty() || cell.length() != 0 || closed) {
            record.add(cell.toString());
            records.add(List.copyOf(record));
        }
        if(records.isEmpty()) throw new IOException("Empty CSV");
        List<String> header = records.removeFirst();
        if(header.stream().anyMatch(String::isBlank) || new HashSet<>(header).size() != header.size())
            throw new IOException("Invalid CSV header");
        var result = new ArrayList<Map<String, String>>();
        for(var values : records) {
            if(values.size() != header.size()) throw new IOException("CSV column count mismatch");
            var row = new LinkedHashMap<String, String>();
            for(int index = 0; index < header.size(); index++) row.put(header.get(index), values.get(index));
            result.add(row);
        }
        return result;
    }

    private static String html(String name, List<Map<String, String>> rows, List<Map<String, String>> statuses) {
        long passed = rows.stream().filter(row -> row.get("SuiteStatus").equals("PASS")).count();
        long qualified = rows.stream().filter(row -> row.get("SuiteStatus").equals("PASS")
                && row.get("DurationStatus").startsWith("target-met") && completeTimings(row)).count();
        long warnings = rows.stream().filter(ComparisonReport::warning).count();
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="en"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>jNative benchmark results</title>
                <style>
                :root{font:15px/1.5 system-ui,sans-serif;color:#172033;background:#f5f7fb}
                body{margin:24px}h1{margin-bottom:6px}p{max-width:1100px}a{color:#1756b8}
                .summary{font-size:18px;font-weight:600}.controls{display:flex;gap:12px;flex-wrap:wrap;margin:22px 0}
                input,select{font:inherit;padding:9px 12px;border:1px solid #bac6d8;border-radius:6px}
                input{min-width:290px}.table{overflow:auto;max-height:75vh;border:1px solid #d4dce9;background:white}
                table{border-collapse:collapse;width:100%;font-size:13px}th,td{padding:9px 12px;border-bottom:1px solid #e2e7ef;white-space:nowrap}
                th{position:sticky;top:0;background:#eaf0f9;text-align:left;z-index:1}td.num{font-variant-numeric:tabular-nums;text-align:right}
                tr:nth-child(even){background:#f9fbfe}tr[data-warning=true]{background:#fff4d5}tr[data-failed=true]{background:#ffe6e6}
                [hidden]{display:none!important}.muted{color:#53627a}label{display:flex;align-items:center;gap:8px}
                </style><h1>jNative full benchmark: 
                """);
        html.append(escape(name)).append("</h1><p class=summary>").append(statuses.size()).append(" suites · ")
                .append(rows.size()).append(" selected cases · ").append(passed).append(" verified · ")
                .append(qualified).append(" duration-qualified · ").append(warnings).append(" warnings</p>");
        html.append("""
                <p>Elapsed milliseconds are the median of process medians. Implementations in the same row share the round count.
                For map-probe lookups, the lookup count is size × rounds. Use nanoseconds per lookup to compare sizes.</p>
                <p class="muted">C++ reference uses the existing native containers. Stack, Small8 and Small32 are additional
                list-growth candidates; blank candidate cells mean not selected or inapplicable. Small8 and Small32 trade inline storage for heap allocation.
                Container growth, ownership and GC contracts differ. Blank primary timings indicate unavailable measurements.</p>
                <p><a href="all-cases.csv" download>Download all cases as CSV</a> · <a href="suite-status.csv">Suite status and elapsed times</a></p>
                <div class="controls"><label>Find <input id="search" type="search" placeholder="Suite, operation, size or status"></label>
                <label>Show <select id="status"><option value="all">All cases</option><option value="warning">Warnings</option>
                <option value="failed">Failed suites</option></select></label><span id="count" aria-live="polite"></span></div>
                <div class="table"><table><thead><tr><th>Case</th><th>Rounds</th><th>Lookups</th>
                <th>jNative ms</th><th>GraalVM ms</th><th>C++ reference ms</th><th>C++ Stack ms</th><th>C++ Small8 ms</th><th>C++ Small32 ms</th>
                <th>jNative ns/lookup</th><th>GraalVM ns/lookup</th><th>C++ ns/lookup</th>
                <th>Duration status</th><th>Suite verification</th></tr></thead><tbody>
                """);
        for(var row : rows) {
            html.append("<tr data-warning=\"").append(warning(row)).append("\" data-failed=\"")
                    .append(row.get("SuiteStatus").equals("FAIL")).append("\">");
            for(String column : List.of("CaseId", "Rounds", "LookupCount", "jNativeMs", "GraalVMMs",
                    "CppMs", "CppStackMs", "CppSmallMs", "CppSmall32Ms",
                    "jNativeNsPerLookup", "GraalVMNsPerLookup", "CppNsPerLookup", "DurationStatus", "SuiteStatus")) {
                String value = row.get(column);
                boolean numeric = column.endsWith("Ms") || column.endsWith("NsPerLookup")
                        || column.equals("LookupCount") || column.equals("Rounds");
                html.append(numeric ? "<td class=\"num\">" : "<td>")
                        .append(escape(value.isEmpty() ? "—" : value)).append("</td>");
            }
            html.append("</tr>\n");
        }
        return html.append("""
                </tbody></table></div><script>
                const search=document.getElementById('search'), status=document.getElementById('status');
                const rows=Array.from(document.querySelectorAll('tbody tr'));
                function filter(){const query=search.value.toLowerCase();let shown=0;
                for(const row of rows){const selected=status.value==='all'||(status.value==='warning'&&row.dataset.warning==='true')||
                (status.value==='failed'&&row.dataset.failed==='true');row.hidden=!(selected&&row.textContent.toLowerCase().includes(query));
                if(!row.hidden)shown++;}document.getElementById('count').textContent=shown+' / '+rows.length+' cases';}
                search.addEventListener('input',filter);status.addEventListener('change',filter);filter();
                </script></html>
                """).toString();
    }

    private static boolean warning(Map<String, String> row) {
        return !row.get("SuiteStatus").equals("PASS") || !row.get("DurationStatus").startsWith("target-met")
                || row.get("DurationStatus").contains("above-budget")
                || !completeTimings(row);
    }

    private static boolean completeTimings(Map<String, String> row) {
        return List.of("jNativeMs", "GraalVMMs", "CppMs").stream().noneMatch(column -> row.get(column).isBlank());
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
