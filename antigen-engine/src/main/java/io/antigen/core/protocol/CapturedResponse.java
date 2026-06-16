package io.antigen.core.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.antigen.core.http.Response;

import java.util.Collections;
import java.util.Map;

/**
 * Wire form of a captured HTTP response (protocol §4.2).
 *
 * <p>Implements the engine's capture contract {@link Response} so the planner consumes it
 * directly. Immutable: {@link #withBody(String)} is how the adapter would obtain a mutated copy,
 * mirroring {@code ApacheHTTPResponse}. The body is parsed to a map once at construction.
 */
public final class CapturedResponse implements Response {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int status;
    private final Map<String, Object> headers;
    private final String body;
    private final Map<String, Object> responseAsMap;

    @JsonCreator
    public CapturedResponse(@JsonProperty("status") int status,
                            @JsonProperty("headers") Map<String, Object> headers,
                            @JsonProperty("body") String body) {
        this.status = status;
        this.headers = headers != null ? headers : Collections.emptyMap();
        this.body = body;
        this.responseAsMap = parse(body);
    }

    private static Map<String, Object> parse(String body) {
        if (body == null || body.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // Non-JSON body: the planner treats an empty map as "nothing to mutate" (architecture §8).
            return Collections.emptyMap();
        }
    }

    @JsonProperty("status") @Override public int getStatusCode() { return status; }
    @Override public Map<String, Object> getHeaders() { return headers; }
    @Override public String getBody() { return body; }

    /** Responses carry no URL of their own in the protocol; the paired request holds it. */
    @JsonIgnore @Override public String getUrl() { return null; }

    @Override public void setBody(String body) {
        throw new UnsupportedOperationException("CapturedResponse is immutable; use withBody()");
    }

    @Override public Response withBody(String newBody) {
        return new CapturedResponse(status, headers, newBody);
    }

    @JsonIgnore @Override public Map<String, Object> getResponseAsMap() { return responseAsMap; }
}
