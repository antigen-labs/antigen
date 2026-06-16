package io.antigen.core.conformance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.antigen.core.config.InvariantConfig;
import io.antigen.core.config.ResolvedTestConfig;
import io.antigen.core.plan.FaultPlan;
import io.antigen.core.protocol.BaselineRequest;
import io.antigen.core.protocol.EngineSession;
import io.antigen.core.protocol.VerdictsRequest;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Replays the versioned conformance vectors ({@code conformance/v1/}) through {@link EngineSession}
 * and asserts the engine reproduces the golden fault plan and report (architecture §6).
 *
 * <p>These goldens are the cross-language contract: any foreign adapter must produce the same
 * outputs. Comparison is order-insensitive (parsed JSON trees) so map iteration order never makes
 * the suite flaky. Regenerate after an intentional behavior change with
 * {@code -Dantigen.conformance.regenerate=true} and review the diff.
 */
class ConformanceVectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final boolean REGENERATE = Boolean.getBoolean("antigen.conformance.regenerate");

    @TestFactory
    Stream<DynamicTest> conformanceVectors() throws IOException {
        Path root = locateVectorRoot();
        try (Stream<Path> dirs = Files.list(root)) {
            List<Path> scenarios = dirs.filter(Files::isDirectory).sorted().toList();
            if (scenarios.isEmpty()) {
                throw new IllegalStateException("no conformance scenarios under " + root.toAbsolutePath());
            }
            return scenarios.stream().map(dir ->
                    DynamicTest.dynamicTest(dir.getFileName().toString(), () -> replay(dir)));
        }
    }

    private void replay(Path dir) throws IOException {
        EngineSession session = new EngineSession("conformance", testId -> resolvedConfig(dir));

        // test/baseline -> fault plan
        BaselineRequest baseline = MAPPER.readValue(dir.resolve("baseline.json").toFile(), BaselineRequest.class);
        FaultPlan plan = session.plan(baseline);
        assertMatchesGolden(dir.resolve("expected-plan.json"), plan, "plan");

        // test/verdicts -> report (only when the scenario provides verdicts)
        Path verdictsFile = dir.resolve("verdicts.json");
        if (Files.exists(verdictsFile)) {
            VerdictsRequest verdicts = MAPPER.readValue(verdictsFile.toFile(), VerdictsRequest.class);
            session.submitVerdicts(verdicts);
            assertMatchesGolden(dir.resolve("expected-report.json"), session.getReport().getReport(), "report");
        }
    }

    /** Builds the per-test config from the scenario's {@code invariants.json}. */
    private ResolvedTestConfig resolvedConfig(Path dir) {
        try {
            Map<String, List<InvariantConfig>> invariants = MAPPER.readValue(
                    dir.resolve("invariants.json").toFile(),
                    new TypeReference<Map<String, List<InvariantConfig>>>() {});
            return new ResolvedTestConfig(false, false, "all", invariants, List.<Pattern>of());
        } catch (IOException e) {
            throw new RuntimeException("failed to load invariants for " + dir, e);
        }
    }

    /**
     * Compares {@code actual} against the golden file as parsed JSON trees (order-insensitive). In
     * regenerate mode, (re)writes the golden instead of asserting.
     */
    private void assertMatchesGolden(Path goldenFile, Object actual, String label) throws IOException {
        if (REGENERATE) {
            MAPPER.writeValue(goldenFile.toFile(), actual);
            return;
        }
        JsonNode expected = MAPPER.readTree(goldenFile.toFile());
        JsonNode got = MAPPER.valueToTree(actual);
        assertEquals(expected, got, () -> "conformance " + label + " mismatch for "
                + goldenFile.getParent().getFileName() + "\nexpected: " + expected + "\nactual:   " + got);
    }

    /** Finds {@code conformance/v1} whether tests run from the module dir or the repo root. */
    private static Path locateVectorRoot() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("conformance", "v1"));
        candidates.add(Path.of("..", "conformance", "v1"));
        for (Path c : candidates) {
            if (Files.isDirectory(c)) return c;
        }
        throw new IllegalStateException("conformance/v1 not found from " + Path.of("").toAbsolutePath()
                + " (tried " + candidates + ")");
    }
}
