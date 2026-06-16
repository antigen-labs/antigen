package io.antigen.core.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.antigen.core.http.Request;

import java.util.Collections;
import java.util.Map;

/**
 * Wire form of a captured HTTP request (protocol §4.2).
 *
 * <p>Implements the engine's capture contract {@link Request} directly, so a deserialized
 * {@code test/baseline} message feeds {@link io.antigen.core.plan.FaultPlanner} with no
 * intermediate conversion. This is the transport-shaped {@code Capture} type deferred from
 * Phase 1/2a (see {@code refactor/phase1.md} decision #3): it lands here, with the protocol,
 * which is where it finally has a consumer.
 */
public final class CapturedRequest implements Request {

    private final String method;
    private final String url;
    private final String body;
    private final Map<String, Object> headers;

    @JsonCreator
    public CapturedRequest(@JsonProperty("method") String method,
                           @JsonProperty("url") String url,
                           @JsonProperty("headers") Map<String, Object> headers,
                           @JsonProperty("body") String body) {
        this.method = method;
        this.url = url;
        this.body = body;
        this.headers = headers != null ? headers : Collections.emptyMap();
    }

    @Override public String getMethod() { return method; }
    @Override public String getUrl() { return url; }
    @Override public String getBody() { return body; }
    @Override public Map<String, Object> getHeaders() { return headers; }
}
