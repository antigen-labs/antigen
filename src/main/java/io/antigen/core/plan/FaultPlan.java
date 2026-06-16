package io.antigen.core.plan;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * The output of {@link FaultPlanner}: every re-run an adapter must execute for one test, plus
 * the pre-determined outcomes that need no re-run.
 *
 * <p>This is the in-process form of the protocol fault plan
 * (see {@code docs/knowledge/architecture.md} §4.2). These DTOs are the source of truth for the
 * future wire schema, so keep them transport-friendly (plain fields, Jackson-serializable).
 */
@Data
public class FaultPlan {

    /**
     * Optional control run executed before scoring: re-runs the test with the unmutated baseline.
     * A failure means the test is flaky/state-dependent and its verdicts are excluded. {@code null}
     * when the plan has no invariant activity to gate.
     */
    private PlannedRun control;

    private final List<PlannedRun> runs = new ArrayList<>();
    private final List<PlannedNote> notes = new ArrayList<>();

    public void addRun(PlannedRun run) { runs.add(run); }

    public void addNote(PlannedNote note) { notes.add(note); }

    public boolean isEmpty() { return control == null && runs.isEmpty() && notes.isEmpty(); }
}
