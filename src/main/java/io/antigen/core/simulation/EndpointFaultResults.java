package io.antigen.core.simulation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents all fault simulation results for a single endpoint.
 * Separates results into contract faults (field-level mutations) and invariant faults (business rule violations).
 */
@Data
public class EndpointFaultResults {

    /**
     * Contract faults grouped by fault type, then by field name.
     * Structure: { "null_field": { "status": {...}, "id": {...} }, "missing_field": { ... } }
     */
    @JsonProperty("contract_faults")
    private Map<String, Map<String, FaultSimulationResult>> contractFaults;

    /**
     * Invariant faults grouped by invariant name.
     * Structure: { "positive_quantity": {...}, "filled_order_has_filled_at": {...} }
     */
    @JsonProperty("invariant_faults")
    private Map<String, FaultSimulationResult> invariantFaults;

    public EndpointFaultResults() {
        this.contractFaults = new ConcurrentHashMap<>();
        this.invariantFaults = new ConcurrentHashMap<>();
    }

    /**
     * Records a contract fault result (field-level mutation).
     */
    public void recordContractFault(String faultType, String field, TestLevelSimulationResults result) {
        contractFaults
                .computeIfAbsent(faultType, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(field, k -> new FaultSimulationResult())
                .addTestResult(result);
    }

    /**
     * Records a invariant fault result (business rule violation).
     */
    public void recordInvariantFault(String invariantName, TestLevelSimulationResults result) {
        invariantFaults
                .computeIfAbsent(invariantName, k -> new FaultSimulationResult())
                .addTestResult(result);
    }

    /**
     * Returns the total number of contract fault types tested.
     */
    public int getContractFaultCount() {
        int count = 0;
        for (Map<String, FaultSimulationResult> fields : contractFaults.values()) {
            count += fields.size();
        }
        return count;
    }

    /**
     * Returns the number of contract faults that were caught.
     */
    public int getContractFaultsCaught() {
        int count = 0;
        for (Map<String, FaultSimulationResult> fields : contractFaults.values()) {
            for (FaultSimulationResult result : fields.values()) {
                if (result.isCaughtByAnyTest()) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Returns the total number of invariant faults tested.
     */
    public int getInvariantFaultCount() {
        return invariantFaults.size();
    }

    /**
     * Returns the number of invariant faults that were caught.
     */
    public int getInvariantFaultsCaught() {
        int count = 0;
        for (FaultSimulationResult result : invariantFaults.values()) {
            if (result.isCaughtByAnyTest()) {
                count++;
            }
        }
        return count;
    }
}
