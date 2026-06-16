package io.antigen.core.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Merges all configuration sources into a single ResolvedTestConfig for simulation.
 *
 * Merge order:
 *   1. invariants/*.yml        (invariants — additive)
 *   2. <ClassName>.antigen.yml class-level
 *   3. <ClassName>.antigen.yml method-level  (most specific)
 *
 * - invariants:  additive  (all levels contribute)
 * - settings:    override  (most specific wins)
 * - exclusions:  additive  (union)
 */
public class ConfigResolver {

    public static ResolvedTestConfig resolve(
            Class<?> testClass,
            Optional<TestScopedConfig> classConfig,
            String methodName) {

        List<FeatureConfig> features = InvariantConfigCache.getInstance().getAllFeatures();

        TestScopedConfig scopedConfig = classConfig.orElse(null);
        TestMethodConfig methodConfig = scopedConfig != null ? findMethodConfig(scopedConfig, methodName) : null;

        if (methodConfig != null && methodConfig.isExclude()) {
            return ResolvedTestConfig.SKIP;
        }

        boolean stopOnFirstCatch = resolveStopOnFirstCatch(scopedConfig, methodConfig);
        String defaultQuantifier = resolveDefaultQuantifier(scopedConfig, methodConfig);
        Map<String, List<InvariantConfig>> mergedInvariants =
                mergeInvariants(features, testClass.getName(), methodName, scopedConfig, methodConfig);
        List<Pattern> excludedPatterns = mergeExcludedEndpointPatterns(scopedConfig, methodConfig);

        return new ResolvedTestConfig(false, stopOnFirstCatch, defaultQuantifier, mergedInvariants, excludedPatterns);
    }

    // ── Method config lookup ──────────────────────────────────────────────────

    static TestMethodConfig findMethodConfig(TestScopedConfig scopedConfig, String methodName) {
        if (scopedConfig.tests == null || scopedConfig.tests.isEmpty()) return null;

        TestMethodConfig exact = scopedConfig.tests.get(methodName);
        if (exact != null) return exact;

        TestMethodConfig bestMatch = null;
        int bestWildcardCount = Integer.MAX_VALUE;

        for (Map.Entry<String, TestMethodConfig> entry : scopedConfig.tests.entrySet()) {
            String pattern = entry.getKey();
            if (!pattern.contains("*") && !pattern.contains("?")) continue;
            if (globMatches(pattern, methodName)) {
                int wildcardCount = countWildcards(pattern);
                if (wildcardCount < bestWildcardCount) {
                    bestWildcardCount = wildcardCount;
                    bestMatch = entry.getValue();
                }
            }
        }

        return bestMatch;
    }

    private static boolean globMatches(String pattern, String input) {
        return input.matches(pattern.replace(".", "\\.").replace("*", ".*").replace("?", "."));
    }

    private static int countWildcards(String pattern) {
        int count = 0;
        for (char c : pattern.toCharArray()) {
            if (c == '*' || c == '?') count++;
        }
        return count;
    }

    // ── Settings resolution ───────────────────────────────────────────────────

    private static boolean resolveStopOnFirstCatch(TestScopedConfig classConfig, TestMethodConfig methodConfig) {
        if (methodConfig != null && methodConfig.settings != null)
            return methodConfig.settings.stop_on_first_catch;
        if (classConfig != null && classConfig.settings != null)
            return classConfig.settings.stop_on_first_catch;
        return SimulatorConfig.isStopOnFirstCatchEnabled();
    }

    private static String resolveDefaultQuantifier(TestScopedConfig classConfig, TestMethodConfig methodConfig) {
        if (methodConfig != null && methodConfig.settings != null && methodConfig.settings.default_quantifier != null)
            return methodConfig.settings.default_quantifier;
        if (classConfig != null && classConfig.settings != null && classConfig.settings.default_quantifier != null)
            return classConfig.settings.default_quantifier;
        return SimulatorConfig.getDefaultQuantifier();
    }

    // ── Invariant merging ─────────────────────────────────────────────────────

