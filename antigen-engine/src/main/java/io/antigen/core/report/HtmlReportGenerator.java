package io.antigen.core.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HtmlReportGenerator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void generateReport(String outputPath) {
        try {
            // Read JSON reports
            String dir = io.antigen.core.simulation.FaultSimulationReport.OUTPUT_DIR + "/";
            JsonNode faultSimulation = readJsonFile(dir + "fault_simulation_report.json");
            JsonNode gapAnalysis = readJsonFile(dir + "gap_analysis.json");
            JsonNode schemaCoverage = readJsonFile(dir + "schema_coverage.json");

            // Generate HTML
            String html = buildHtmlReport(faultSimulation, gapAnalysis, schemaCoverage);

            // Write to file
            try (FileWriter writer = new FileWriter(outputPath)) {
                writer.write(html);
            }

            System.out.println("[Antigen] HTML report generated: " + outputPath);

        } catch (Exception e) {
            System.err.println("[Antigen] Failed to generate HTML report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static JsonNode readJsonFile(String filename) throws IOException {
        File file = new File(filename);
        if (!file.exists()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        return OBJECT_MAPPER.readTree(file);
    }

    private static String buildHtmlReport(JsonNode faultSimulation, JsonNode gapAnalysis, JsonNode schemaCoverage) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>Antigen Report</title>\n");
        html.append("  <style>\n");
        html.append(getCssStyles());
        html.append("  </style>\n");
        html.append("</head>\n<body>\n");

        // Header
        html.append(buildHeader());

        // Summary Cards
        html.append(buildSummaryCards(faultSimulation, gapAnalysis, schemaCoverage));

        // Navigation Tabs
        html.append("  <div class=\"tabs\">\n");
        html.append("    <button class=\"tab-button active\" onclick=\"showTab('fault-simulation')\">Fault Simulation</button>\n");
        html.append("    <button class=\"tab-button\" onclick=\"showTab('gap-analysis')\">Execution Coverage</button>\n");
        html.append("  </div>\n");

        // Tab Content
        html.append("  <div id=\"fault-simulation\" class=\"tab-content active\">\n");
        html.append(buildFaultSimulationSection(faultSimulation));
        html.append("  </div>\n");

        html.append("  <div id=\"gap-analysis\" class=\"tab-content\">\n");
        html.append(buildGapAnalysisSection(gapAnalysis));
        html.append("  </div>\n");

        // JavaScript
        html.append("  <script>\n");
        html.append(getJavaScript());
        html.append("  </script>\n");

        html.append("</body>\n</html>");

        return html.toString();
    }

    private static String buildHeader() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return "  <div class=\"header\">\n" +
               "    <div class=\"header-content\">\n" +
               "      <div>\n" +
               "        <div class=\"brand\"><span class=\"brand-mark\"></span>Antigen<span class=\"brand-sub\">Report</span></div>\n" +
               "        <p class=\"timestamp\">Generated " + timestamp + "</p>\n" +
               "      </div>\n" +
               "      <button class=\"theme-toggle\" onclick=\"toggleTheme()\" title=\"Toggle theme\">\n" +
               "        <span class=\"theme-label\">Dark</span>\n" +
               "      </button>\n" +
               "    </div>\n" +
               "  </div>\n";
    }

    private static String buildSummaryCards(JsonNode faultSimulation, JsonNode gapAnalysis, JsonNode schemaCoverage) {
        int totalEndpoints = faultSimulation != null ? faultSimulation.size() : 0;
        int[] stats = calculateFaultStats(faultSimulation); // [total, detected, escaped]
        int total = stats[0], detected = stats[1], escaped = stats[2];
        double detectionRate = total > 0 ? (detected * 100.0 / total) : 0;

        int untestedEndpoints = 0;
        double coveragePercentage = 0;
        if (gapAnalysis != null && gapAnalysis.has("summary")) {
            JsonNode summary = gapAnalysis.get("summary");
            if (summary.has("untested_endpoints")) untestedEndpoints = summary.get("untested_endpoints").asInt();
            if (summary.has("coverage_percentage")) coveragePercentage = summary.get("coverage_percentage").asDouble();
        }

        StringBuilder cards = new StringBuilder();
        cards.append("  <div class=\"summary-cards\">\n");

        // Card 1: Detection Rate
        String rateClass = detectionRate >= 90 ? "good" : detectionRate >= 70 ? "warning" : "bad";
        cards.append("    <div class=\"card\">\n");
        cards.append("      <div class=\"card-title\">Detection Rate</div>\n");
        cards.append("      <div class=\"card-value " + rateClass + "\">" + String.format("%.1f%%", detectionRate) + "</div>\n");
        cards.append("      <div class=\"meter " + rateClass + "\"><i style=\"width:" + String.format(Locale.US, "%.1f", detectionRate) + "%\"></i></div>\n");
        cards.append("      <div class=\"card-subtitle\">" + detected + " of " + total + " invariant violations detected</div>\n");
        cards.append("    </div>\n");

        // Card 2: Escaped violations
        String escapedClass = escaped == 0 ? "good" : escaped <= 3 ? "warning" : "bad";
        double escapedPct = total > 0 ? (escaped * 100.0 / total) : 0;
        cards.append("    <div class=\"card\">\n");
        cards.append("      <div class=\"card-title\">Escaped Violations</div>\n");
        cards.append("      <div class=\"card-value " + escapedClass + "\">" + escaped + "</div>\n");
        cards.append("      <div class=\"meter " + escapedClass + "\"><i style=\"width:" + String.format(Locale.US, "%.1f", escapedPct) + "%\"></i></div>\n");
        cards.append("      <div class=\"card-subtitle\">Invariant violations not caught by any test</div>\n");
        cards.append("    </div>\n");

        // Card 3: Endpoint Coverage
        String covClass = coveragePercentage >= 80 ? "good" : coveragePercentage >= 50 ? "warning" : "bad";
        cards.append("    <div class=\"card\">\n");
        cards.append("      <div class=\"card-title\">Endpoint Coverage</div>\n");
        cards.append("      <div class=\"card-value " + covClass + "\">" + String.format("%.1f%%", coveragePercentage) + "</div>\n");
        cards.append("      <div class=\"meter " + covClass + "\"><i style=\"width:" + String.format(Locale.US, "%.1f", coveragePercentage) + "%\"></i></div>\n");
        cards.append("      <div class=\"card-subtitle\">" + untestedEndpoints + " endpoints untested</div>\n");
        cards.append("    </div>\n");

        // Card 4: Tested Endpoints
        cards.append("    <div class=\"card\">\n");
        cards.append("      <div class=\"card-title\">Tested Endpoints</div>\n");
        cards.append("      <div class=\"card-value\">" + totalEndpoints + "</div>\n");
        cards.append("      <div class=\"meter neutral\"><i style=\"width:100%\"></i></div>\n");
        cards.append("      <div class=\"card-subtitle\">With invariant simulation</div>\n");
        cards.append("    </div>\n");

        cards.append("  </div>\n");
        return cards.toString();
    }

    private static int[] calculateFaultStats(JsonNode faultSimulation) {
        int total = 0, detected = 0, escaped = 0;
        if (faultSimulation == null || faultSimulation.isNull()) return new int[]{0, 0, 0};

        Iterator<Map.Entry<String, JsonNode>> endpoints = faultSimulation.fields();
        while (endpoints.hasNext()) {
            JsonNode endpointData = endpoints.next().getValue();
            if (!endpointData.has("invariant_faults")) continue;
            Iterator<Map.Entry<String, JsonNode>> it = endpointData.get("invariant_faults").fields();
            while (it.hasNext()) {
                JsonNode fd = it.next().getValue();
                total++;
                if (fd.has("caught_by_any_test") && fd.get("caught_by_any_test").asBoolean()) detected++;
                else escaped++;
            }
        }
        return new int[]{total, detected, escaped};
    }

    private static String buildFaultSimulationSection(JsonNode faultSimulation) {
        if (faultSimulation == null || faultSimulation.isNull() || faultSimulation.size() == 0) {
            return "    <div class=\"empty-state\">No fault simulation data available</div>\n";
        }

        StringBuilder section = new StringBuilder();
        section.append("    <div class=\"section-title\">Fault Simulation Results</div>\n");
        section.append("    <div class=\"section-subtitle\">Showing which faults were detected or escaped by your tests</div>\n");

        int endpointIndex = 0;
        Iterator<Map.Entry<String, JsonNode>> endpoints = faultSimulation.fields();
        while (endpoints.hasNext()) {
            Map.Entry<String, JsonNode> endpoint = endpoints.next();
            String endpointPath = endpoint.getKey();
            JsonNode endpointData = endpoint.getValue();

            // Count invariant faults for this endpoint
            int invariantTotal = 0, invariantDetected = 0, invariantEscaped = 0;
            if (endpointData.has("invariant_faults")) {
                Iterator<Map.Entry<String, JsonNode>> it = endpointData.get("invariant_faults").fields();
                while (it.hasNext()) {
                    boolean caught = it.next().getValue().path("caught_by_any_test").asBoolean();
                    invariantTotal++;
                    if (caught) invariantDetected++; else invariantEscaped++;
                }
            }

            if (invariantTotal == 0) { endpointIndex++; continue; }

            section.append("    <div class=\"endpoint-card\">\n");
            section.append("      <div class=\"endpoint-header collapsible\" onclick=\"toggleEndpoint(" + endpointIndex + ")\">\n");
            section.append("        <div class=\"endpoint-title-section\">\n");
            section.append("          <span class=\"endpoint-path\">" + escapeHtml(endpointPath) + "</span>\n");
            section.append("          <div class=\"endpoint-summary\">\n");
            section.append("            <span class=\"summary-badge invariant\">" + invariantDetected + "/" + invariantTotal + " invariants</span>\n");
            section.append("            <span class=\"summary-badge escaped\">" + invariantEscaped + " escaped</span>\n");
            section.append("          </div>\n");
            section.append("        </div>\n");
            section.append("        <span class=\"collapse-icon collapsed\">▼</span>\n");
            section.append("      </div>\n");
            section.append("      <div id=\"endpoint-" + endpointIndex + "\" class=\"endpoint-content collapsed\">\n");

            // Build fault table
            section.append("      <div class=\"fault-table\">\n");
            section.append("        <div class=\"fault-table-header\">\n");
            section.append("          <div class=\"fault-cell\">Invariant</div>\n");
            section.append("          <div class=\"fault-cell\">Status</div>\n");
            section.append("          <div class=\"fault-cell\">Details</div>\n");
            section.append("        </div>\n");

            // Render invariant faults: invariantName -> result
            if (endpointData.has("invariant_faults")) {
                JsonNode invariantFaultsNode = endpointData.get("invariant_faults");
                Iterator<Map.Entry<String, JsonNode>> invariantIter = invariantFaultsNode.fields();
                while (invariantIter.hasNext()) {
                    Map.Entry<String, JsonNode> invariantEntry = invariantIter.next();
                    String invariantName = invariantEntry.getKey();
                    JsonNode faultData = invariantEntry.getValue();

                    boolean caught = faultData.has("caught_by_any_test") &&
                                    faultData.get("caught_by_any_test").asBoolean();

                    // Get tested_by and caught_by
                    JsonNode testedBy = faultData.get("tested_by");
                    JsonNode caughtBy = faultData.get("caught_by");
                    int testedCount = testedBy != null && testedBy.isArray() ? testedBy.size() : 0;

                    section.append("        <div class=\"fault-row\">\n");
                    section.append("          <div class=\"fault-cell\"><span class=\"fault-badge invariant-badge\">" + escapeHtml(invariantName) + "</span></div>\n");
                    section.append("          <div class=\"fault-cell\">");
                    section.append("<span class=\"status-badge " + (caught ? "detected" : "escaped") + "\">");
                    section.append(caught ? "✓ Detected" : "✗ Escaped");
                    section.append("</span></div>\n");
                    section.append("          <div class=\"fault-cell\">");
                    section.append("<button class=\"details-btn\" onclick=\"toggleDetails(this)\">View " + testedCount + " test(s)</button>");
                    section.append("<div class=\"test-details\" style=\"display:none;\">");

                    // Show all tests that tested this invariant
                    if (testedBy != null && testedBy.isArray()) {
                        for (JsonNode testName : testedBy) {
                            String test = testName.asText();
                            // Check if this test caught the fault
                            boolean testCaught = false;
                            String error = null;
                            if (caughtBy != null && caughtBy.isArray()) {
                                for (JsonNode caughtDetail : caughtBy) {
                                    if (caughtDetail.has("test") && caughtDetail.get("test").asText().equals(test)) {
                                        testCaught = true;
                                        if (caughtDetail.has("error") && !caughtDetail.get("error").isNull()) {
                                            error = caughtDetail.get("error").asText();
                                        }
                                        break;
                                    }
                                }
                            }

                            section.append("<div class=\"test-detail-item\">");
                            section.append("<span class=\"test-name\">" + escapeHtml(test) + "</span>");
                            section.append("<span class=\"status-badge " + (testCaught ? "detected" : "escaped") + "\">");
                            section.append(testCaught ? "✓" : "✗");
                            section.append("</span>");
                            if (error != null) {
                                section.append("<div class=\"error-message\">" + escapeHtml(error) + "</div>");
                            }
                            section.append("</div>");
                        }
                    }

                    section.append("</div>");
                    section.append("</div>\n");
                    section.append("        </div>\n");
                }
            }

            section.append("      </div>\n");
            section.append("      </div>\n"); // Close endpoint-content
            section.append("    </div>\n"); // Close endpoint-card

            endpointIndex++;
        }

        return section.toString();
    }

    private static String buildGapAnalysisSection(JsonNode gapAnalysis) {
        if (gapAnalysis == null || gapAnalysis.isNull() || gapAnalysis.size() == 0) {
            return "    <div class=\"empty-state\">No gap analysis data available</div>\n";
        }

        StringBuilder section = new StringBuilder();
        section.append("    <div class=\"section-title\">Executed Endpoints</div>\n");
        section.append("    <div class=\"section-subtitle\">Number of endpoints executions based on OpenAPI spec</div>\n");
        section.append("    <div class=\"section-subtitle\">This is not representative of your functional coverage. Use it as a proxy to identify potential coverage issues</div>\n");

        // Summary
        if (gapAnalysis.has("summary")) {
            JsonNode summary = gapAnalysis.get("summary");
            section.append("    <div class=\"gap-summary\">\n");
            section.append("      <div class=\"gap-stat\">\n");
            section.append("        <span class=\"gap-label\">Total Endpoints:</span>\n");
            section.append("        <span class=\"gap-value\">" + summary.get("total_endpoints_in_spec").asInt() + "</span>\n");
            section.append("      </div>\n");
            section.append("      <div class=\"gap-stat\">\n");
            section.append("        <span class=\"gap-label\">Tested:</span>\n");
            section.append("        <span class=\"gap-value good\">" + summary.get("tested_endpoints").asInt() + "</span>\n");
            section.append("      </div>\n");
            section.append("      <div class=\"gap-stat\">\n");
            section.append("        <span class=\"gap-label\">Untested:</span>\n");
            section.append("        <span class=\"gap-value warning\">" + summary.get("untested_endpoints").asInt() + "</span>\n");
            section.append("      </div>\n");
            section.append("      <div class=\"gap-stat\">\n");
            section.append("        <span class=\"gap-label\">Coverage:</span>\n");
            section.append("        <span class=\"gap-value\">" + String.format("%.1f%%", summary.get("coverage_percentage").asDouble()) + "</span>\n");
            section.append("      </div>\n");
            section.append("    </div>\n");
        }

        // Sub-tabs for Tested/Untested
        section.append("    <div class=\"sub-tabs\">\n");
        section.append("      <button class=\"sub-tab-button active\" onclick=\"showSubTab('tested-endpoints')\">Tested Endpoints</button>\n");
        section.append("      <button class=\"sub-tab-button\" onclick=\"showSubTab('untested-endpoints')\">Untested Endpoints</button>\n");
        section.append("    </div>\n");

        // Tested endpoints sub-tab
        section.append("    <div id=\"tested-endpoints\" class=\"sub-tab-content active\">\n");
        section.append(buildTestedEndpointsSection(gapAnalysis));
        section.append("    </div>\n");

        // Untested endpoints sub-tab
        section.append("    <div id=\"untested-endpoints\" class=\"sub-tab-content\">\n");
        section.append(buildUntestedEndpointsSection(gapAnalysis));
        section.append("    </div>\n");

        return section.toString();
    }

    private static String buildTestedEndpointsSection(JsonNode gapAnalysis) {
        StringBuilder section = new StringBuilder();

        // Tested endpoints
        if (gapAnalysis.has("tested_endpoints")) {
            JsonNode tested = gapAnalysis.get("tested_endpoints");
            if (tested.isArray() && tested.size() > 0) {
                section.append("    <div class=\"endpoint-list\">\n");

                int endpointIndex = 0;
                for (JsonNode endpoint : tested) {
                    String path = endpoint.has("path") ? endpoint.get("path").asText() : "";
                    String method = endpoint.has("method") ? endpoint.get("method").asText() : "";
                    JsonNode tests = endpoint.has("tests") ? endpoint.get("tests") : null;
                    int callCount = endpoint.has("call_count") ? endpoint.get("call_count").asInt() : 0;
                    int testCount = (tests != null && tests.isArray()) ? tests.size() : 0;

                    section.append("      <div class=\"endpoint-card gap-endpoint-card\">\n");
                    section.append("        <div class=\"endpoint-header collapsible\" onclick=\"toggleGapEndpoint('tested-" + endpointIndex + "')\">\n");
                    section.append("          <div class=\"endpoint-title-section\">\n");
                    section.append("            <div class=\"endpoint-main\">\n");
                    section.append("              <span class=\"http-method method-" + method.toLowerCase() + "\">" + method + "</span>\n");
                    section.append("              <span class=\"endpoint-path\">" + escapeHtml(path) + "</span>\n");
                    section.append("            </div>\n");
                    section.append("            <div class=\"endpoint-summary\">\n");
                    section.append("              <span class=\"summary-badge total\">" + testCount + " test(s)</span>\n");
                    section.append("              <span class=\"summary-badge total\">" + callCount + " call(s)</span>\n");
                    section.append("            </div>\n");
                    section.append("          </div>\n");
                    section.append("          <span class=\"collapse-icon collapsed\">▼</span>\n");
                    section.append("        </div>\n");
                    section.append("        <div id=\"tested-" + endpointIndex + "\" class=\"endpoint-content collapsed\">\n");

                    if (tests != null && tests.isArray() && tests.size() > 0) {
                        section.append("          <div class=\"test-list-expanded\">\n");
                        for (JsonNode test : tests) {
                            section.append("            <span class=\"test-tag\">" + escapeHtml(test.asText()) + "</span>\n");
                        }
                        section.append("          </div>\n");
                    }

                    section.append("        </div>\n");
                    section.append("      </div>\n");
                    endpointIndex++;
                }

                section.append("    </div>\n");
            } else {
                section.append("    <div class=\"empty-state\">No tested endpoints found</div>\n");
            }
        }

        return section.toString();
    }

    private static String buildUntestedEndpointsSection(JsonNode gapAnalysis) {
        StringBuilder section = new StringBuilder();

        // Untested endpoints
        if (gapAnalysis.has("untested_endpoints")) {
            JsonNode untested = gapAnalysis.get("untested_endpoints");
            if (untested.isArray() && untested.size() > 0) {
                section.append("    <div class=\"endpoint-list\">\n");

                int endpointIndex = 0;
                for (JsonNode endpoint : untested) {
                    String path = endpoint.has("path") ? endpoint.get("path").asText() : "";
                    String method = endpoint.has("method") ? endpoint.get("method").asText() : "";

                    section.append("      <div class=\"endpoint-card gap-endpoint-card untested-card\">\n");
                    section.append("        <div class=\"endpoint-header collapsible\" onclick=\"toggleGapEndpoint('untested-" + endpointIndex + "')\">\n");
                    section.append("          <div class=\"endpoint-title-section\">\n");
                    section.append("            <div class=\"endpoint-main\">\n");
                    section.append("              <span class=\"http-method method-" + method.toLowerCase() + "\">" + method + "</span>\n");
                    section.append("              <span class=\"endpoint-path\">" + escapeHtml(path) + "</span>\n");
                    section.append("            </div>\n");
                    section.append("            <div class=\"endpoint-summary\">\n");
//                    section.append("              <span class=\"summary-badge escaped\">Not Tested</span>\n");
                    section.append("            </div>\n");
                    section.append("          </div>\n");
                    section.append("          <span class=\"collapse-icon collapsed\">▼</span>\n");
                    section.append("        </div>\n");
                    section.append("        <div id=\"untested-" + endpointIndex + "\" class=\"endpoint-content collapsed\">\n");
                    section.append("          <div class=\"untested-info\">\n");
                    section.append("            <p>This endpoint is defined in the OpenAPI specification but has no test coverage.</p>\n");
                    section.append("            <p>Consider adding tests to improve API coverage.</p>\n");
                    section.append("          </div>\n");
                    section.append("        </div>\n");
                    section.append("      </div>\n");
                    endpointIndex++;
                }

                section.append("    </div>\n");
            } else {
                section.append("    <div class=\"empty-state\">All endpoints are tested! Great job!</div>\n");
            }
        }

        return section.toString();
    }

    private static String buildSchemaCoverageSection(JsonNode schemaCoverage) {
        if (schemaCoverage == null || schemaCoverage.isNull() || !schemaCoverage.has("paths")) {
            return "    <div class=\"empty-state\">No schema coverage data available</div>\n";
        }

        StringBuilder section = new StringBuilder();
        section.append("    <div class=\"section-title\">Schema Coverage Details</div>\n");
        section.append("    <div class=\"section-subtitle\">Detailed HTTP calls captured during test execution</div>\n");

        JsonNode paths = schemaCoverage.get("paths");
        Iterator<Map.Entry<String, JsonNode>> pathIter = paths.fields();

        int pathIndex = 0;
        while (pathIter.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathIter.next();
            String path = pathEntry.getKey();
            JsonNode methods = pathEntry.getValue();

            // Calculate summary for this path
            int methodCount = 0;
            int totalCalls = 0;
            int totalTests = 0;
            Iterator<Map.Entry<String, JsonNode>> summaryMethodIter = methods.fields();
            while (summaryMethodIter.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = summaryMethodIter.next();
                JsonNode methodData = methodEntry.getValue();
                methodCount++;
                if (methodData.has("summary")) {
                    JsonNode summary = methodData.get("summary");
                    totalCalls += summary.has("no_of_times_called") ? summary.get("no_of_times_called").asInt() : 0;
                    totalTests += summary.has("no_of_tests_calling") ? summary.get("no_of_tests_calling").asInt() : 0;
                }
            }

            section.append("    <div class=\"endpoint-card\">\n");
            section.append("      <div class=\"endpoint-header collapsible\" onclick=\"toggleSchemaEndpoint(" + pathIndex + ")\">\n");
            section.append("        <div class=\"endpoint-title-section\">\n");
            section.append("          <span class=\"endpoint-path\">" + escapeHtml(path) + "</span>\n");
            section.append("          <div class=\"endpoint-summary\">\n");
            section.append("            <span class=\"summary-badge total\">" + methodCount + " method(s)</span>\n");
            section.append("            <span class=\"summary-badge total\">" + totalCalls + " call(s)</span>\n");
            section.append("            <span class=\"summary-badge total\">" + totalTests + " test(s)</span>\n");
            section.append("          </div>\n");
            section.append("        </div>\n");
            section.append("        <span class=\"collapse-icon collapsed\">▼</span>\n");
            section.append("      </div>\n");
            section.append("      <div id=\"schema-endpoint-" + pathIndex + "\" class=\"endpoint-content collapsed\">\n");

            Iterator<Map.Entry<String, JsonNode>> methodIter = methods.fields();
            while (methodIter.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methodIter.next();
                String method = methodEntry.getKey();
                JsonNode methodData = methodEntry.getValue();

                section.append("        <div class=\"method-section\">\n");
                section.append("          <h4 class=\"method-title\"><span class=\"http-method method-" + method.toLowerCase() + "\">" + method + "</span></h4>\n");

                if (methodData.has("summary")) {
                    JsonNode summary = methodData.get("summary");
                    section.append("          <div class=\"coverage-stats\">\n");
                    section.append("            <span class=\"stat\">Calls: <strong>" + summary.get("no_of_times_called").asInt() + "</strong></span>\n");
                    section.append("            <span class=\"stat\">Tests: <strong>" + summary.get("no_of_tests_calling").asInt() + "</strong></span>\n");
                    section.append("          </div>\n");
                }

                if (methodData.has("calls") && methodData.get("calls").isArray()) {
                    JsonNode calls = methodData.get("calls");
                    section.append("          <div class=\"calls-container\">\n");

                    int callIndex = 0;
                    for (JsonNode call : calls) {
                        String testName = call.has("test") ? call.get("test").asText() : "Unknown";
                        int statusCode = call.has("response_status_code") ? call.get("response_status_code").asInt() : 0;
                        String statusClass = statusCode >= 200 && statusCode < 300 ? "success" :
                                           statusCode >= 400 ? "error" : "info";

                        section.append("            <div class=\"call-item\">\n");
                        section.append("              <div class=\"call-header\" onclick=\"toggleCall('call-" + pathIndex + "-" + callIndex + "')\">\n");
                        section.append("                <span class=\"test-name\">" + escapeHtml(testName) + "</span>\n");
                        section.append("                <span class=\"status-code " + statusClass + "\">" + statusCode + "</span>\n");
                        section.append("              </div>\n");
                        section.append("              <div id=\"call-" + pathIndex + "-" + callIndex + "\" class=\"call-details\" style=\"display:none;\">\n");

                        if (call.has("body") && !call.get("body").isNull()) {
                            section.append("                <div class=\"call-detail-section\">\n");
                            section.append("                  <strong>Request Body:</strong>\n");
                            section.append("                  <pre>" + escapeHtml(call.get("body").asText()) + "</pre>\n");
                            section.append("                </div>\n");
                        }

                        if (call.has("response_body") && !call.get("response_body").isNull()) {
                            section.append("                <div class=\"call-detail-section\">\n");
                            section.append("                  <strong>Response Body:</strong>\n");
                            section.append("                  <pre>" + escapeHtml(formatJson(call.get("response_body").asText())) + "</pre>\n");
                            section.append("                </div>\n");
                        }

                        section.append("              </div>\n");
                        section.append("            </div>\n");

                        callIndex++;
                    }

                    section.append("          </div>\n");
                }

                section.append("        </div>\n");
            }

            section.append("      </div>\n");
            section.append("    </div>\n");

            pathIndex++;
        }

        return section.toString();
    }

    private static String formatJson(String json) {
        try {
            Object obj = OBJECT_MAPPER.readValue(json, Object.class);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            return json;
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }

    private static String getCssStyles() {
        return """
:root {
    --bg-primary: #e6e9ef;
    --bg-secondary: #ffffff;
    --card-bg: #ffffff;
    --panel-header: #eef1f5;
    --text-primary: #1c1b17;
    --text-secondary: rgba(28, 27, 23, 0.60);
    --text-tertiary: rgba(28, 27, 23, 0.42);
    --border-color: rgba(28, 27, 23, 0.10);
    --border-strong: rgba(28, 27, 23, 0.18);
    --hover-bg: #f4f6f8;
    --grid-line: rgba(28, 27, 23, 0.07);
    --accent-primary: #4f46e5;
    --accent-soft: rgba(79, 70, 229, 0.08);
    --accent-success: #15803d;
    --accent-warning: #b45309;
    --accent-error: #b91c1c;
    --detected-bg: #e7f3ec;
    --detected-text: #15803d;
    --escaped-bg: #fbecec;
    --escaped-text: #b91c1c;
    --mono: ui-monospace, "SF Mono", "JetBrains Mono", "Roboto Mono", Menlo, Consolas, monospace;
    --radius: 3px;
}

[data-theme="dark"] {
    --bg-primary: #0e1014;
    --bg-secondary: #15171c;
    --card-bg: #15171c;
    --panel-header: #1a1d23;
    --text-primary: #e7e9ec;
    --text-secondary: rgba(231, 233, 236, 0.62);
    --text-tertiary: rgba(231, 233, 236, 0.42);
    --border-color: rgba(231, 233, 236, 0.10);
    --border-strong: rgba(231, 233, 236, 0.20);
    --hover-bg: #1c1f26;
    --grid-line: rgba(231, 233, 236, 0.08);
    --accent-primary: #818cf8;
    --accent-soft: rgba(129, 140, 248, 0.14);
    --accent-success: #4ade80;
    --accent-warning: #fbbf24;
    --accent-error: #f87171;
    --detected-bg: rgba(34, 197, 94, 0.12);
    --detected-text: #6ee7b7;
    --escaped-bg: rgba(248, 113, 113, 0.12);
    --escaped-text: #fca5a5;
}

* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    background: var(--bg-primary);
    min-height: 100vh;
    padding: 0 0 64px;
    color: var(--text-primary);
    font-size: 13px;
    line-height: 1.5;
    -webkit-font-smoothing: antialiased;
    transition: background 0.15s, color 0.15s;
}

/* ---- Header / app bar ---- */
.header {
    background: var(--bg-secondary);
    border-bottom: 1px solid var(--border-color);
    margin-bottom: 28px;
}

.header-content {
    max-width: 1400px;
    margin: 0 auto;
    padding: 22px 32px;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.brand {
    display: flex;
    align-items: center;
    gap: 9px;
    font-size: 19px;
    font-weight: 600;
    letter-spacing: -0.02em;
    color: var(--text-primary);
}

.brand-mark {
    width: 11px;
    height: 11px;
    background: var(--accent-primary);
    border-radius: 2px;
    transform: rotate(45deg);
    display: inline-block;
}

.brand-sub {
    font-family: var(--mono);
    font-size: 11px;
    font-weight: 500;
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--text-tertiary);
    padding-left: 2px;
}

.subtitle {
    font-size: 12.5px;
    color: var(--text-secondary);
    margin-top: 6px;
}

.timestamp {
    font-family: var(--mono);
    font-size: 11px;
    color: var(--text-tertiary);
    margin-top: 3px;
}

.theme-toggle {
    font-family: var(--mono);
    font-size: 10.5px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--text-secondary);
    background: var(--bg-primary);
    border: 1px solid var(--border-color);
    border-radius: var(--radius);
    padding: 8px 14px;
    cursor: pointer;
    transition: all 0.15s;
}

.theme-toggle:hover {
    border-color: var(--accent-primary);
    color: var(--accent-primary);
}

/* ---- KPI tiles ---- */
.summary-cards {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    gap: 14px;
    margin: 0 auto 28px;
    max-width: 1400px;
    padding: 0 32px;
}

.card {
    background: var(--card-bg);
    border: 1px solid var(--border-color);
    border-radius: var(--radius);
    padding: 16px 18px;
    display: flex;
    flex-direction: column;
    gap: 11px;
}

.card-title {
    font-family: var(--mono);
    font-size: 10.5px;
    color: var(--text-tertiary);
    text-transform: uppercase;
    letter-spacing: 0.1em;
    font-weight: 500;
}

.card-value {
    font-family: var(--mono);
    font-size: 32px;
    font-weight: 600;
    line-height: 1;
    letter-spacing: -0.02em;
    font-variant-numeric: tabular-nums;
    color: var(--text-primary);
}

.card-value.good { color: var(--accent-success); }
.card-value.warning { color: var(--accent-warning); }
.card-value.bad { color: var(--accent-error); }

.card-subtitle {
    font-size: 12px;
    color: var(--text-secondary);
    line-height: 1.45;
}

.meter {
    height: 4px;
    background: var(--hover-bg);
    border-radius: 2px;
    overflow: hidden;
}

.meter > i {
    display: block;
    height: 100%;
    background: var(--accent-primary);
    border-radius: 2px;
}

.meter.good > i { background: var(--accent-success); }
.meter.warning > i { background: var(--accent-warning); }
.meter.bad > i { background: var(--accent-error); }
.meter.neutral > i { background: var(--border-strong); }

/* ---- Tabs ---- */
.tabs {
    display: flex;
    gap: 4px;
    margin: 0 auto 24px;
    max-width: 1400px;
    padding: 0 32px;
    border-bottom: 1px solid var(--border-color);
}

.tab-button {
    padding: 11px 18px;
    background: transparent;
    border: none;
    border-bottom: 2px solid transparent;
    font-family: var(--mono);
    font-size: 11.5px;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s;
    color: var(--text-secondary);
    margin-bottom: -1px;
}

.tab-button:hover {
    color: var(--text-primary);
}

.tab-button.active {
    color: var(--accent-primary);
    border-bottom-color: var(--accent-primary);
}

.tab-content {
    display: none;
    max-width: 1400px;
    margin: 0 auto;
    padding: 0 32px;
}

.tab-content.active {
    display: block;
}

/* ---- Sub-tabs ---- */
.sub-tabs {
    display: inline-flex;
    gap: 2px;
    margin-bottom: 22px;
    background: var(--hover-bg);
    padding: 3px;
    border-radius: var(--radius);
    border: 1px solid var(--border-color);
}

.sub-tab-button {
    padding: 6px 14px;
    background: transparent;
    border: none;
    border-radius: 2px;
    font-family: var(--mono);
    font-size: 11px;
    letter-spacing: 0.04em;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s;
    color: var(--text-secondary);
}

.sub-tab-button:hover {
    color: var(--text-primary);
}

.sub-tab-button.active {
    background: var(--bg-secondary);
    color: var(--accent-primary);
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}

.sub-tab-content {
    display: none;
}

.sub-tab-content.active {
    display: block;
}

/* ---- Section headings ---- */
.section-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-primary);
    margin-bottom: 5px;
    letter-spacing: -0.01em;
}

.section-subtitle {
    font-size: 12.5px;
    color: var(--text-secondary);
    margin-bottom: 6px;
}

.section-subtitle:last-of-type {
    margin-bottom: 22px;
}

/* ---- Panels ---- */
.endpoint-card {
    background: var(--card-bg);
    border-radius: var(--radius);
    margin-bottom: 10px;
    border: 1px solid var(--border-strong);
    overflow: hidden;
}

.endpoint-header.collapsible {
    cursor: pointer;
    display: flex;
    justify-content: space-between;
    align-items: center;
    transition: background 0.15s;
    padding: 14px 18px;
    background: var(--panel-header);
}

.endpoint-header.collapsible:hover {
    background: var(--hover-bg);
}

/* Expanded panel — clearly distinct from a collapsed row */
.endpoint-card:has(.endpoint-content:not(.collapsed)) {
    border-color: var(--accent-primary);
    box-shadow: inset 3px 0 0 var(--accent-primary);
}

.endpoint-card:has(.endpoint-content:not(.collapsed)) > .endpoint-header.collapsible {
    background: var(--accent-soft);
    border-bottom: 1px solid var(--border-strong);
}

.endpoint-card:has(.endpoint-content:not(.collapsed)) > .endpoint-header.collapsible .endpoint-path {
    color: var(--accent-primary);
}

.endpoint-card:has(.endpoint-content:not(.collapsed)) > .endpoint-header.collapsible .collapse-icon {
    color: var(--accent-primary);
    border-color: var(--accent-primary);
    background: var(--bg-secondary);
}

.endpoint-title-section {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
}

.endpoint-summary {
    display: flex;
    gap: 6px;
    align-items: center;
}

.summary-badge {
    display: inline-flex;
    align-items: center;
    padding: 3px 9px;
    border-radius: 2px;
    font-family: var(--mono);
    font-size: 11px;
    font-weight: 500;
    letter-spacing: 0.02em;
}

.summary-badge.detected {
    background: var(--detected-bg);
    color: var(--detected-text);
}

.summary-badge.escaped {
    background: var(--escaped-bg);
    color: var(--escaped-text);
}

.summary-badge.invariant {
    background: var(--accent-soft);
    color: var(--accent-primary);
}

.summary-badge.total {
    background: var(--hover-bg);
    color: var(--text-secondary);
    border: 1px solid var(--border-color);
}

.collapse-icon {
    font-size: 8px;
    color: var(--text-secondary);
    margin-left: 14px;
    width: 22px;
    height: 22px;
    flex: none;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border: 1px solid var(--border-strong);
    border-radius: 2px;
    background: var(--bg-secondary);
    transition: transform 0.15s, color 0.15s, border-color 0.15s, background 0.15s;
}

.collapse-icon.collapsed {
    transform: rotate(-90deg);
}

.endpoint-content {
    transition: max-height 0.2s ease, opacity 0.15s;
    max-height: 6000px;
    opacity: 1;
    overflow: hidden;
}

.endpoint-content.collapsed {
    max-height: 0;
    opacity: 0;
}

.endpoint-path {
    font-family: var(--mono);
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
}

/* ---- Fault data grid ---- */
.fault-table {
    display: flex;
    flex-direction: column;
    background: var(--card-bg);
}

.fault-table-header {
    display: grid;
    grid-template-columns: 2fr 1fr 1.5fr;
    background: var(--card-bg);
    font-family: var(--mono);
    font-weight: 500;
    color: var(--text-tertiary);
    font-size: 10px;
    text-transform: uppercase;
    letter-spacing: 0.1em;
    border-bottom: 1px solid var(--border-strong);
}

.fault-row {
    display: grid;
    grid-template-columns: 2fr 1fr 1.5fr;
    background: var(--card-bg);
    border-bottom: 1px solid var(--grid-line);
}

.fault-row:last-child {
    border-bottom: none;
}

.fault-row:hover {
    background: var(--hover-bg);
}

.fault-cell {
    padding: 11px 18px;
    display: flex;
    align-items: center;
}

.fault-badge {
    font-family: var(--mono);
    font-size: 12px;
    color: var(--text-primary);
}

.fault-badge.invariant-badge {
    color: var(--text-primary);
    font-weight: 500;
}

.status-badge {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 3px 9px 3px 8px;
    border-radius: 2px;
    font-family: var(--mono);
    font-size: 11px;
    font-weight: 500;
    letter-spacing: 0.02em;
}

.status-badge::before {
    content: "";
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: currentColor;
}

.status-badge.detected {
    background: var(--detected-bg);
    color: var(--detected-text);
}

.status-badge.escaped {
    background: var(--escaped-bg);
    color: var(--escaped-text);
}

.details-btn {
    padding: 5px 11px;
    background: transparent;
    color: var(--accent-primary);
    border: 1px solid var(--border-strong);
    border-radius: 2px;
    cursor: pointer;
    font-family: var(--mono);
    font-size: 11px;
    font-weight: 500;
    transition: all 0.15s;
}

.details-btn:hover {
    border-color: var(--accent-primary);
    background: var(--accent-soft);
}

.test-details {
    margin-top: 12px;
    padding: 12px;
    background: var(--hover-bg);
    border-radius: var(--radius);
    flex: 1 0 100%;
}

.test-detail-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 9px 12px;
    background: var(--card-bg);
    border-radius: 2px;
    margin-bottom: 6px;
    border: 1px solid var(--border-color);
}

.test-detail-item:last-child {
    margin-bottom: 0;
}

.test-name {
    flex: 1;
    font-family: var(--mono);
    font-size: 12px;
    color: var(--text-primary);
}

.error-message {
    flex: 1 0 100%;
    margin-top: 8px;
    padding: 9px 11px;
    background: var(--escaped-bg);
    border-left: 2px solid var(--accent-error);
    border-radius: 2px;
    font-size: 12px;
    color: var(--escaped-text);
    font-family: var(--mono);
    line-height: 1.5;
}

/* ---- Gap summary strip ---- */
.gap-summary {
    display: flex;
    gap: 0;
    background: var(--card-bg);
    border-radius: var(--radius);
    margin-bottom: 24px;
    border: 1px solid var(--border-color);
    overflow: hidden;
}

.gap-stat {
    display: flex;
    flex-direction: column;
    gap: 7px;
    padding: 16px 22px;
    flex: 1;
    border-right: 1px solid var(--border-color);
}

.gap-stat:last-child {
    border-right: none;
}

.gap-label {
    font-family: var(--mono);
    font-size: 10.5px;
    color: var(--text-tertiary);
    text-transform: uppercase;
    letter-spacing: 0.08em;
    font-weight: 500;
}

.gap-value {
    font-family: var(--mono);
    font-size: 26px;
    font-weight: 600;
    color: var(--text-primary);
    font-variant-numeric: tabular-nums;
    letter-spacing: -0.02em;
}

.gap-value.good { color: var(--accent-success); }
.gap-value.warning { color: var(--accent-warning); }

.endpoint-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.endpoint-main {
    display: flex;
    align-items: center;
    gap: 12px;
}

.http-method {
    display: inline-block;
    padding: 3px 8px;
    border-radius: 2px;
    font-family: var(--mono);
    font-size: 10.5px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    min-width: 52px;
    text-align: center;
}

.method-get { background: var(--accent-soft); color: var(--accent-primary); }
.method-post { background: var(--detected-bg); color: var(--detected-text); }
.method-put { background: rgba(180, 83, 9, 0.12); color: var(--accent-warning); }
.method-delete { background: var(--escaped-bg); color: var(--escaped-text); }
.method-patch { background: var(--hover-bg); color: var(--text-secondary); }

.test-tag {
    display: inline-block;
    padding: 4px 9px;
    background: var(--hover-bg);
    color: var(--text-secondary);
    border-radius: 2px;
    font-size: 11.5px;
    font-family: var(--mono);
    border: 1px solid var(--border-color);
}

.method-section {
    margin: 16px 18px;
    padding: 14px 16px;
    background: var(--hover-bg);
    border-radius: var(--radius);
}

.method-title {
    font-size: 13px;
    margin-bottom: 12px;
}

.coverage-stats {
    display: flex;
    gap: 20px;
    margin-bottom: 14px;
    font-family: var(--mono);
    font-size: 12px;
}

.stat {
    color: var(--text-secondary);
}

.calls-container {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.call-item {
    background: var(--card-bg);
    border-radius: 2px;
    overflow: hidden;
    border: 1px solid var(--border-color);
}

.call-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 14px;
    cursor: pointer;
    transition: background 0.15s;
}

.call-header:hover {
    background: var(--hover-bg);
}

.status-code {
    padding: 3px 9px;
    border-radius: 2px;
    font-family: var(--mono);
    font-size: 11.5px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
}

.status-code.success { background: var(--detected-bg); color: var(--detected-text); }
.status-code.error { background: var(--escaped-bg); color: var(--escaped-text); }
.status-code.info { background: var(--accent-soft); color: var(--accent-primary); }

.call-details {
    padding: 14px;
    background: var(--bg-primary);
    border-top: 1px solid var(--border-color);
}

.call-detail-section {
    margin-bottom: 14px;
}

.call-detail-section:last-child {
    margin-bottom: 0;
}

.call-detail-section strong {
    display: block;
    margin-bottom: 7px;
    color: var(--text-secondary);
    font-family: var(--mono);
    font-size: 10.5px;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    font-weight: 500;
}

.call-detail-section pre {
    background: var(--card-bg);
    padding: 12px;
    border-radius: 2px;
    border: 1px solid var(--border-color);
    overflow-x: auto;
    font-family: var(--mono);
    font-size: 12px;
    line-height: 1.55;
    color: var(--text-primary);
}

code {
    font-family: var(--mono);
    font-size: 12px;
    padding: 1px 5px;
    background: var(--hover-bg);
    border-radius: 2px;
    border: 1px solid var(--border-color);
}

.empty-state {
    text-align: center;
    padding: 48px 20px;
    background: var(--card-bg);
    border-radius: var(--radius);
    color: var(--text-secondary);
    font-size: 13px;
    border: 1px dashed var(--border-strong);
}

.gap-endpoint-card {
    margin-bottom: 8px;
}

.untested-card {
    border-left: 2px solid var(--accent-error);
}

.test-list-expanded {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding: 14px 18px;
}

.untested-info {
    padding: 14px 18px;
    color: var(--text-secondary);
    font-size: 12.5px;
}

.untested-info p {
    margin-bottom: 6px;
    line-height: 1.55;
}

.untested-info p:last-child {
    margin-bottom: 0;
}
""";
    }

    private static String getJavaScript() {
        return """
function showTab(tabId) {
    // Hide all tabs
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });

    // Remove active class from all buttons
    document.querySelectorAll('.tab-button').forEach(btn => {
        btn.classList.remove('active');
    });

    // Show selected tab
    document.getElementById(tabId).classList.add('active');

    // Add active class to clicked button
    event.target.classList.add('active');
}

function showSubTab(subTabId) {
    // Hide all sub-tabs
    document.querySelectorAll('.sub-tab-content').forEach(tab => {
        tab.classList.remove('active');
    });

    // Remove active class from all sub-tab buttons
    document.querySelectorAll('.sub-tab-button').forEach(btn => {
        btn.classList.remove('active');
    });

    // Show selected sub-tab
    document.getElementById(subTabId).classList.add('active');

    // Add active class to clicked button
    event.target.classList.add('active');
}

function toggleEndpoint(index) {
    const content = document.getElementById('endpoint-' + index);
    const icon = event.currentTarget.querySelector('.collapse-icon');

    if (content.classList.contains('collapsed')) {
        content.classList.remove('collapsed');
        icon.classList.remove('collapsed');
    } else {
        content.classList.add('collapsed');
        icon.classList.add('collapsed');
    }
}

function toggleDetails(btn) {
    const details = btn.nextElementSibling;
    if (details.style.display === 'none') {
        details.style.display = 'block';
        btn.textContent = btn.textContent.replace('View', 'Hide');
    } else {
        details.style.display = 'none';
        btn.textContent = btn.textContent.replace('Hide', 'View');
    }
}

function toggleCall(id) {
    const details = document.getElementById(id);
    if (details.style.display === 'none') {
        details.style.display = 'block';
    } else {
        details.style.display = 'none';
    }
}

function toggleGapEndpoint(id) {
    const content = document.getElementById(id);
    const icon = event.currentTarget.querySelector('.collapse-icon');

    if (content.classList.contains('collapsed')) {
        content.classList.remove('collapsed');
        icon.classList.remove('collapsed');
    } else {
        content.classList.add('collapsed');
        icon.classList.add('collapsed');
    }
}

function toggleSchemaEndpoint(index) {
    const content = document.getElementById('schema-endpoint-' + index);
    const icon = event.currentTarget.querySelector('.collapse-icon');

    if (content.classList.contains('collapsed')) {
        content.classList.remove('collapsed');
        icon.classList.remove('collapsed');
    } else {
        content.classList.add('collapsed');
        icon.classList.add('collapsed');
    }
}

function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    const label = document.querySelector('.theme-label');

    document.documentElement.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);

    // Label shows the mode you'll switch to next
    if (label) label.textContent = newTheme === 'dark' ? 'Light' : 'Dark';
}

// Initialize theme from localStorage
document.addEventListener('DOMContentLoaded', function() {
    const savedTheme = localStorage.getItem('theme') || 'light';
    const label = document.querySelector('.theme-label');

    document.documentElement.setAttribute('data-theme', savedTheme);
    if (label) label.textContent = savedTheme === 'dark' ? 'Light' : 'Dark';
});
""";
    }
}
