package io.antigen.ai.phases;

import io.antigen.ai.model.EscapedFault;
import lombok.Value;

import java.util.List;

@Value
public class AntigenPhase implements PhaseResult {
    boolean success;
    List<EscapedFault> escapedFaults;
    double faultDetectionRate;
    int totalFaults;
    int caughtFaults;

    public static AntigenPhase success(double detectionRate, int total, int caught) {
        return new AntigenPhase(true, List.of(), detectionRate, total, caught);
    }

    public static AntigenPhase failed(List<EscapedFault> escaped, double detectionRate, int total, int caught) {
        return new AntigenPhase(false, escaped, detectionRate, total, caught);
    }

    public boolean hasEscapedFaults() {
        return !escapedFaults.isEmpty();
    }

    @Override
    public String getFeedback() {
        if (success) {
            return String.format("Antigen passed - %.1f%% fault detection rate (%d/%d faults caught)",
                faultDetectionRate * 100, caughtFaults, totalFaults);
        }

        return String.format("""
            ANTIGEN FAILURE - Your tests did NOT catch %d out of %d injected faults (%.1f%% detection rate).

            IMPORTANT: Use the Read tool to read build/antigen/fault_simulation_report.json.
            DO NOT write scripts to parse it - read it directly with the Read tool.

            The report structure:
            {
              "/api/endpoint": {
                "invariant_faults": {
                  "invariant_name": {
                    "caught_by_any_test": false,  <- false means this fault escaped
                    "tested_by": ["testMethod"],
                    "caught_by": []
                  }
                }
              }
            }

            Your task:
            1. Read build/antigen/fault_simulation_report.json
            2. Find all entries where "caught_by_any_test": false
            3. Look at the "tested_by" array to see which tests ran against this fault
            4. Update those tests to add proper assertions to catch that particular fault
            """,
            escapedFaults.size(),
            totalFaults,
            faultDetectionRate * 100);
    }
}
