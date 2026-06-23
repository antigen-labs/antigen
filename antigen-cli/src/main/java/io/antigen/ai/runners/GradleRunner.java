package io.antigen.ai.runners;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.antigen.ai.feedback.ErrorParser;
import io.antigen.ai.model.CompilationError;
import io.antigen.ai.model.EscapedFault;
import io.antigen.ai.model.TestFailure;
import io.antigen.ai.orchestrator.AntigenConfig;
import io.antigen.ai.orchestrator.GenerationContext;
import io.antigen.ai.phases.BuildPhase;
import io.antigen.ai.phases.AntigenPhase;
import io.antigen.ai.phases.TestPhase;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class GradleRunner {

    private final ProcessExecutor processExecutor;
    private final ErrorParser errorParser;
    private final AntigenConfig config;

    public GradleRunner(AntigenConfig config) {
        this.processExecutor = new ProcessExecutor();
        this.errorParser = new ErrorParser();
        this.config = config;
    }

    public BuildPhase build(GenerationContext context) {
        log.info("Building project...");

        String gradleCommand = getGradleCommand(context.getProjectPath());

        ProcessExecutor.ProcessCommand command = ProcessExecutor.ProcessCommand.builder()
                .command(gradleCommand, "clean", "compileTestJava", "--console=plain", "--no-daemon")
                .workingDirectory(context.getProjectPath())
                .timeout(config.getBuildTimeout())
                .verbose(config.isVerbose())
                .build();

        ProcessExecutor.ProcessResult result = processExecutor.execute(command);

        if (result.isSuccess()) {
            log.info("Build successful");
            return BuildPhase.success();
        }

        log.warn("Build failed");
        List<CompilationError> errors = errorParser.parseCompilationErrors(result.getOutput());

        if (errors.isEmpty()) {
            errors = List.of(new CompilationError("unknown", 0,
                    errorParser.extractErrorSummary(result.getOutput())));
        }

        return BuildPhase.failed(errors);
    }

    public TestPhase runTests(GenerationContext context) {
        log.info("Running tests (without Antigen)...");

        String gradleCommand = getGradleCommand(context.getProjectPath());

        ProcessExecutor.ProcessCommand command = ProcessExecutor.ProcessCommand.builder()
                .command(
                        gradleCommand,
                        "test",
                        "--tests", toTestPattern(context),
                        "-DrunWithAntigen=false",
                        "--console=plain",
                        "--no-daemon"
                )
                .workingDirectory(context.getProjectPath())
                .timeout(config.getTestTimeout())
                .verbose(config.isVerbose())
                .build();

        ProcessExecutor.ProcessResult result = processExecutor.execute(command);

        if (result.isSuccess()) {
            log.info("All tests passed");
            return TestPhase.success();
        }

        log.warn("Tests failed");
        List<TestFailure> failures = errorParser.parseTestFailures(result.getOutput());

        if (failures.isEmpty()) {
            failures = List.of(new TestFailure("unknown", "unknown",
                    errorParser.extractErrorSummary(result.getOutput()), result.getOutput()));
        }

        return TestPhase.failed(failures);
    }

    public AntigenPhase runAntigen(GenerationContext context) {
        log.info("Running tests with Antigen fault injection...");

        String gradleCommand = getGradleCommand(context.getProjectPath());

        // Write the report to a stable path OUTSIDE the agent's project workspace so the AI cannot
        // read which faults were injected, but retain it (and log the path) so a developer can
        // inspect it. Cleared before the run so a stale report can't be mistaken for a fresh one.
        Path reportPath;
        try {
            Path reportDir = Path.of(System.getProperty("java.io.tmpdir"), "antigen");
            Files.createDirectories(reportDir);
            reportPath = reportDir.resolve("fault_simulation_report.json");
            Files.deleteIfExists(reportPath);
        } catch (IOException e) {
            log.error("Failed to prepare report file", e);
            return AntigenPhase.error("Could not prepare a file for the Antigen report: " + e.getMessage());
        }
        log.info("Antigen report (internal, hidden from the agent): {}", reportPath);

        ProcessExecutor.ProcessCommand command = ProcessExecutor.ProcessCommand.builder()
                .command(
                        gradleCommand,
                        "test",
                        "--tests", toTestPattern(context),
                        // Force re-execution: the plain test run (without Antigen) leaves the test
                        // task up-to-date, and the -D properties below are not declared task inputs,
                        // so without this Gradle skips the simulation run and no report is written.
                        "--rerun-tasks",
                        "-DrunWithAntigen=true",
                        "-Dio.antigen.core.config.source=local",
                        "-Dantigen.report.path=" + reportPath,
                        "-Dantigen.report.json_only=true",
                        "--console=plain",
                        "--no-daemon"
                )
                .workingDirectory(context.getProjectPath())
                .timeout(config.getAntigenTimeout())
                .verbose(config.isVerbose())
                .build();

        ProcessExecutor.ProcessResult result = processExecutor.execute(command);

        try {
            AntigenReport report = parseAntigenReport(reportPath);

            if (report.getEscapedFaults().isEmpty()) {
                log.info("Antigen passed - all faults caught");
                return AntigenPhase.success(
                    report.getFaultDetectionRate(),
                    report.getTotalFaults(),
                    report.getCaughtFaults()
                );
            }

            log.warn("Antigen failed - {} faults escaped", report.getEscapedFaults().size());
            return AntigenPhase.failed(
                report.getEscapedFaults(),
                report.getFaultDetectionRate(),
                report.getTotalFaults(),
                report.getCaughtFaults()
            );

        } catch (IOException e) {
            log.error("Antigen simulation produced no usable report at {}", reportPath, e);
            return AntigenPhase.error(
                    "Antigen simulation produced no usable report (" + reportPath + " was missing, "
                    + "empty, or unparseable). The fault-simulation test run did not write results -- "
                    + "this is an environment/setup problem, not an assertion gap. Check that the target "
                    + "project applies Antigen and forwards -DrunWithAntigen/-Dantigen.report.path to the "
                    + "test JVM, that the simulation tests actually executed, and that the backend API was "
                    + "reachable. Cause: " + e.getMessage());
        }
    }

    /**
     * Final proof-of-result pass, run once after the loop converges. Re-runs the simulation WITHOUT
     * {@code json_only} so the engine writes the full human-readable artifact set (HTML, JSON,
     * coverage, gap) into the project's {@code build/antigen}. Safe to expose here because the loop
     * has terminated -- the agent is no longer generating, so this cannot leak faults back into it.
     * Returns the HTML report path, or {@code null} if the pass could not be run.
     */
    public Path generateFullReport(GenerationContext context) {
        log.info("Generating final Antigen report (HTML + JSON) as proof of result...");
        String gradleCommand = getGradleCommand(context.getProjectPath());

        ProcessExecutor.ProcessCommand command = ProcessExecutor.ProcessCommand.builder()
                .command(
                        gradleCommand,
                        "test",
                        "--tests", toTestPattern(context),
                        "--rerun-tasks",
                        "-DrunWithAntigen=true",
                        "-Dio.antigen.core.config.source=local",
                        "--console=plain",
                        "--no-daemon"
                )
                .workingDirectory(context.getProjectPath())
                .timeout(config.getAntigenTimeout())
                .verbose(config.isVerbose())
                .build();

        try {
            processExecutor.execute(command);
            Path html = context.getProjectPath()
                    .resolve(io.antigen.core.simulation.FaultSimulationReport.OUTPUT_DIR)
                    .resolve("antigen_report.html");
            return Files.exists(html) ? html : null;
        } catch (Exception e) {
            log.warn("Failed to generate final Antigen report: {}", e.getMessage());
            return null;
        }
    }

    private AntigenReport parseAntigenReport(Path reportPath) throws IOException {
        if (!Files.exists(reportPath)) {
            throw new IOException("Antigen report not found at: " + reportPath);
        }

        ObjectMapper mapper = new ObjectMapper();
        // New structure: { "/endpoint": { counters..., "contract_faults": {faultType: {field: result}}, "invariant_faults": {name: result} } }
        java.util.Map<String, EndpointReport> rawReport =
                mapper.readValue(reportPath.toFile(), new com.fasterxml.jackson.core.type.TypeReference<>() {});

        java.util.List<EscapedFault> escapedFaults = new java.util.ArrayList<>();
        int totalFaults = 0;
        int caughtFaults = 0;

        for (java.util.Map.Entry<String, EndpointReport> endpointEntry : rawReport.entrySet()) {
            String endpoint = endpointEntry.getKey();
            EndpointReport endpointReport = endpointEntry.getValue();

            for (java.util.Map.Entry<String, FaultFieldResult> invariantEntry
                    : endpointReport.getInvariantFaults().entrySet()) {
                String invariantName = invariantEntry.getKey();
                FaultFieldResult result = invariantEntry.getValue();
                totalFaults++;
                if (result.isCaughtByAnyTest()) {
                    caughtFaults++;
                } else {
                    escapedFaults.add(new EscapedFault(
                            endpoint, invariantName, "invariant",
                            String.join(", ", result.getTestedBy())));
                }
            }
        }

        double faultDetectionRate = totalFaults > 0 ? (double) caughtFaults / totalFaults : 0.0;

        AntigenReport report = new AntigenReport();
        report.setTotalFaults(totalFaults);
        report.setCaughtFaults(caughtFaults);
        report.setEscapedFaults(escapedFaults);
        report.setFaultDetectionRate(faultDetectionRate);

        return report;
    }

    private String toTestPattern(GenerationContext context) {
        Path relative = context.getProjectPath().relativize(context.getOutputDir());
        String pkg = relative.toString().replace('\\', '/');
        String javaRoot = "src/test/java/";
        if (pkg.startsWith(javaRoot)) {
            pkg = pkg.substring(javaRoot.length());
        }
        return pkg.replace('/', '.') + ".*";
    }

    private String getGradleCommand(Path projectPath) {
        Path gradlewUnix = projectPath.resolve("gradlew");
        Path gradlewWindows = projectPath.resolve("gradlew.bat");

        if (Files.exists(gradlewWindows)) {
            return gradlewWindows.toString();
        } else if (Files.exists(gradlewUnix)) {
            return gradlewUnix.toString();
        }

        return "gradle";
    }

    /**
     * DTO for one endpoint entry in the Antigen report.
     * Structure: { contractFaultCount, invariantFaultCount, contractFaultsCaught, invariantFaultsCaught,
     *              contract_faults: { faultType: { fieldName: FaultFieldResult } },
     *              invariant_faults: { invariantName: FaultFieldResult } }
     */
    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class EndpointReport {
        @com.fasterxml.jackson.annotation.JsonProperty("invariant_faults")
        private java.util.Map<String, FaultFieldResult> invariantFaults;

        public EndpointReport() {}

        public java.util.Map<String, FaultFieldResult> getInvariantFaults() {
            return invariantFaults != null ? invariantFaults : java.util.Map.of();
        }
    }

    /**
     * DTO for a single fault result (contract field or invariant).
     * Structure: { "caught_by_any_test": true/false, "tested_by": [...], "caught_by": [...] }
     */
    @Data
    public static class FaultFieldResult {
        @com.fasterxml.jackson.annotation.JsonProperty("caught_by_any_test")
        private boolean caughtByAnyTest;
        @com.fasterxml.jackson.annotation.JsonProperty("tested_by")
        private java.util.List<String> testedBy;
        @com.fasterxml.jackson.annotation.JsonProperty("caught_by")
        private java.util.List<FaultTestResult> caughtBy;

        public FaultFieldResult() {}

        public java.util.List<String> getTestedBy() {
            return testedBy != null ? testedBy : java.util.List.of();
        }
    }

    /**
     * DTO for an individual test result within a fault entry.
     */
    @Data
    public static class FaultTestResult {
        private String test;
        private boolean caught;
        private String error;

        public FaultTestResult() {}
    }

    /**
     * DTO for Antigen report summary
     */
    @Data
    public static class AntigenReport {
        private int totalFaults;
        private int caughtFaults;
        private List<EscapedFault> escapedFaults;
        private double faultDetectionRate;

        public AntigenReport() {}

        public List<EscapedFault> getEscapedFaults() {
            return escapedFaults != null ? escapedFaults : List.of();
        }
    }
}
