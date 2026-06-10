package io.antigen.core.config;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root model for per-test-class .antigen.yml configuration files.
 * Placed in: src/test/resources/antigen/simulation/<fully.qualified.ClassName>.antigen.yml
 *
 * Class-level settings apply to all tests in the class.
 * Method-level overrides live under the `tests:` key.
 */
@Data
public class TestScopedConfig {

    public String version;
    public SimulatorConfig.Settings settings;
    public Map<String, Map<String, MethodInvariantsConfig>> endpoints = new HashMap<>();
    public ExclusionsOverride exclusions;
    public Map<String, TestMethodConfig> tests = new HashMap<>();

    @Data
    public static class ExclusionsOverride {
        public List<String> endpoints;
    }
}
