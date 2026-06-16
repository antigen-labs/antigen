package io.antigen.core.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One run's outcome in a {@code test/verdicts} message (protocol §4.3).
 *
 * <p>Semantics (architecture §4.3): for fault runs, {@code passed == true} means the fault
 * <em>escaped</em> and {@code passed == false} means it was <em>caught</em> (with an optional
 * {@link #error}). For the control run, {@code passed == false} means the test is flaky.
 */
public final class Verdict {

    private final String runId;
    private final boolean passed;
    private final String error;

    @JsonCreator
    public Verdict(@JsonProperty("runId") String runId,
                   @JsonProperty("passed") boolean passed,
                   @JsonProperty("error") String error) {
        this.runId = runId;
        this.passed = passed;
        this.error = error;
    }

    public String getRunId() { return runId; }
    public boolean isPassed() { return passed; }
    public String getError() { return error; }
}
