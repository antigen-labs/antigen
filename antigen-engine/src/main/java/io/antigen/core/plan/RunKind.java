package io.antigen.core.plan;

/**
 * The kind of a planned simulation re-run.
 *
 * <p>Mirrors the {@code kind} field of the wire protocol fault plan
 * (see {@code docs/knowledge/architecture.md} §4.2). New kinds (e.g. protocol-level
 * faults — 5xx, timeouts) are added here; shims must treat unknown kinds as unsupported
 * rather than assuming body-only mutations.
 */
public enum RunKind {
    /** Re-run with the unmutated baseline body. Used to detect flaky/state-dependent tests. */
    CONTROL,
    /** Re-run with a body mutated to violate a declared invariant. */
    INVARIANT_VIOLATION
}
