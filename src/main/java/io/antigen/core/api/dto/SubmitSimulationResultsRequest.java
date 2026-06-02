package io.antigen.core.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class SubmitSimulationResultsRequest {
    
    private String testSuiteName;
    private UUID strategyId;
    
    @JsonProperty("executionStartTime")
    private LocalDateTime executionStartTime;
    
    @JsonProperty("executionEndTime")
    private LocalDateTime executionEndTime;
    
    private String agentVersion;
    private Map<String, EndpointResults> results;
    private SimulationSummary summary;

    @Data
    public static class EndpointResults {
        private Map<String, FieldResults> fields;
        
        public Map<String, FieldResults> getFields() { return fields; }
        public void setFields(Map<String, FieldResults> fields) { this.fields = fields; }
    }

    @Data
    public static class FieldResults {
        @JsonProperty("null_field")
        private FaultTypeResult nullField;
        
        @JsonProperty("missing_field")
        private FaultTypeResult missingField;

        @JsonProperty("empty_string")
        private FaultTypeResult emptyString;

        @JsonProperty("empty_list")
        private FaultTypeResult emptyList;
        
        @JsonProperty("invalid_value")
        private FaultTypeResult invalidValue;
        

    }

    @Data
    public static class FaultTypeResult {
        @JsonProperty("caught_by_any")
        private Boolean caughtByAny;
        private List<TestResult> results;

    }

    @Data
    public static class TestResult {
        private String test;
        private Boolean caught;
        private String error;
    }

    @Data
    public static class SimulationSummary {
        private Integer totalFaultTypes;
        private Integer faultTypesCaught;
        private Integer faultTypesMissed;
        private Double faultCoverageScore;
        private Integer totalTestExecutions;
        private Double testExecutionSuccessRate;

    }

}