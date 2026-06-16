package io.antigen.core.unit.config;

import io.antigen.core.config.ConfigDirLoader;
import io.antigen.core.config.InvariantConfig;
import io.antigen.core.config.ResolvedTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the protocol's filesystem config path (Phase 3b): invariants load from
 * {@code <configDir>/simulation/invariants/*.yml} and resolve by {@code testId} — no classpath, no
 * {@link Class}. This is the decoupling that lets a foreign adapter drive the engine.
 */
class ConfigDirLoaderTest {

    private static final String FEATURE_YML = """
            name: Order Lifecycle
            invariants:
              /api/v1/orders:
                GET:
                  invariants:
                    - name: valid_status
                      field: status
                      in: [PENDING, FILLED]
            """;

    private Path writeConfigDir(Path root, String featureYml) throws IOException {
        Path invariants = root.resolve("simulation").resolve("invariants");
        Files.createDirectories(invariants);
        Files.writeString(invariants.resolve("orders.yml"), featureYml);
        return root;
    }

    @Test
    void resolvesInvariantsFromConfigDirByTestId(@TempDir Path tmp) throws IOException {
        Path configDir = writeConfigDir(tmp, FEATURE_YML);

        ConfigDirLoader loader = ConfigDirLoader.fromConfigDir(configDir.toString());
        assertThat(loader.getFeatures()).hasSize(1);

        ResolvedTestConfig config = loader.resolve("com.example.OrdersApiTest#getOrder");
        List<InvariantConfig> invariants = config.getInvariantsFor("/api/v1/orders", "GET");

        assertThat(invariants).hasSize(1);
        assertThat(invariants.get(0).getName()).isEqualTo("valid_status");
        assertThat(invariants.get(0).getIn()).containsExactly("PENDING", "FILLED");
    }

    @Test
    void perClassScopedConfigIsMergedFromConfigDir(@TempDir Path tmp) throws IOException {
        Path configDir = writeConfigDir(tmp, FEATURE_YML);
        // A <ClassName>.antigen.yml adds a method-targeted invariant on top of the feature file.
        Files.writeString(configDir.resolve("simulation").resolve("com.example.OrdersApiTest.antigen.yml"), """
                endpoints:
                  /api/v1/orders:
                    GET:
                      invariants:
                        - name: positive_quantity
                          field: quantity
                          greater_than: 0
                """);

        ResolvedTestConfig config = ConfigDirLoader.fromConfigDir(configDir.toString())
                .resolve("com.example.OrdersApiTest#getOrder");

        assertThat(config.getInvariantsFor("/api/v1/orders", "GET"))
                .extracting(InvariantConfig::getName)
                .containsExactlyInAnyOrder("valid_status", "positive_quantity");
    }

    @Test
    void missingConfigDirYieldsNoFeatures(@TempDir Path tmp) {
        ConfigDirLoader loader = ConfigDirLoader.fromConfigDir(tmp.resolve("does-not-exist").toString());
        assertThat(loader.getFeatures()).isEmpty();
        assertThat(loader.resolve("X#y").getInvariantsFor("/api/v1/orders", "GET")).isEmpty();
    }
}
