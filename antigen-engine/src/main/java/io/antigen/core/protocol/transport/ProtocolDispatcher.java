package io.antigen.core.protocol.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.antigen.core.protocol.BaselineRequest;
import io.antigen.core.protocol.EngineSession;
import io.antigen.core.protocol.ProtocolException;
import io.antigen.core.protocol.SessionEndRequest;
import io.antigen.core.protocol.SessionEndResult;
import io.antigen.core.protocol.SessionStartRequest;
import io.antigen.core.protocol.SessionStartResult;
import io.antigen.core.protocol.VerdictsRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transport-agnostic message router: turns a JSON request envelope into an {@link EngineSession}
 * call and serializes the result. Both the stdio and HTTP servers share one dispatcher, so the
 * wire behavior is identical regardless of transport.
 *
 * <p>Envelope (v1): a request is {@code {"method": "...", "params": { ... }}}; a response is
 * {@code {"result": { ... }}} or {@code {"error": {"message": "..."}}}. {@code method} is one of
 * {@code session/start}, {@code test/baseline}, {@code test/verdicts}, {@code session/end}
 * (architecture §4). The dispatcher holds open sessions, keyed by the id minted at
 * {@code session/start}, so multiple sessions can coexist on one engine process.
 */
public final class ProtocolDispatcher {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, EngineSession> sessions = new ConcurrentHashMap<>();

    /** Decodes one request envelope, dispatches it, and returns the response envelope as JSON. */
    public String dispatch(String requestJson) {
        try {
            JsonNode envelope = mapper.readTree(requestJson);
            String method = envelope.path("method").asText(null);
            JsonNode params = envelope.path("params");
            Object result = handle(method, params);
            return mapper.writeValueAsString(Map.of("result", result));
        } catch (ProtocolException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Object handle(String method, JsonNode params) throws Exception {
        if (method == null) {
            throw new ProtocolException("missing 'method'");
        }
        switch (method) {
            case "session/start": {
                SessionStartRequest req = mapper.treeToValue(params, SessionStartRequest.class);
                EngineSession session = EngineSession.start(req);
                sessions.put(session.getSessionId(), session);
                return new SessionStartResult(session.getSessionId());
            }
            case "test/baseline": {
                BaselineRequest req = mapper.treeToValue(params, BaselineRequest.class);
                return session(req.getSessionId()).plan(req);
            }
            case "test/verdicts": {
                VerdictsRequest req = mapper.treeToValue(params, VerdictsRequest.class);
                session(req.getSessionId()).submitVerdicts(req);
                return Map.of("ok", true);
            }
            case "session/end": {
                SessionEndRequest req = mapper.treeToValue(params, SessionEndRequest.class);
                SessionEndResult result = session(req.getSessionId()).end();
                sessions.remove(req.getSessionId());
                return result;
            }
            default:
                throw new ProtocolException("unknown method '" + method + "'");
        }
    }

    private EngineSession session(String sessionId) {
        EngineSession session = sessions.get(sessionId);
        if (session == null) {
            throw new ProtocolException("unknown sessionId '" + sessionId + "'");
        }
        return session;
    }

    private String error(String message) {
        try {
            return mapper.writeValueAsString(Map.of("error", Map.of("message", String.valueOf(message))));
        } catch (Exception e) {
            return "{\"error\":{\"message\":\"failed to serialize error\"}}";
        }
    }
}
