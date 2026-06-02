package io.antigen.core.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.antigen.core.config.CoverageConfig;
import io.antigen.core.coverage.Collector;
import io.antigen.core.coverage.CollectorData;
import io.antigen.core.coverage.EndpointMethodCoverage;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class GapAnalyzer {

    public static void generateGapReport() {
        CoverageConfig config = CoverageConfig.getInstance();

        // Check if gap analysis is enabled
        if (!config.isGapAnalysisEnabled()) {
            System.out.println("[Gap Analysis] Gap analysis is disabled");
            return;
        }

        String specPath = config.getGapAnalysisSpecPath();
        if (specPath == null || specPath.isEmpty()) {
            System.err.println("[Gap Analysis] OpenAPI spec path not configured");
            return;
        }

        // Load endpoints from OpenAPI spec
        Set<OpenAPISpecLoader.EndpointInfo> specEndpoints = OpenAPISpecLoader.loadEndpoints(specPath);
        if (specEndpoints.isEmpty()) {
            System.err.println("[Gap Analysis] No endpoints loaded from OpenAPI spec");
            return;
        }

        // Get coverage data
        CollectorData coverageData = Collector.getData();
        Map<String, Map<String, EndpointMethodCoverage>> paths = coverageData.getPaths();

        // Create gap report
        GapReport report = new GapReport();
        report.getMetadata().setOpenapiSpecPath(specPath);

        Set<String> testedEndpoints = new HashSet<>();

        // Collect all tested endpoints from coverage data
        if (paths != null) {
            for (Map.Entry<String, Map<String, EndpointMethodCoverage>> pathEntry : paths.entrySet()) {
                String path = pathEntry.getKey();
                Map<String, EndpointMethodCoverage> methods = pathEntry.getValue();

                if (methods != null) {
                    for (Map.Entry<String, EndpointMethodCoverage> methodEntry : methods.entrySet()) {
                        String method = methodEntry.getKey();
                        EndpointMethodCoverage coverage = methodEntry.getValue();

                        String endpointKey = method + " " + path;
                        testedEndpoints.add(endpointKey);

                        // Add to tested endpoints list
                        Set<String> tests = coverage.getSummary() != null ? coverage.getSummary().getTests() : new HashSet<>();
                        int callCount = coverage.getSummary() != null ? coverage.getSummary().getNo_of_times_called() : 0;

                        report.getTestedEndpoints().add(new GapReport.EndpointDetail(path, method, tests, callCount));
                    }
                }
            }
        }

        // Find untested endpoints
        for (OpenAPISpecLoader.EndpointInfo specEndpoint : specEndpoints) {
            String endpointKey = specEndpoint.getMethod() + " " + specEndpoint.getPath();

            if (!testedEndpoints.contains(endpointKey)) {
                report.getUntestedEndpoints().add(
                    new GapReport.EndpointDetail(specEndpoint.getPath(), specEndpoint.getMethod())
                );
            }
        }

        // Sort endpoints for consistent output
        report.getTestedEndpoints().sort(Comparator.comparing(GapReport.EndpointDetail::getPath)
                .thenComparing(GapReport.EndpointDetail::getMethod));
        report.getUntestedEndpoints().sort(Comparator.comparing(GapReport.EndpointDetail::getPath)
                .thenComparing(GapReport.EndpointDetail::getMethod));

        // Calculate summary
        report.calculateSummary();

        // Save report
        saveReport(report, config.getGapAnalysisOutputFile());

        // Print summary
        System.out.println("[Gap Analysis] ========================================");
        System.out.println("[Gap Analysis] Gap Analysis Summary");
        System.out.println("[Gap Analysis] ========================================");
        System.out.println("[Gap Analysis] Total endpoints in spec: " + report.getSummary().getTotalEndpointsInSpec());
        System.out.println("[Gap Analysis] Tested endpoints: " + report.getSummary().getTestedEndpoints());
        System.out.println("[Gap Analysis] Untested endpoints: " + report.getSummary().getUntestedEndpoints());
        System.out.println("[Gap Analysis] Coverage: " + report.getSummary().getCoveragePercentage() + "%");
        System.out.println("[Gap Analysis] Report saved to: " + config.getGapAnalysisOutputFile());
        System.out.println("[Gap Analysis] ========================================");

        if (!report.getUntestedEndpoints().isEmpty()) {
            System.out.println("[Gap Analysis] Untested endpoints:");
            for (GapReport.EndpointDetail endpoint : report.getUntestedEndpoints()) {
                System.out.println("[Gap Analysis]   - " + endpoint.getMethod() + " " + endpoint.getPath());
            }
        }
    }

    private static void saveReport(GapReport report, String outputFile) {
        ObjectMapper objectMapper = new ObjectMapper();
        try (FileWriter writer = new FileWriter(outputFile)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, report);
            System.out.println("[Gap Analysis] Gap report saved to: " + outputFile);
        } catch (IOException e) {
            System.err.println("[Gap Analysis] Failed to save gap report: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
