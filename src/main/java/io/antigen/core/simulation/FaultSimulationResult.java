package io.antigen.core.simulation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the aggregated result of simulating a specific fault on a specific field.
 * Contains a flag indicating if ANY test caught the fault, plus detailed results from all tests.
 */
@Data
public class FaultSimulationResult {

    /**
     * True if at least one test caught this fault, false if all tests missed it.
     */
    @JsonProperty("caught_by_any_test")
    private boolean caughtByAnyTest;

    /**
     * List of all test names that were used to test this mutation.
     */
    @JsonProperty("tested_by")
    private List<String> testedBy;

    /**
     * Detailed results from tests that caught this fault.
     */
    @JsonProperty("caught_by")
    private List<TestLevelSimulationResults> caughtBy;

    public FaultSimulationResult() {
        this.caughtByAnyTest = false;
        this.testedBy = new ArrayList<>();
        this.caughtBy = new ArrayList<>();
    }

    /**
     * Adds a test result to this fault simulation result.
     * Updates caughtByAnyTest flag if the test caught the fault.
     *
     * @param testResult The result from a single test execution
     */
    public void addTestResult(TestLevelSimulationResults testResult) {
        if (testResult == null) {
            return;
        }

        // Always add to tested_by list
        if (!testedBy.contains(testResult.getTest())) {
            testedBy.add(testResult.getTest());
        }

        // Only add to caught_by if the test caught the fault
        if (testResult.isCaught()) {
            caughtByAnyTest = true;
            caughtBy.add(testResult);
        }
    }

    /**
     * Returns true if no tests have been added yet.
     * This is a utility method not included in JSON serialization.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return testedBy.isEmpty();
    }

    /**
     * Returns the number of test results.
     * This is a utility method not included in JSON serialization.
     */
    @JsonIgnore
    public int size() {
        return testedBy.size();
    }

    /**
     * Returns the detailed results (for backward compatibility).
     * @deprecated Use getCaughtBy() instead
     */
    @JsonIgnore
    @Deprecated
    public List<TestLevelSimulationResults> getDetails() {
        return caughtBy;
    }
}
