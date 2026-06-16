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

    private final List<PlannedRun> runs = new ArrayList<>();
    private final List<PlannedNote> notes = new ArrayList<>();

    public void addRun(PlannedRun run) { runs.add(run); }

    public void addNote(PlannedNote note) { notes.add(note); }

    public boolean isEmpty() { return runs.isEmpty() && notes.isEmpty(); }
}
