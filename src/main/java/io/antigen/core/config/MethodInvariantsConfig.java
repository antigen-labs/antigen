package io.antigen.core.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for invariants specific to an HTTP method on an endpoint.
 *
 * Example usage in config.yml:
 * <pre>
 * endpoints:
 *   /api/v1/orders/{order_id}:
 *     GET:
 *       invariants:
 *         - name: positive_quantity
 *           field: quantity
 *           greater_than: 0
 * </pre>
 */
@Data
public class MethodInvariantsConfig {

    /**
     * List of invariant rules for this HTTP method
     */
    private List<InvariantConfig> invariants = new ArrayList<>();

    /**
     * Check if there are any invariants configured
     */
    public boolean hasInvariants() {
        return invariants != null && !invariants.isEmpty();
    }
}
