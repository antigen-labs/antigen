package io.antigen.core.analytics;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.File;
import java.util.*;

public class OpenAPISpecLoader {

    public static class EndpointInfo {
        private String path;
        private String method;

        public EndpointInfo(String path, String method) {
            this.path = path;
            this.method = method;
        }

        public String getPath() {
            return path;
        }

        public String getMethod() {
            return method;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EndpointInfo that = (EndpointInfo) o;
            return Objects.equals(path, that.path) && Objects.equals(method, that.method);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, method);
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    public static Set<EndpointInfo> loadEndpoints(String specPath) {
        if (specPath == null || specPath.isEmpty()) {
            System.out.println("[Gap Analysis] No OpenAPI spec path provided");
            return Collections.emptySet();
        }

        File specFile = new File(specPath);
        if (!specFile.exists()) {
            System.err.println("[Gap Analysis] OpenAPI spec file not found: " + specPath);
            return Collections.emptySet();
        }

        try {
            OpenAPIV3Parser parser = new OpenAPIV3Parser();
            SwaggerParseResult parseResult = parser.readLocation(specPath, null, null);

            if (parseResult == null || parseResult.getOpenAPI() == null) {
                System.err.println("[Gap Analysis] Failed to parse OpenAPI spec: " + specPath);
                if (parseResult != null && parseResult.getMessages() != null && !parseResult.getMessages().isEmpty()) {
                    System.err.println("[Gap Analysis] Parse errors: " + parseResult.getMessages());
                }
                return Collections.emptySet();
            }

            OpenAPI openAPI = parseResult.getOpenAPI();
            Set<EndpointInfo> endpoints = new HashSet<>();

            if (openAPI.getPaths() != null) {
                for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
                    String path = pathEntry.getKey();
                    PathItem pathItem = pathEntry.getValue();

                    // Extract all HTTP methods for this path
                    if (pathItem.getGet() != null) {
                        endpoints.add(new EndpointInfo(path, "GET"));
                    }
                    if (pathItem.getPost() != null) {
                        endpoints.add(new EndpointInfo(path, "POST"));
                    }
                    if (pathItem.getPut() != null) {
                        endpoints.add(new EndpointInfo(path, "PUT"));
                    }
                    if (pathItem.getPatch() != null) {
                        endpoints.add(new EndpointInfo(path, "PATCH"));
                    }
                    if (pathItem.getDelete() != null) {
                        endpoints.add(new EndpointInfo(path, "DELETE"));
                    }
                    if (pathItem.getHead() != null) {
                        endpoints.add(new EndpointInfo(path, "HEAD"));
                    }
                    if (pathItem.getOptions() != null) {
                        endpoints.add(new EndpointInfo(path, "OPTIONS"));
                    }
                }
            }

            System.out.println("[Gap Analysis] Loaded " + endpoints.size() + " endpoints from OpenAPI spec");
            return endpoints;

        } catch (Exception e) {
            System.err.println("[Gap Analysis] Error loading OpenAPI spec: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptySet();
        }
    }
}
