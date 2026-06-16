package io.antigen.core.plan;

import lombok.Data;

/**
 * A pre-determined, non-executed outcome the engine decided at plan time — an invariant that
 * needs no re-run but must still appear in the report as "not caught".
 *
 * <p>Two cases today: the baseline response already violates the invariant
 * ({@link Reason#ORIGINAL_VIOLATION}), or a conditional invariant's precondition is unmet so no
 * mutation could be generated ({@link Reason#NOT_APPLICABLE}). Emitting these as data keeps
 * {@link FaultPlanner} pure — it never touches the report singleton; the adapter records them.
 */
@Data
public class PlannedNote {

    public enum Reason { ORIGINAL_VIOLATION, NOT_APPLICABLE }

    private String endpoint;
    private String invariant;
    private Reason reason;
    /** The error string recorded against the invariant result, fully composed by the planner. */
    private String message;

    public static PlannedNote of(String endpoint, String invariant, Reason reason, String message) {
        PlannedNote note = new PlannedNote();
        note.endpoint = endpoint;
        note.invariant = invariant;
        note.reason = reason;
        note.message = message;
        return note;
    }
}
