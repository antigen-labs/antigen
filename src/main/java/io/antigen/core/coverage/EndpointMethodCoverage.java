package io.antigen.core.coverage;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.*;

@Data
public class EndpointMethodCoverage {

    @JsonProperty("summary")
    private CoverageSummary summary;

    @JsonProperty("calls")
    private List<EndpointCall> calls;

    public EndpointMethodCoverage() {
        this.summary = new CoverageSummary();
        this.calls = new ArrayList<>();
    }

    public synchronized void addCall(EndpointCall call) {
        calls.add(call);
        updateSummary(call);
    }

    private void updateSummary(EndpointCall call) {
        summary.no_of_times_called++;

        if (call.getTestName() != null && !summary.tests.contains(call.getTestName())) {
            summary.tests.add(call.getTestName());
            summary.no_of_tests_calling++;
        }
    }

    @Data
    public static class CoverageSummary {
        @JsonProperty("no_of_times_called")
        private int no_of_times_called = 0;

        @JsonProperty("no_of_tests_calling")
        private int no_of_tests_calling = 0;

        @JsonProperty("tests")
        private Set<String> tests = new LinkedHashSet<>();
    }
}