    private static Map<String, List<InvariantConfig>> mergeInvariants(
            List<FeatureConfig> features,
            String className,
            String methodName,
            TestScopedConfig classConfig,
            TestMethodConfig methodConfig) {

        Map<String, List<InvariantConfig>> result = new HashMap<>();

        for (FeatureConfig feature : features) {
            addFeatureInvariants(feature, className, methodName, result);
        }

        if (classConfig != null && classConfig.endpoints != null) {
            addEndpointInvariants(classConfig.endpoints, result);
        }

        if (methodConfig != null && methodConfig.endpoints != null) {
            addEndpointInvariants(methodConfig.endpoints, result);
        }

        return result;
    }

    /**
     * Adds a feature's invariants for the given test, applying the include_only
     * cascade per invariant: invariant-level include_only overrides feature-level;
     * absent at both levels means the invariant applies to any test (auto). The
     * downstream simulator further filters by which endpoints the test exercises.
     */
    static void addFeatureInvariants(
            FeatureConfig feature,
            String className,
            String methodName,
            Map<String, List<InvariantConfig>> target) {

        Map<String, Map<String, MethodInvariantsConfig>> source = feature.getInvariants();
        if (source == null) return;

        for (Map.Entry<String, Map<String, MethodInvariantsConfig>> endpointEntry : source.entrySet()) {
            String endpointPattern = endpointEntry.getKey();
            Map<String, MethodInvariantsConfig> methodMap = endpointEntry.getValue();
            if (methodMap == null) continue;

            for (Map.Entry<String, MethodInvariantsConfig> methodEntry : methodMap.entrySet()) {
                String httpMethod = methodEntry.getKey().toUpperCase();
                MethodInvariantsConfig mic = methodEntry.getValue();
                if (mic == null || mic.getInvariants() == null) continue;

                for (InvariantConfig invariant : mic.getInvariants()) {
                    List<FeatureTestMapping> effectiveScope = invariant.getIncludeOnly() != null
                            ? invariant.getIncludeOnly()
                            : feature.getIncludeOnly();

                    if (!FeatureTestMapping.scopeMatches(effectiveScope, className, methodName)) continue;

                    target.computeIfAbsent(endpointPattern + "::" + httpMethod, k -> new ArrayList<>())
                            .add(invariant);
                }
            }
        }
    }

    static void addEndpointInvariants(
            Map<String, Map<String, MethodInvariantsConfig>> source,
            Map<String, List<InvariantConfig>> target) {

        if (source == null) return;

        for (Map.Entry<String, Map<String, MethodInvariantsConfig>> endpointEntry : source.entrySet()) {
            String endpointPattern = endpointEntry.getKey();
            Map<String, MethodInvariantsConfig> methodMap = endpointEntry.getValue();
            if (methodMap == null) continue;

            for (Map.Entry<String, MethodInvariantsConfig> methodEntry : methodMap.entrySet()) {
                String method = methodEntry.getKey().toUpperCase();
                MethodInvariantsConfig mic = methodEntry.getValue();
                if (mic == null || mic.getInvariants() == null || mic.getInvariants().isEmpty()) continue;

                target.computeIfAbsent(endpointPattern + "::" + method, k -> new ArrayList<>())
                        .addAll(mic.getInvariants());
            }
        }
    }

    // ── Exclusion merging ─────────────────────────────────────────────────────

    private static List<Pattern> mergeExcludedEndpointPatterns(
            TestScopedConfig classConfig, TestMethodConfig methodConfig) {

        List<String> patterns = new ArrayList<>();

        if (classConfig != null && classConfig.exclusions != null && classConfig.exclusions.endpoints != null)
            patterns.addAll(classConfig.exclusions.endpoints);
        if (methodConfig != null && methodConfig.exclusions != null && methodConfig.exclusions.endpoints != null)
            patterns.addAll(methodConfig.exclusions.endpoints);

        List<Pattern> compiled = new ArrayList<>();
        for (String glob : patterns) {
            compiled.add(Pattern.compile(glob.replace(".", "\\.").replace("*", ".*").replace("?", ".")));
        }
        return compiled;
    }
}
