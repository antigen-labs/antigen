package io.antigen.core.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.*;

@Data
public class GapReport {

    @JsonProperty("metadata")
    private Metadata metadata;

    @JsonProperty("summary")
    private Summary summary;

    @JsonProperty("untested_endpoints")
    private List<EndpointDetail> untestedEndpoints;

    @JsonProperty("tested_endpoints")
    private List<EndpointDetail> testedEndpoints;

    public GapReport() {
        this.metadata = new Metadata();
        this.summary = new Summary();
        this.untestedEndpoints = new ArrayList<>();
        this.testedEndpoints = new ArrayList<>();
    }

    @Data
    public static class Metadata {
        @JsonProperty("generated_at")
        private String generatedAt = Instant.now().toString();

        @JsonProperty("openapi_spec_path")
        private String openapiSpecPath;
    }

    @Data
    public static class Summary {
        @JsonProperty("total_endpoints_in_spec")
        private int totalEndpointsInSpec = 0;

        @JsonProperty("tested_endpoints")
        private int testedEndpoints = 0;

        @JsonProperty("untested_endpoints")
        private int untestedEndpoints = 0;

        @JsonProperty("coverage_percentage")
        private double coveragePercentage = 0.0;
    }

    @Data
    public static class EndpointDetail {
        @JsonProperty("path")
        private String path;

        @JsonProperty("method")
        private String method;

        @JsonProperty("tests")
        private Set<String> tests;

        @JsonProperty("call_count")
        private Integer callCount;

        public EndpointDetail(String path, String method) {
            this.path = path;
            this.method = method;
        }

        public EndpointDetail(String path, String method, Set<String> tests, int callCount) {
            this.path = path;
            this.method = method;
            this.tests = tests;
            this.callCount = callCount;
        }
    }

    public void calculateSummary() {
        summary.totalEndpointsInSpec = testedEndpoints.size() + untestedEndpoints.size();
        summary.testedEndpoints = testedEndpoints.size();
        summary.untestedEndpoints = untestedEndpoints.size();

        if (summary.totalEndpointsInSpec > 0) {
            summary.coveragePercentage = Math.round((double) summary.testedEndpoints / summary.totalEndpointsInSpec * 10000.0) / 100.0;
        }
    }
}
