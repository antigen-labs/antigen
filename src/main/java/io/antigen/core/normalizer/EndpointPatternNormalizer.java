package io.antigen.core.normalizer;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility class for normalizing URL paths to endpoint patterns.
 *
 * Converts concrete URLs like "/api/v1/orders/123" to patterns like "/api/v1/orders/{id}".
 * This enables aggregating results across multiple tests that hit the same endpoint pattern
 * but with different parameter values.
 *
 * Uses OpenAPI spec when available, otherwise falls back to heuristics.
 *
 * Example transformations:
 * - /api/v1/orders/123 -> /api/v1/orders/{id}
 * - /api/v1/users/abc-def-123 -> /api/v1/users/{id}
 * - /api/v1/products/p-12345/reviews/456 -> /api/v1/products/{id}/reviews/{id}
 * - /api/v1/stocks/AAPL -> /api/v1/stocks/{id}
 * - /api/v1/search?query=test -> /api/v1/search (query params ignored)
 */
public class EndpointPatternNormalizer {

    // Pattern to match numeric IDs (e.g., 123, 456789, +1, -1, +123, -456)
    // Matches optional +/- followed by digits
    private static final Pattern NUMERIC_ID = Pattern.compile("^[+-]?\\d+$");

    // Pattern to match floating point numbers (e.g., 1.5, +2.3, -3.14)
    private static final Pattern FLOAT_ID = Pattern.compile("^[+-]?\\d+\\.\\d+$");

    // Pattern to match UUIDs (e.g., 550e8400-e29b-41d4-a716-446655440000)
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    // Pattern to match alphanumeric IDs with separators (e.g., abc-123, p-12345, user_456)
    private static final Pattern ALPHANUMERIC_ID = Pattern.compile("^[a-zA-Z0-9]+-[0-9]+$|^[a-zA-Z]+-[a-zA-Z0-9-]+$|^[a-zA-Z0-9]+_[0-9]+$");

    // Pattern to match long alphanumeric strings (likely tokens or IDs)
    private static final Pattern LONG_ALPHANUMERIC = Pattern.compile("^[a-zA-Z0-9]{16,}$");

    // Pattern to match stock symbols (2-5 uppercase letters)
    private static final Pattern STOCK_SYMBOL = Pattern.compile("^[A-Z]{2,5}$");

    // Pattern to match account numbers (e.g., ACC12190023)
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^[A-Z]{2,4}[0-9]{5,}$");

    // Cached OpenAPI path patterns
    private static volatile Set<String> openApiPathPatterns = null;
    private static volatile boolean openApiLoaded = false;

    private EndpointPatternNormalizer() {
    }

    /**
     * Loads OpenAPI spec path patterns from the default location.
     */
    private static void loadOpenApiPatterns() {
        if (openApiLoaded) {
            return;
        }

        synchronized (EndpointPatternNormalizer.class) {
            if (openApiLoaded) {
                return;
            }

            openApiPathPatterns = new HashSet<>();
            String[] possiblePaths = {"api-specs.yaml", "lib/api-specs.yaml", "openapi.yaml", "swagger.yaml"};

            for (String specPath : possiblePaths) {
                File specFile = new File(specPath);
                if (specFile.exists()) {
                    try {
                        OpenAPIV3Parser parser = new OpenAPIV3Parser();
                        SwaggerParseResult parseResult = parser.readLocation(specPath, null, null);

                        if (parseResult != null && parseResult.getOpenAPI() != null) {
                            OpenAPI openAPI = parseResult.getOpenAPI();
                            if (openAPI.getPaths() != null) {
                                // Normalize OpenAPI patterns to use {id} instead of specific parameter names
                                for (String path : openAPI.getPaths().keySet()) {
                                    String normalizedPath = normalizeOpenApiPath(path);
                                    openApiPathPatterns.add(normalizedPath);
                                }
                                System.out.println("[Normalizer] Loaded " + openApiPathPatterns.size() +
                                                 " patterns from OpenAPI spec: " + specPath);
                            }
                            break;
                        }
                    } catch (Exception e) {
                        System.err.println("[Normalizer] Failed to load OpenAPI spec from " + specPath + ": " + e.getMessage());
                    }
                }
            }

            openApiLoaded = true;
        }
    }

    /**
     * Normalizes an OpenAPI path pattern to use {id} for all parameters.
     * Example: /api/v1/stocks/{symbol} -> /api/v1/stocks/{id}
     */
    private static String normalizeOpenApiPath(String path) {
        if (path == null) {
            return path;
        }
        // Replace any {paramName} with {id}
        return path.replaceAll("\\{[^}]+\\}", "{id}");
    }

