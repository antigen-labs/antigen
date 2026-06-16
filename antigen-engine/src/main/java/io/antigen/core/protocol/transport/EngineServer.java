package io.antigen.core.protocol.transport;

import java.io.PrintStream;

/**
 * Entry point for the engine running as a protocol server — the executable the GraalVM native
 * build (Phase 4) targets and a foreign adapter spawns. Defaults to stdio; {@code http} selects
 * the localhost HTTP transport.
 *
 * <pre>
 *   java -cp antigen-engine.jar io.antigen.core.protocol.transport.EngineServer          # stdio
 *   java -cp antigen-engine.jar io.antigen.core.protocol.transport.EngineServer http     # HTTP
 * </pre>
 *
 * <p>The engine logs diagnostics with {@code System.out.println} all over the place; on a stdio
 * transport that would corrupt the JSON response channel. So the first thing we do is capture the
 * real stdout and redirect {@code System.out} to stderr — protocol bytes go to the captured
 * stream, every engine log goes to stderr. In HTTP mode the port banner is the one line the
 * adapter parses from stdout; everything else is on stderr too.
 */
public final class EngineServer {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "stdio";

        PrintStream realStdout = System.out;
        System.setOut(System.err); // engine diagnostics → stderr; protocol channel stays clean

        ProtocolDispatcher dispatcher = new ProtocolDispatcher();

        if ("http".equalsIgnoreCase(mode)) {
            HttpProtocolServer http = new HttpProtocolServer(dispatcher);
            int port = http.start();
            realStdout.println("ANTIGEN_PORT=" + port); // the line the adapter waits for
            realStdout.flush();
            Thread.currentThread().join(); // serve until the process is killed
        } else {
            new StdioServer(dispatcher).serve(System.in, realStdout);
        }
    }

    private EngineServer() {}
}
