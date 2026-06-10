package io.antigen.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The fully-merged configuration for a single test method execution.
 * Produced by ConfigResolver by combining: features/*.yml + class .antigen.yml + method .antigen.yml
 *
 * - invariants:  additive (all levels contribute)
 * - settings:    override (most specific wins)
 * - exclusions:  additive (union)
 */
public class ResolvedTestConfig {

    public static final ResolvedTestConfig SKIP = new ResolvedTestConfig(
            true, false, "all", Map.of(), List.of()
    );

    private final boolean skip;
    private final boolean stopOnFirstCatch;
    private final String defaultQuantifier;
    private final Map<String, List<InvariantConfig>> invariants;
    private final List<Pattern> excludedEndpointPatterns;

    public ResolvedTestConfig(
            boolean skip,
            boolean stopOnFirstCatch,
            String defaultQuantifier,
            Map<String, List<InvariantConfig>> invariants,
            List<Pattern> excludedEndpointPatterns) {
        this.skip = skip;
        this.stopOnFirstCatch = stopOnFirstCatch;
        this.defaultQuantifier = defaultQuantifier;
        this.invariants = Collections.unmodifiableMap(invariants);
        this.excludedEndpointPatterns = Collections.unmodifiableList(new ArrayList<>(excludedEndpointPatterns));
    }

    public boolean isSkip() { return skip; }
    public boolean isStopOnFirstCatch() { return stopOnFirstCatch; }
    public String getDefaultQuantifier() { return defaultQuantifier; }

    public List<InvariantConfig> getInvariantsFor(String endpointPattern, String httpMethod) {
        return invariants.getOrDefault(endpointPattern + "::" + httpMethod.toUpperCase(), List.of());
    }

    public boolean isEndpointExcluded(String endpoint) {
        if (endpoint == null) return false;
        for (Pattern p : excludedEndpointPatterns) {
            if (p.matcher(endpoint).matches()) return true;
        }
        return false;
    }

    public boolean hasAnyInvariants() { return !invariants.isEmpty(); }
}
