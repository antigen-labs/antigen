package io.antigen.core.unit.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.antigen.core.protocol.transport.HttpProtocolServer;
import io.antigen.core.protocol.transport.ProtocolDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-tests the localhost HTTP transport over a real loopback socket: the same dispatcher,
 * reached over the wire instead of in-JVM. Confirms the spawn-and-connect path a foreign adapter
 * uses actually binds, accepts a POST, and round-trips the envelope.
 */
class HttpProtocolServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpProtocolServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = new HttpProtocolServer(new ProtocolDispatcher());
        port = server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private JsonNode post(String method, Object params) throws IOException, InterruptedException {
        String body = MAPPER.writeValueAsString(Map.of("method", method, "params", params));
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/"))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return MAPPER.readTree(response.body());
    }

    @Test
    void startsSessionOverHttp() throws IOException, InterruptedException {
        JsonNode start = post("session/start", Map.of(
                "protocolVersion", "1", "adapter", Map.of("name", "http-test", "version", "0")));
        String sessionId = start.path("result").path("sessionId").asText();
        assertThat(sessionId).isNotBlank();

        // and closing it returns a (empty) summary over the same transport
        JsonNode end = post("session/end", Map.of("sessionId", sessionId));
        assertThat(end.path("result").path("summary").path("faults").asInt()).isEqualTo(0);
    }
}
