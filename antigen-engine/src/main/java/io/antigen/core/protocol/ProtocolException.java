package io.antigen.core.protocol;

/**
 * A protocol-level error: an unsupported {@code protocolVersion}, verdicts for an unknown test,
 * or any other malformed/out-of-order message. The transport layer (Phase 3b) maps this to an
 * error response; the in-process Java adapter never triggers it.
 */
public class ProtocolException extends RuntimeException {
    public ProtocolException(String message) {
        super(message);
    }
}
