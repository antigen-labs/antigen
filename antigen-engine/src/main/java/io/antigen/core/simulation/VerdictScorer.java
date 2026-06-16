package io.antigen.core.simulation;

import io.antigen.core.plan.PlannedNote;
import io.antigen.core.plan.PlannedRun;

/**
 * The single definition of "what a verdict means" for the score.
 *
 * <p>Folds a run's pass/fail outcome into a {@link FaultSimulationReport} exactly one way, so the
 * in-process Java adapter ({@code Runner}) and the protocol path ({@code EngineSession}) cannot
 * drift — making a detection rate mean the same thing regardless of how the test was run
 * (architecture §1). The engine owns this interpretation; adapters only report pass/fail
 * (architecture §3, "strict division of knowledge").
 *
 * <p>Mapping (architecture §4.3): for a fault run, {@code passed == true} ⇒ the fault escaped
 * (not caught); {@code passed == false} ⇒ caught. A note is always recorded as not caught.
 */
public final class VerdictScorer {

    private VerdictScorer() {}

    /**
     * Records a fault run's verdict against its invariant. Returns the result it recorded so a
     * caller can react (e.g. {@code stop_on_first_catch} bookkeeping).
     */
    public static TestLevelSimulationResults scoreFaultRun(FaultSimulationReport report, String testId,
                                                           PlannedRun run, boolean passed, String error) {
        TestLevelSimulationResults result = new TestLevelSimulationResults();
        result.setTest(testId);
        result.setCaught(!passed);
        if (!passed) {
            result.setError(error);
        }
        report.recordInvariantResult(run.getEndpoint(), run.getInvariant(), result);
        return result;
    }

    /**
     * Records a pre-determined note (baseline already violates / conditional not applicable) as a
     * not-caught result — no re-run was executed for it.
     */
    public static void scoreNote(FaultSimulationReport report, String testId, PlannedNote note) {
        TestLevelSimulationResults result = new TestLevelSimulationResults();
        result.setTest(testId);
        result.setCaught(false);
        result.setError(note.getMessage());
        report.recordInvariantResult(note.getEndpoint(), note.getInvariant(), result);
    }
}
