package io.antigen.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root model for an invariant file.
 * Placed in: src/test/resources/antigen/simulation/invariants/<name>.yml
 *
 * An invariant file groups related business invariants under a name. Invariants
 * apply automatically to any test that exercises the matching endpoint, unless
 * narrowed via include_only.
 *
 * Invariants defined here are merged additively with:
 *   - class-level .antigen.yml invariants
 *   - method-level .antigen.yml invariants
 *
 * Example:
 * <pre>
 * name: Order Lifecycle
 * description: Rules governing order state transitions and data consistency
 *
 * invariants:
 *   /api/v1/orders/{id}:
 *     GET:
 *       - name: filled_order_has_filled_at
 *         if:
 *           field: status
 *           equals: FILLED
 *         then:
 *           field: filled_at
 *           is_not_null: true
 *
 * # Optional: restrict ALL invariants in this feature to specific tests.
 * # When omitted, invariants apply automatically to any test that calls
 * # the matching endpoint (auto-detection). A per-invariant include_only
 * # overrides this feature-level default.
 * include_only:
 *   - class: com.example.orders.OrderApiTest
 *     methods:
 *       - testGetFilledOrder
 *       - "testCreate*"
 * </pre>
 */
@Data
public class FeatureConfig {

    /** Display name for this set of invariants (used in reports) */
    private String name;

    /** Optional human-readable description of what these invariants cover */
    private String description;

    /**
     * Invariant rules grouped by endpoint path and HTTP method.
     * Structure: Map<endpoint_path, Map<http_method, MethodInvariantsConfig>>
     * Same DSL as config.yml endpoints section.
     */
    private Map<String, Map<String, MethodInvariantsConfig>> invariants = new HashMap<>();

    /**
     * Optional feature-level scope. When present, restricts this feature's
     * invariants to the listed tests. When absent, invariants apply to any
     * test that exercises the matching endpoint (auto-detection).
     * Overridable per-invariant via {@link InvariantConfig#getIncludeOnly()}.
     */
    @JsonProperty("include_only")
    private List<FeatureTestMapping> includeOnly;

    public boolean hasInvariants() {
        return invariants != null && !invariants.isEmpty();
    }
}
