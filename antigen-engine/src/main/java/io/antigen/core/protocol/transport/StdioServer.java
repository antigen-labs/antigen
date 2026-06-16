package io.antigen.core.protocol.transport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * stdio transport (architecture §4: "stdio acceptable for CI"). Framing is JSON Lines — one
 * single-line request per input line, one single-line response per output line — which makes the
 * spawn-and-pipe model trivial for a foreign adapter and avoids any port negotiation.
 *
 * <p>The protocol channel is stdout; the engine's diagnostic logging (which scatters
 * {@code System.out.println}) must therefore be redirected to stderr before serving, or it would
 * corrupt the response stream. {@code EngineServer} does that redirect and hands this server the
 * <em>original</em> stdout.
 */
public final class StdioServer {

    private final ProtocolDispatcher dispatcher;

    public StdioServer(ProtocolDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void serve(InputStream in, OutputStream out) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String response = dispatcher.dispatch(line);
            writer.write(response);
            writer.write('\n');
            writer.flush();
        }
    }
}
