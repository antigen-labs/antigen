package io.antigen.core.interceptor;

import io.antigen.core.simulation.FaultSimulationReport;
import io.antigen.core.coverage.Collector;
import io.antigen.core.analytics.GapAnalyzer;
import io.antigen.core.report.HtmlReportGenerator;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.io.File;

public class GlobalTestExecutionListener implements TestExecutionListener {

    private static boolean executed = false;
    private final boolean runWithAntigen = Boolean.parseBoolean(System.getProperty("runWithAntigen"));

    public GlobalTestExecutionListener() {
        System.out.println("[Antigen] GlobalTestExecutionListener initialized. runWithAntigen=" + runWithAntigen);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        System.out.println("[Antigen] testPlanExecutionFinished called. executed=" + executed + ", runWithAntigen=" + runWithAntigen);
        if (!executed && runWithAntigen) {
            executed = true;
            System.out.println("[Antigen] All tests completed - Generating reports...");
            new File(FaultSimulationReport.OUTPUT_DIR).mkdirs();

            FaultSimulationReport.getInstance().printConsoleSummary();
            FaultSimulationReport.getInstance().createJSONReport();
            Collector.saveCoverageReport();
            GapAnalyzer.generateGapReport();

            String htmlPath = FaultSimulationReport.OUTPUT_DIR + "/antigen_report.html";
            try {
                HtmlReportGenerator.generateReport(htmlPath);
                System.out.println("[Antigen] HTML report: " + htmlPath);
            } catch (Exception e) {
                System.err.println("[Antigen] Failed to generate HTML report: " + e.getMessage());
            }

            System.out.println("[Antigen] Reports written to " + FaultSimulationReport.OUTPUT_DIR);
        } else {
            System.out.println("[Antigen] Skipping report generation (executed=" + executed + ", runWithAntigen=" + runWithAntigen + ")");
        }
    }
}