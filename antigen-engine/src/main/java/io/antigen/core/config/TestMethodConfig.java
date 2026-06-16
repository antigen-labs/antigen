package io.antigen.core.config;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-test-method overrides within a .antigen.yml file.
 * Keys in the parent `tests:` map can be exact method names or glob patterns.
 *
 * Example:
 * <pre>
 * tests:
 *   testGetFilledOrder:
 *     endpoints:
 *       /api/orders/{id}:
 *         GET:
 *           invariants:
 *             - name: filled_at_set
 *               field: filled_at
 *               is_not_null: true
 *   testHealthCheck:
 *     exclude: true
 * </pre>
 */
@Data
public class TestMethodConfig {

    public boolean exclude = false;
    public SimulatorConfig.Settings settings;
    public Map<String, Map<String, MethodInvariantsConfig>> endpoints = new HashMap<>();
    public TestScopedConfig.ExclusionsOverride exclusions;
}
