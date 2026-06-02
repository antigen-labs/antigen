package io.antigen.core.invariant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts field values from JSON response maps using path notation.
 *
 * Supported path formats:
 * - Simple: "field_name"
 * - Nested: "parent.child.field"
 * - Array all elements: "$[*].field"
 * - Array with nested: "$[*].parent.field"
 * - JSONPath reference: "$.field" (for field comparison)
 */
public class FieldExtractor {

    /**
     * Extracts value(s) from a response map using the given path.
     *
     * @param responseMap The parsed JSON response as a map
     * @param path The field path (dot notation or JSONPath-like)
     * @return The extracted value, or a List of values for array paths, or null if not found
     */
    @SuppressWarnings("unchecked")
    public static Object extractValue(Map<String, Object> responseMap, String path) {
        if (responseMap == null || path == null || path.isEmpty()) {
            return null;
        }

        // Handle $[*] array notation - extracts from array at root level
        if (path.startsWith("$[*].")) {
            String remainingPath = path.substring(5); // Remove "$[*]."
            Object rootValue = responseMap.get("$root");
            if (rootValue instanceof List) {
                return extractFromArray((List<Object>) rootValue, remainingPath);
            }
            // If responseMap itself represents array items (rare case)
            return null;
        }

        // Handle $. prefix for field reference (just strip it)
        if (path.startsWith("$.")) {
            path = path.substring(2);
        }

        return extractNestedValue(responseMap, path);
    }

    /**
     * Extracts value(s) from a response, handling both object and array responses.
     *
     * @param response The response - either Map or List
     * @param path The field path
     * @return The extracted value(s)
     */
    @SuppressWarnings("unchecked")
    public static Object extractValueFromResponse(Object response, String path) {
        if (response == null || path == null || path.isEmpty()) {
            return null;
        }

        // Handle array responses with $[*] notation
        if (path.startsWith("$[*].") && response instanceof List) {
            String remainingPath = path.substring(5);
            return extractFromArray((List<Object>) response, remainingPath);
        }

        // Handle object responses
        if (response instanceof Map) {
            return extractValue((Map<String, Object>) response, path);
        }

        return null;
    }

    /**
     * Extracts a nested value using dot notation.
     */
    @SuppressWarnings("unchecked")
    private static Object extractNestedValue(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (current == null) {
                return null;
            }

            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else if (current instanceof List) {
                // If we hit a list and have more path to traverse, extract from each element
                List<Object> results = new ArrayList<>();
                for (Object item : (List<Object>) current) {
                    if (item instanceof Map) {
                        Object value = ((Map<String, Object>) item).get(part);
                        if (value != null) {
                            results.add(value);
                        }
                    }
                }
                current = results.isEmpty() ? null : results;
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * Extracts values from each element in an array.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> extractFromArray(List<Object> array, String path) {
        List<Object> results = new ArrayList<>();
        for (Object item : array) {
            if (item instanceof Map) {
                Object value = extractNestedValue((Map<String, Object>) item, path);
                if (value != null) {
                    results.add(value);
                }
            }
        }
        return results;
    }

    /**
     * Checks if a path represents an array field path ($[*].field)
     */
    public static boolean isArrayPath(String path) {
        return path != null && path.startsWith("$[*].");
    }

    /**
     * Checks if a value is a field reference ($.field)
     */
    public static boolean isFieldReference(Object value) {
        return value instanceof String && ((String) value).startsWith("$.");
    }

    /**
     * Extracts the field name from a field reference.
     * "$.field_name" -> "field_name"
     */
    public static String extractReferenceField(String reference) {
        if (reference != null && reference.startsWith("$.")) {
            return reference.substring(2);
        }
        return reference;
    }

    /**
     * Checks if a field exists in the response map.
     */
    public static boolean fieldExists(Map<String, Object> responseMap, String path) {
        if (responseMap == null || path == null || path.isEmpty()) {
            return false;
        }

        if (path.startsWith("$.")) {
            path = path.substring(2);
        }

        String[] parts = path.split("\\.");
        Object current = responseMap;

        for (int i = 0; i < parts.length; i++) {
            if (!(current instanceof Map)) {
                return false;
            }
            Map<String, Object> currentMap = (Map<String, Object>) current;
            String part = parts[i];

            if (!currentMap.containsKey(part)) {
                return false;
            }

            current = currentMap.get(part);
        }

        return true;
    }
}
