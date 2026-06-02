package io.antigen.core.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ContractFaultStrategyResponse {
    
    private UUID id;
    
    @JsonProperty("projectId")
    private UUID projectId;
    
    private String name;
    private String description;
    private String category;
    private Boolean enabled;
    private Faults faults;
    private Exclusions exclusions;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @JsonProperty("updatedAt")  
    private LocalDateTime updatedAt;

    @Data
    public static class Faults {
        @JsonProperty("null_field")
        private FaultConfig nullField;
        
        @JsonProperty("missing_field")
        private FaultConfig missingField;

        @JsonProperty("empty_string")
        private FaultConfig emptyString;

        @JsonProperty("empty_list")
        private FaultConfig emptyList;
        
        @JsonProperty("invalid_data_type")
        private FaultConfig invalidDataType;
        
        @JsonProperty("invalid_value")
        private FaultConfig invalidValue;
        
        @JsonProperty("http_method_change")
        private FaultConfig httpMethodChange;
        
        @JsonProperty("status_code_change")
        private FaultConfig statusCodeChange;
        
        @JsonProperty("delay_injection")
        private DelayFaultConfig delayInjection;

    }

    @Data
    public static class FaultConfig {
        private Boolean enabled;


    }

    @Data
    public static class DelayFaultConfig extends FaultConfig {
        @JsonProperty("delay_ms")
        private Integer delayMs;

    }

    @Data
    public static class Exclusions {
        private List<String> urls;
        private List<String> endpoints;
        private List<String> tests;

    }
}