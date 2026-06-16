package io.antigen.core.protocol.transport;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * localhost HTTP transport (architecture §4). Binds to an ephemeral port on the loopback
 * interface; the adapter spawns the engine, reads the printed port, and POSTs request envelopes to
 * {@code /}. One request envelope per POST body, the response envelope as the body — the same
 * envelope {@link ProtocolDispatcher} speaks over stdio.
 *
 * <p>Uses the JDK's built-in {@code com.sun.net.httpserver} — no third-party HTTP dependency, which
 * keeps the engine GraalVM-native-buildable (Phase 4) and the {@code EngineLayerTest} purity guard
 * green.
 */
public final class HttpProtocolServer {

    private final ProtocolDispatcher dispatcher;
    private HttpServer server;

    public HttpProtocolServer(ProtocolDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /** Binds and starts serving; returns the bound loopback port. */
    public int start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] responseBody = dispatcher.dispatch(requestBody).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseBody.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
