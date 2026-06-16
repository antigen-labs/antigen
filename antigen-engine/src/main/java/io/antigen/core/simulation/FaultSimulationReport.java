package io.antigen.core.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.antigen.core.report.HtmlReportGenerator;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FaultSimulationReport {

    private static final FaultSimulationReport INSTANCE = new FaultSimulationReport();
    public static final String REPORT_PATH_PROPERTY = "antigen.report.path";
    public static final String OUTPUT_DIR = "build/antigen";
    private static final String DEFAULT_REPORT_NAME = OUTPUT_DIR + "/fault_simulation_report.json";

    private final Map<String, EndpointFaultResults> report = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Set<String> caughtFaults = ConcurrentHashMap.newKeySet();
    private final Set<String> flakyTests = ConcurrentHashMap.newKeySet();

    private FaultSimulationReport() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static FaultSimulationReport getInstance() { return INSTANCE; }

    public Map<String, EndpointFaultResults> getReport() { return report; }

    public void recordInvariantResult(String endpoint, String invariantName, TestLevelSimulationResults result) {
        if (endpoint == null || invariantName == null || result == null) {
            System.err.println("[Antigen-WARN] Null data in recordInvariantResult. Skipping.");
            return;
        }
        report.computeIfAbsent(endpoint, k -> new EndpointFaultResults())
                .recordInvariantFault(invariantName, result);
    }

    // ── stop_on_first_catch tracking ──────────────────────────────────────────

    public boolean isInvariantFaultCaught(String endpoint, String invariantName) {
        return caughtFaults.contains(endpoint + "|invariant|" + invariantName);
    }

    public void markInvariantFaultCaught(String endpoint, String invariantName) {
        caughtFaults.add(endpoint + "|invariant|" + invariantName);
    }

    public void clearCaughtFaultsTracking() { caughtFaults.clear(); }

    // ── flaky-test tracking (failed control run) ──────────────────────────────

    /** Flags a test as flaky/state-dependent — its control run failed, so its verdicts are excluded. */
    public void markFlaky(String testName) { if (testName != null) flakyTests.add(testName); }

    public Set<String> getFlakyTests() { return flakyTests; }

    // ── Console summary ───────────────────────────────────────────────────────

    public void printConsoleSummary() {
        Map<String, int[]> perTestStats = new LinkedHashMap<>();
        Map<String, List<String>> perTestEscaped = new LinkedHashMap<>();
        int globalTotal = 0, globalCaught = 0;

        for (Map.Entry<String, EndpointFaultResults> epEntry : report.entrySet()) {
            String endpoint = epEntry.getKey();
            for (Map.Entry<String, FaultSimulationResult> invEntry
                    : epEntry.getValue().getInvariantFaults().entrySet()) {
                globalTotal++;
                if (invEntry.getValue().isCaughtByAnyTest()) globalCaught++;
                String desc = endpoint + " [invariant:" + invEntry.getKey() + "]";
                accumulatePerTest(invEntry.getValue(), desc, perTestStats, perTestEscaped);
            }
        }

        if (globalTotal == 0 && flakyTests.isEmpty()) return;

        String sep = "=".repeat(70);
        String div = "-".repeat(70);

        System.out.println();
        System.out.println(sep);
        System.out.println(" Antigen — Simulation Run Summary");
        System.out.println(sep);
        if (globalTotal > 0) {
            double rate = globalCaught * 100.0 / globalTotal;
            System.out.printf(" Overall: %d total  |  %d detected (%.0f%%)  |  %d escaped%n",
                    globalTotal, globalCaught, rate, globalTotal - globalCaught);
        }
        if (!flakyTests.isEmpty()) {
            System.out.printf(" Flaky/excluded (failed control run): %d%n", flakyTests.size());
            for (String t : flakyTests) System.out.println("   [!] " + t);
        }

        if (!perTestStats.isEmpty()) {
            List<Map.Entry<String, int[]>> sorted = new ArrayList<>(perTestStats.entrySet());
            sorted.sort((a, b) -> Integer.compare(
                    b.getValue()[1] - b.getValue()[0],
                    a.getValue()[1] - a.getValue()[0]));

            System.out.println(div);
            System.out.printf(" %-36s  %6s  %5s  %7s%n", "Test", "Caught", "Total", "Escaped");
            System.out.println(div);
            for (Map.Entry<String, int[]> entry : sorted) {
                int caught = entry.getValue()[0], total = entry.getValue()[1];
                System.out.printf(" %-36s  %6d  %5d  %7d%n",
                        entry.getKey(), caught, total, total - caught);
                if (total - caught > 0) {
                    List<String> ef = perTestEscaped.get(entry.getKey());
                    if (ef != null) ef.forEach(f -> System.out.println("   [X] " + f));
                }
            }
        }

        System.out.println(sep);
        System.out.println();
    }

    private void accumulatePerTest(FaultSimulationResult result, String faultDesc,
            Map<String, int[]> perTestStats, Map<String, List<String>> perTestEscaped) {
        Set<String> caughtTests = new HashSet<>();
        for (TestLevelSimulationResults ts : result.getCaughtBy()) caughtTests.add(ts.getTest());
        for (String testName : result.getTestedBy()) {
            perTestStats.computeIfAbsent(testName, k -> new int[]{0, 0});
            perTestEscaped.computeIfAbsent(testName, k -> new ArrayList<>());
            perTestStats.get(testName)[1]++;
            if (caughtTests.contains(testName)) {
                perTestStats.get(testName)[0]++;
            } else {
                perTestEscaped.get(testName).add(faultDesc);
            }
        }
    }

    // ── Report output ─────────────────────────────────────────────────────────

    public void createJSONReport() {
        try {
            String configuredPath = System.getProperty(REPORT_PATH_PROPERTY);
            File reportFile = configuredPath != null ? new File(configuredPath) : new File(DEFAULT_REPORT_NAME);
            if (reportFile.getParentFile() != null) reportFile.getParentFile().mkdirs();
            objectMapper.writeValue(reportFile, report);
            System.out.println("[Antigen] Saving fault simulation report to: " + reportFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[Antigen] Failed to save report: " + e.getMessage());
        }
    }
}
