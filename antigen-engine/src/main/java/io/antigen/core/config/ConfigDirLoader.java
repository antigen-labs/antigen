package io.antigen.core.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Loads Antigen config from a filesystem {@code configDir} and resolves per-test config by
 * {@code testId} string — the protocol path's answer to "where do invariants come from?"
 * (architecture §4.1). It replaces the JVM-coupled discovery used in-process
 * ({@link InvariantConfigCache} classpath scan + {@link ConfigResolver#resolve(Class, Optional, String)})
 * with plain filesystem reads keyed by data, so a foreign adapter (which has neither the engine's
 * classpath nor a {@link Class}) can drive the engine.
 *
 * <p>Directory layout mirrors the classpath convention rooted at {@code configDir}:
 * <pre>
 *   &lt;configDir&gt;/simulation/invariants/*.yml          feature invariant files
 *   &lt;configDir&gt;/simulation/&lt;ClassName&gt;.antigen.yml   optional per-class scoped config
 * </pre>
 *
 * <p>Features are scanned once at construction; per-test resolution then reads the (usually
 * absent) scoped file lazily. Global {@code SimulatorConfig} settings stay at their defaults in
 * this path — every invariant arrives through the resolved config.
 */
public final class ConfigDirLoader {

    private final List<FeatureConfig> features;
    private final Path simulationDir;

    private ConfigDirLoader(List<FeatureConfig> features, Path simulationDir) {
        this.features = features;
        this.simulationDir = simulationDir;
    }

    /** Scans {@code <configDir>/simulation/invariants} for feature files. */
    public static ConfigDirLoader fromConfigDir(String configDir) {
        Path simulation = Path.of(configDir).resolve("simulation");
        Path invariants = simulation.resolve("invariants");
        List<FeatureConfig> features = Files.isDirectory(invariants)
                ? new InvariantConfigScanner().scanDirectory(invariants)
                : List.of();
        return new ConfigDirLoader(features, simulation);
    }

    public List<FeatureConfig> getFeatures() { return features; }

    /**
     * Resolves the config for a {@code testId} of the form {@code fully.qualified.ClassName#method}.
     * A missing {@code #method} is tolerated (whole-class scope); an unknown class simply yields
     * the feature-derived invariants with no class/method overrides.
     */
    public ResolvedTestConfig resolve(String testId) {
        String className = testId;
        String methodName = "";
        int hash = testId.indexOf('#');
        if (hash >= 0) {
            className = testId.substring(0, hash);
            methodName = testId.substring(hash + 1);
        }
        Optional<TestScopedConfig> classConfig = new TestScopedConfigLoader()
                .loadFromFile(simulationDir.resolve(className + ".antigen.yml"));
        return ConfigResolver.resolve(features, className, classConfig, methodName);
    }
}
