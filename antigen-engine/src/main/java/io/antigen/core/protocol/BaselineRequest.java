package io.antigen.core.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.antigen.core.http.RequestResponsePair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The {@code test/baseline} message (protocol §4.2): a test's captured exchanges, sent once after
 * the baseline run. The engine answers with a {@link io.antigen.core.plan.FaultPlan}.
 */
public final class BaselineRequest {

    private final String sessionId;
    private final String testId;
    private final List<Capture> captures;

    @JsonCreator
    public BaselineRequest(@JsonProperty("sessionId") String sessionId,
                           @JsonProperty("testId") String testId,
                           @JsonProperty("captures") List<Capture> captures) {
        this.sessionId = sessionId;
        this.testId = testId;
        this.captures = captures != null ? captures : List.of();
    }

    public String getSessionId() { return sessionId; }
    public String getTestId() { return testId; }
    public List<Capture> getCaptures() { return captures; }

    /**
     * Captures as the planner's input, ordered by sequence {@link Capture#getIndex()} so list
     * position equals the index a {@code targetIndex} refers to.
     */
    public List<RequestResponsePair> toPairs() {
        List<Capture> ordered = new ArrayList<>(captures);
        ordered.sort(Comparator.comparingInt(Capture::getIndex));
        List<RequestResponsePair> pairs = new ArrayList<>(ordered.size());
        for (Capture c : ordered) {
            pairs.add(new RequestResponsePair(c.getRequest(), c.getResponse()));
        }
        return pairs;
    }
}
