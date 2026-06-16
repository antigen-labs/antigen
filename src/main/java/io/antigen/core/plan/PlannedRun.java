package io.antigen.core.plan;

import lombok.Data;

/**
 * One required simulation re-run, fully specified by the engine.
 *
 * <p>This is the executable unit of a {@link FaultPlan} and mirrors an entry of the protocol's
 * {@code runs} array (see {@code docs/knowledge/architecture.md} §4.2). The engine ships the
 * {@link #responseBody} fully-formed; the adapter only serves it — it never constructs or
 * modifies payloads (architecture §3, "strict division of knowledge").
 */
@Data
public class PlannedRun {

    /** Stable identifier within a plan, e.g. {@code "r0"}. */
    private String runId;

    private RunKind kind;

    /** Normalized endpoint pattern this run targets (report key), e.g. {@code /api/v1/orders/{id}}. */
    private String endpoint;

    /** Invariant being violated; {@code null} for {@link RunKind#CONTROL}. */
    private String invariant;

    /** Mutated field path (reporting only); {@code null} for {@link RunKind#CONTROL}. */
    private String field;

    /** Human-readable mutation description (reporting only); {@code null} for {@link RunKind#CONTROL}. */
    private String mutation;

    /** Index, within the test's captured requests, of the request whose response is replaced. */
    private int targetIndex;

    /** The exact response body to serve at {@link #targetIndex} during the re-run. */
    private String responseBody;

    public static PlannedRun invariantViolation(String runId, String endpoint, String invariant,
                                                String field, String mutation,
                                                int targetIndex, String responseBody) {
        PlannedRun run = new PlannedRun();
        run.runId = runId;
        run.kind = RunKind.INVARIANT_VIOLATION;
        run.endpoint = endpoint;
        run.invariant = invariant;
        run.field = field;
        run.mutation = mutation;
        run.targetIndex = targetIndex;
        run.responseBody = responseBody;
        return run;
    }
}
