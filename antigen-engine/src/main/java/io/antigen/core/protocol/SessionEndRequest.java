package io.antigen.core.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The {@code session/end} message (protocol §4.4): just the session to close and report on. */
public final class SessionEndRequest {

    private final String sessionId;

    @JsonCreator
    public SessionEndRequest(@JsonProperty("sessionId") String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() { return sessionId; }
}
