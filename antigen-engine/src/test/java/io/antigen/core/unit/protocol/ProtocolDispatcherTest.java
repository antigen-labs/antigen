package io.antigen.core.unit.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.antigen.core.protocol.transport.ProtocolDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives a full session through the {@link ProtocolDispatcher} as JSON envelopes — the wire layer
 * end-to-end, in-JVM (no sockets). Proves the transport produces the same plan/score the
 * {@code EngineSession} façade does (which the conformance vectors pin), and that protocol errors
 * surface as error envelopes rather than crashes.
 */
class ProtocolDispatcherTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProtocolDispatcher dispatcher = new ProtocolDispatcher();

    private JsonNode send(String method, Object params) throws IOException {
        String request = MAPPER.writeValueAsString(Map.of("method", method, "params", params));
        return MAPPER.readTree(dispatcher.dispatch(request));
    }

    private Path writeOrdersConfig(Path root) throws IOException {
        Path invariants = root.resolve("simulation").resolve("invariants");
        Files.createDirectories(invariants);
        Files.writeString(invariants.resolve("orders.yml"), """
                name: Order Lifecycle
                invariants:
                  /api/v1/orders:
                    GET:
                      invariants:
                        - name: valid_status
                          field: status
                          in: [PENDING, FILLED]
                """);
        return root;
    }

    @Test
    void fullSessionRoundTrip(@TempDir Path tmp) throws IOException {
        Path configDir = writeOrdersConfig(tmp);

        // session/start
        JsonNode start = send("session/start", Map.of(
                "protocolVersion", "1",
                "configDir", configDir.toString(),
                "adapter", Map.of("name", "test", "version", "0")));
        String sessionId = start.path("result").path("sessionId").asText();
        assertThat(sessionId).isNotBlank();

        // test/baseline -> plan with a control + one violation run
        JsonNode baseline = send("test/baseline", Map.of(
                "sessionId", sessionId,
                "testId", "com.example.OrdersApiTest#getOrder",
                "captures", List.of(Map.of(
                        "index", 0,
                        "request", Map.of("method", "GET", "url", "http://localhost:8000/api/v1/orders"),
                        "response", Map.of("status", 200,
                                "headers", Map.of("Content-Type", "application/json"),
                                "body", "{\"status\":\"PENDING\"}")))));
        JsonNode plan = baseline.path("result");
        assertThat(plan.path("control").path("kind").asText()).isEqualTo("CONTROL");
        assertThat(plan.path("runs")).hasSize(1);
        assertThat(plan.path("runs").get(0).path("invariant").asText()).isEqualTo("valid_status");

        // test/verdicts: control passes, the fault is caught
        JsonNode verdicts = send("test/verdicts", Map.of(
                "sessionId", sessionId,
                "testId", "com.example.OrdersApiTest#getOrder",
                "verdicts", List.of(
                        Map.of("runId", "control", "passed", true),
                        Map.of("runId", "r0", "passed", false, "error", "caught"))));
        assertThat(verdicts.path("result").path("ok").asBoolean()).isTrue();

        // session/end -> summary
        JsonNode end = send("session/end", Map.of("sessionId", sessionId));
        JsonNode summary = end.path("result").path("summary");
        assertThat(summary.path("faults").asInt()).isEqualTo(1);
        assertThat(summary.path("caught").asInt()).isEqualTo(1);
        assertThat(summary.path("escaped").asInt()).isEqualTo(0);
        assertThat(end.path("result").path("exitCode").asInt()).isEqualTo(0);
    }

    @Test
    void rejectsUnsupportedProtocolVersion() throws IOException {
        JsonNode response = send("session/start", Map.of("protocolVersion", "999"));
        assertThat(response.path("error").path("message").asText()).contains("protocolVersion");
    }

    @Test
    void unknownSessionIsAnError() throws IOException {
        JsonNode response = send("test/verdicts", Map.of(
                "sessionId", "nope", "testId", "t", "verdicts", List.of()));
        assertThat(response.path("error").path("message").asText()).contains("sessionId");
    }

    @Test
    void unknownMethodIsAnError() throws IOException {
        JsonNode response = send("bogus/method", Map.of());
        assertThat(response.path("error").path("message").asText()).contains("unknown method");
    }
}
