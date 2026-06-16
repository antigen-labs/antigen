package io.antigen.core.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The {@code test/verdicts} message (protocol §4.3): the pass/fail outcome of every run the
 * adapter executed from the fault plan. The engine maps these to caught/escaped/flaky and folds
 * them into the report — verdict interpretation is the engine's job, not the adapter's.
 */
public final class VerdictsRequest {

    private final String sessionId;
    private final String testId;
    private final List<Verdict> verdicts;

    @JsonCreator
    public VerdictsRequest(@JsonProperty("sessionId") String sessionId,
                           @JsonProperty("testId") String testId,
                           @JsonProperty("verdicts") List<Verdict> verdicts) {
        this.sessionId = sessionId;
        this.testId = testId;
        this.verdicts = verdicts != null ? verdicts : List.of();
    }

    public String getSessionId() { return sessionId; }
    public String getTestId() { return testId; }
    public List<Verdict> getVerdicts() { return verdicts; }
}
