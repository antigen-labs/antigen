package io.antigen.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class GenerationConfigLoader {

    private static final String CONFIG_RELATIVE = "src/test/resources/antigen/generation/config.yml";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /**
     * Loads antigen/generation/config.yml from the given project directory.
     * Returns empty if the file doesn't exist.
     */
    public static Optional<GenerationConfig> load(Path projectPath) {
        Path configFile = projectPath.resolve(CONFIG_RELATIVE);
        if (!Files.exists(configFile)) {
            return Optional.empty();
        }
        try (InputStream is = Files.newInputStream(configFile)) {
            return Optional.of(YAML.readValue(is, GenerationConfig.class));
        } catch (IOException e) {
            System.err.println("[Antigen] Failed to parse antigen/generation/config.yml: " + e.getMessage());
            return Optional.empty();
        }
    }
}
