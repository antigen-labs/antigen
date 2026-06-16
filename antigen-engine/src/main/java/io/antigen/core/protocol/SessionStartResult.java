package io.antigen.core.protocol;

/** Response to {@code session/start} (protocol §4.1): the id the adapter quotes on every later message. */
public final class SessionStartResult {

    private final String sessionId;

    public SessionStartResult(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() { return sessionId; }
}
