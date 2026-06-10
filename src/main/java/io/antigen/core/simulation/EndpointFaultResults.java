package io.antigen.core.simulation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class EndpointFaultResults {

    @JsonProperty("invariant_faults")
    private Map<String, FaultSimulationResult> invariantFaults;

    public EndpointFaultResults() {
        this.invariantFaults = new ConcurrentHashMap<>();
    }

    public void recordInvariantFault(String invariantName, TestLevelSimulationResults result) {
        invariantFaults
                .computeIfAbsent(invariantName, k -> new FaultSimulationResult())
                .addTestResult(result);
    }

    public int getInvariantFaultCount() {
        return invariantFaults.size();
    }

    public int getInvariantFaultsCaught() {
        return (int) invariantFaults.values().stream()
                .filter(FaultSimulationResult::isCaughtByAnyTest)
                .count();
    }
}
