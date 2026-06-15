package io.antigen.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Maps a test class (and optionally specific methods) to an invariant scope.
 * Used inside an `include_only:` block — either feature-level (restricting all
 * invariants in the file) or per-invariant (overriding the feature-level scope).
 *
 * Examples:
 * <pre>
 * include_only:
 *   # All methods in class
 *   - class: com.example.orders.OrderApiTest
 *
 *   # Specific methods (exact names or glob patterns)
 *   - class: com.example.orders.OrderAdminTest
 *     methods:
 *       - testGetFilledOrder
 *       - "testCreate*"
 * </pre>
 */
@Data
public class FeatureTestMapping {

    /**
     * Fully-qualified class name of the test class.
     * Example: com.example.orders.OrderApiTest
     */
    @JsonProperty("class")
    private String className;

    /**
     * Optional list of method names to include.
     * Supports exact names and glob patterns (e.g. "testGet*").
     * If null or empty, the feature applies to ALL methods in the class.
     */
    private List<String> methods;

    public boolean appliesToAllMethods() {
        return methods == null || methods.isEmpty();
    }

    /**
     * Returns true if this mapping matches the given test class and method.
     * Method names support exact match and glob patterns ("testGet*").
     */
    public boolean matches(String testClassName, String testMethodName) {
        if (className == null || !className.equals(testClassName)) return false;
        if (appliesToAllMethods()) return true;
        for (String pattern : methods) {
            if (globMatches(pattern, testMethodName)) return true;
        }
        return false;
    }

    private static boolean globMatches(String pattern, String input) {
        if (input == null) return false;
        if (!pattern.contains("*") && !pattern.contains("?")) {
            return pattern.equals(input);
        }
        String regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        return input.matches(regex);
    }

    /**
     * Resolves whether a scope (list of include_only mappings) applies to a test.
     * A null/empty scope means "no restriction" — applies to every test (auto).
     */
    public static boolean scopeMatches(List<FeatureTestMapping> scope,
                                       String testClassName, String testMethodName) {
        if (scope == null || scope.isEmpty()) return true;
        for (FeatureTestMapping mapping : scope) {
            if (mapping.matches(testClassName, testMethodName)) return true;
        }
        return false;
    }
}
