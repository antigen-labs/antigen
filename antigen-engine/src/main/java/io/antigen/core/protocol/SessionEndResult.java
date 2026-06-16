package io.antigen.core.protocol;

import java.util.List;

/**
 * Response to {@code session/end} (protocol §4.4): the run summary plus a process exit code.
 *
 * <p>{@code exitCode} reports protocol/infrastructure health, not a pass/fail gate on escaped
 * faults — escapes are an expected, reported outcome (a healthy suite escapes ~15%, see
 * {@code docs/knowledge/gotchas.md}). Gating policy belongs to the adapter/build, not the engine.
 */
public final class SessionEndResult {

    public static final class Summary {
        private final int faults;
        private final int caught;
        private final int escaped;
        private final List<String> flakyTests;

        public Summary(int faults, int caught, int escaped, List<String> flakyTests) {
            this.faults = faults;
            this.caught = caught;
            this.escaped = escaped;
            this.flakyTests = flakyTests;
        }

        public int getFaults() { return faults; }
        public int getCaught() { return caught; }
        public int getEscaped() { return escaped; }
        public List<String> getFlakyTests() { return flakyTests; }
    }

    private final Summary summary;
    private final int exitCode;

    public SessionEndResult(Summary summary, int exitCode) {
        this.summary = summary;
        this.exitCode = exitCode;
    }

    public Summary getSummary() { return summary; }
    public int getExitCode() { return exitCode; }
}
