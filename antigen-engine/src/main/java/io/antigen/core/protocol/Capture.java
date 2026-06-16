package io.antigen.core.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One captured baseline exchange in a {@code test/baseline} message (protocol §4.2): a
 * sequence {@link #index} plus the request issued and the response it produced.
 *
 * <p>The {@code index} is the request's position within the test; replay matches an outgoing
 * request to its capture by this sequence index (architecture §4, "Request matching during
 * replay").
 */
public final class Capture {

    private final int index;
    private final CapturedRequest request;
    private final CapturedResponse response;

    @JsonCreator
    public Capture(@JsonProperty("index") int index,
                   @JsonProperty("request") CapturedRequest request,
                   @JsonProperty("response") CapturedResponse response) {
        this.index = index;
        this.request = request;
        this.response = response;
    }

    public int getIndex() { return index; }
    public CapturedRequest getRequest() { return request; }
    public CapturedResponse getResponse() { return response; }
}
