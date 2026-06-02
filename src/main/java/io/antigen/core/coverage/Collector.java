package io.antigen.core.coverage;

import io.antigen.core.config.CoverageConfig;

public class Collector {
    private static volatile CollectorData data = new CollectorData();

    public static synchronized CollectorData getData() {
        if (data == null) {
            data = new CollectorData();
        }
        return data;
    }

    public static void saveCoverageReport() {
        CoverageConfig config = CoverageConfig.getInstance();

        // Only save if coverage is enabled
        if (!config.isEnabled()) {
            return;
        }

        String outputFile = config.getOutputFile();
        if (outputFile != null && !outputFile.isEmpty()) {
            System.out.println("Saving coverage report to: " + outputFile);
            FileUtils.saveToJsonFile(outputFile);
        } else {
            System.out.println("Coverage output file not configured. Skipping coverage report.");
        }
    }
}
