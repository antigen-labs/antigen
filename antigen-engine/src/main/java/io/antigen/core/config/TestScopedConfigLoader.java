package io.antigen.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Loads per-test-class .antigen.yml config files from the classpath.
 *
 * Convention:
 *   File: src/test/resources/antigen/simulation/<fully.qualified.ClassName>.antigen.yml
 *   Example: src/test/resources/antigen/simulation/com.example.orders.OrderApiTest.antigen.yml
 *
 * Files are discovered lazily when the test class is first intercepted.
 * Results (including "not found") are cached by TestScopedConfigCache.
 */
public class TestScopedConfigLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * Loads a {@code <ClassName>.antigen.yml} from the filesystem (the protocol path). Returns
     * empty when the file is absent — most test classes have no scoped config.
     */
    public Optional<TestScopedConfig> loadFromFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream is = Files.newInputStream(file)) {
            TestScopedConfig config = YAML_MAPPER.readValue(is, TestScopedConfig.class);
            System.out.println("[Antigen] Loaded test-scoped config: " + file);
            return Optional.of(config);
        } catch (IOException e) {
            System.err.println("[Antigen] Failed to parse " + file + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<TestScopedConfig> load(Class<?> testClass) {
        String[] candidates = {
            "antigen/simulation/" + testClass.getName() + ".antigen.yml"
        };

        for (String resourcePath : candidates) {
            InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                is = testClass.getClassLoader().getResourceAsStream(resourcePath);
            }
            if (is == null) continue;

            try (InputStream stream = is) {
                TestScopedConfig config = YAML_MAPPER.readValue(stream, TestScopedConfig.class);
                System.out.println("[Antigen] Loaded test-scoped config: " + resourcePath);
                return Optional.of(config);
            } catch (IOException e) {
                System.err.println("[Antigen] Failed to parse " + resourcePath + ": " + e.getMessage());
                return Optional.empty();
            }
        }

        return Optional.empty();
    }
}