    /**
     * Tries to match a concrete path against OpenAPI patterns.
     * Returns the matching pattern if found, null otherwise.
     */
    private static String matchOpenApiPattern(String path) {
        if (openApiPathPatterns == null || openApiPathPatterns.isEmpty()) {
            return null;
        }

        String[] pathSegments = path.split("/");

        for (String pattern : openApiPathPatterns) {
            String[] patternSegments = pattern.split("/");

            if (pathSegments.length == patternSegments.length) {
                boolean matches = true;
                for (int i = 0; i < pathSegments.length; i++) {
                    if (!patternSegments[i].equals("{id}") && !patternSegments[i].equals(pathSegments[i])) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return pattern;
                }
            }
        }

        return null;
    }

    /**
     * Normalizes a URL path to an endpoint pattern by replacing dynamic segments with {id}.
     *
     * Uses OpenAPI spec patterns when available, otherwise falls back to heuristics.
     *
     * @param path The URL path to normalize (e.g., "/api/v1/orders/123")
     * @return The normalized pattern (e.g., "/api/v1/orders/{id}")
     */
    public static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        // Load OpenAPI patterns on first use
        if (!openApiLoaded) {
            loadOpenApiPatterns();
        }

        // Remove query parameters if present
        int queryIndex = path.indexOf('?');
        if (queryIndex != -1) {
            path = path.substring(0, queryIndex);
        }

        // Remove trailing slash for consistency
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        // Try to match against OpenAPI patterns first
        String openApiMatch = matchOpenApiPattern(path);
        if (openApiMatch != null) {
            return openApiMatch;
        }

        // Fall back to heuristic-based normalization
        String[] segments = path.split("/");
        StringBuilder normalized = new StringBuilder();

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];

            if (i == 0 && segment.isEmpty()) {
                // Leading slash - preserve it
                normalized.append("/");
                continue;
            }

            if (segment.isEmpty()) {
                // Skip empty segments (e.g., double slashes)
                continue;
            }

            // Check if segment looks like a dynamic parameter
            if (isLikelyDynamicParameter(segment)) {
                if (normalized.length() > 1) {
                    normalized.append("/");
                }
                normalized.append("{id}");
            } else {
                if (normalized.length() > 1) {
                    normalized.append("/");
                }
                normalized.append(segment);
            }
        }

        // Handle root path case
        if (normalized.length() == 0) {
            return "/";
        }

        return normalized.toString();
    }

    /**
     * Determines if a path segment is likely a dynamic parameter (ID, UUID, token, etc.)
     * rather than a static resource name.
     *
     * @param segment The path segment to check
     * @return true if the segment looks like a dynamic parameter
     */
    private static boolean isLikelyDynamicParameter(String segment) {
        // Empty segment is not a parameter
        if (segment == null || segment.isEmpty()) {
            return false;
        }

        // Common resource names that should NOT be treated as IDs
        // Check this FIRST before other patterns
        String lowerSegment = segment.toLowerCase();
        if (lowerSegment.equals("v1") || lowerSegment.equals("v2") || lowerSegment.equals("v3") ||
            lowerSegment.equals("api") || lowerSegment.startsWith("v") && lowerSegment.length() == 2) {
            return false;
        }

        // Stock symbols (e.g., "AAPL", "GOOGL", "MSFT")
        if (STOCK_SYMBOL.matcher(segment).matches()) {
            return true;
        }

        // Account numbers (e.g., "ACC12190023")
        if (ACCOUNT_NUMBER.matcher(segment).matches()) {
            return true;
        }

        // Numeric ID (e.g., "123", "456789", "+1", "-1")
        if (NUMERIC_ID.matcher(segment).matches()) {
            return true;
        }

        // Floating point numbers (e.g., "1.5", "+2.3", "-3.14")
        if (FLOAT_ID.matcher(segment).matches()) {
            return true;
        }

        // UUID (e.g., "550e8400-e29b-41d4-a716-446655440000")
        if (UUID_PATTERN.matcher(segment).matches()) {
            return true;
        }

        // Alphanumeric ID with separators (e.g., "abc-123", "p-12345", "user_456")
        if (ALPHANUMERIC_ID.matcher(segment).matches()) {
            return true;
        }

        // Long alphanumeric strings (likely tokens or encoded IDs)
        if (LONG_ALPHANUMERIC.matcher(segment).matches()) {
            return true;
        }

        return false;
    }

    /**
     * Checks if two paths match the same endpoint pattern.
     *
     * @param path1 First path
     * @param path2 Second path
     * @return true if both paths normalize to the same pattern
     */
    public static boolean matchesPattern(String path1, String path2) {
        return normalize(path1).equals(normalize(path2));
    }
}
