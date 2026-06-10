package io.antigen.core.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class SimulatorConfig {

    public String version;
    public Settings settings;
    public Map<String, Map<String, MethodInvariantsConfig>> endpoints = new HashMap<>();
    public Exclusions exclusions;
    public Simulation simulation;
    public Report report;

    // Legacy fields kept for YAML deserialization compatibility
    public Url url;
    public Tests tests;

    @Data
    public static class Settings {
        public String default_quantifier = "all";
        public boolean stop_on_first_catch = false;
    }

    @Data
    public static class Exclusions {
        public List<String> urls;
        public List<String> endpoints;
        public List<String> tests;
    }

    @Data
    public static class Simulation {
        public List<Integer> allowed_status_codes;
        public boolean only_success_responses = true;
        public boolean skip_collections_response = true;
        public int min_response_fields = 1;
        public List<String> skip_if_contains_fields;
        public MultipleEndpointsStrategy multiple_endpoints_strategy;
    }

    @Data
    public static class MultipleEndpointsStrategy {
        public boolean test_only_last_endpoint = false;
        public List<String> exclude_endpoints;
    }

    @Data
    public static class Report {
        public String format;
        public String output_path;
    }

    @Data
    public static class Url {
        public List<String> exclude;
    }

    @Data
    public static class Tests {
        public List<String> exclude;
    }

    // ── Static defaults used at runtime ──────────────────────────────────────

    private static final Simulation DEFAULT_SIMULATION = new Simulation();
    private static final MultipleEndpointsStrategy DEFAULT_STRATEGY = new MultipleEndpointsStrategy();

    public static boolean shouldSimulateResponse(int statusCode, Map<String, Object> responseMap, String responseBody) {
        if (statusCode < 200 || statusCode >= 300) {
            System.out.println("[Antigen-Sim] Skipping — non-success status: " + statusCode);
            return false;
        }
        if (isCollectionResponse(responseBody)) {
            System.out.println("[Antigen-Sim] Skipping — response is a collection");
            return false;
        }
        if (responseMap == null || responseMap.size() < 1) {
            System.out.println("[Antigen-Sim] Skipping — empty response");
            return false;
        }
        return true;
    }

    private static boolean isCollectionResponse(String body) {
        return body != null && body.trim().startsWith("[");
    }

    public static boolean isEndpointExcluded(String endpoint) {
        return false;
    }

    public static boolean isTestExcluded(String testName) {
        return false;
    }

    public static String getDefaultQuantifier() {
        return "all";
    }

    public static boolean isStopOnFirstCatchEnabled() {
        return false;
    }

    public static MultipleEndpointsStrategy getMultipleEndpointsStrategy() {
        return DEFAULT_STRATEGY;
    }

    public static Map<String, Map<String, MethodInvariantsConfig>> getAllEndpointInvariants() {
        return new HashMap<>();
    }

    public static List<InvariantConfig> getInvariantsForEndpoint(String endpointPath, String httpMethod) {
        return new ArrayList<>();
    }

    public static boolean hasInvariants(String endpointPath, String httpMethod) {
        return false;
    }
}
