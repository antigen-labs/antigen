package io.antigen.core.protocol;

import io.antigen.core.config.ConfigDirLoader;
import io.antigen.core.config.ResolvedTestConfig;
import io.antigen.core.plan.FaultPlan;
import io.antigen.core.plan.FaultPlanner;
import io.antigen.core.plan.PlannedNote;
import io.antigen.core.plan.PlannedRun;
import io.antigen.core.simulation.FaultSimulationReport;
import io.antigen.core.simulation.VerdictScorer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Transport-agnostic engine session: the in-process realization of the protocol (architecture
 * §4). One instance per {@code session/start}; it owns the per-session {@link FaultSimulationReport}
 * and the plans it issued, and implements the four operations purely (no sockets, no test
 * execution). The Phase 3b transport server is a thin shell that decodes JSON onto these methods;
 * the in-process Java adapter could call them directly.
 *
 * <p>Knowledge boundary (architecture §3): this façade plans faults and scores verdicts. It never
 * runs a test or serves a byte — that is the adapter's job. {@link #plan(BaselineRequest)} ships
 * fully-formed mutated bodies; {@link #submitVerdicts(VerdictsRequest)} turns reported pass/fail
 * into caught/escaped/flaky via the shared {@link VerdictScorer}.
 */
public final class EngineSession {

    /** Protocol version this engine speaks; rejected at {@code session/start} if mismatched. */
    public static final String PROTOCOL_VERSION = "1";

    private final String sessionId;
    private final FaultSimulationReport report = new FaultSimulationReport();
    private final FaultPlanner planner = new FaultPlanner();

    /** Per-test config resolver. May return {@code null} → planner falls back to global config. */
    private final Function<String, ResolvedTestConfig> configFor;

    /** Plans issued per test, so {@code test/verdicts} can resolve runIds back to their runs. */
    private final Map<String, FaultPlan> plansByTest = new ConcurrentHashMap<>();

    public EngineSession(String sessionId, Function<String, ResolvedTestConfig> configFor) {
        this.sessionId = sessionId;
        this.configFor = configFor;
    }

    /**
     * Handles {@code session/start} (protocol §4.1): rejects a mismatched protocol version, then
     * opens a session. When a {@code configDir} is supplied, invariants are loaded from it
     * (filesystem) and per-test config resolves by {@code testId} via {@link ConfigDirLoader};
     * otherwise the session falls back to the global config (resolver returns {@code null} →
     * {@link FaultPlanner} uses {@code SimulatorConfig} defaults).
     */
    public static EngineSession start(SessionStartRequest request) {
        if (!PROTOCOL_VERSION.equals(request.getProtocolVersion())) {
            throw new ProtocolException("unsupported protocolVersion '" + request.getProtocolVersion()
                    + "' (engine speaks '" + PROTOCOL_VERSION + "')");
        }
        Function<String, ResolvedTestConfig> configFor = (request.getConfigDir() != null)
                ? ConfigDirLoader.fromConfigDir(request.getConfigDir())::resolve
                : testId -> null;
        return new EngineSession(UUID.randomUUID().toString(), configFor);
    }

    public String getSessionId() { return sessionId; }

    /** Exposed for in-process callers and conformance assertions. */
    public FaultSimulationReport getReport() { return report; }

    /**
     * Handles {@code test/baseline} (protocol §4.2): plan the re-runs for one test and remember
     * the plan for later verdict scoring.
     */
    public FaultPlan plan(BaselineRequest request) {
        ResolvedTestConfig config = configFor != null ? configFor.apply(request.getTestId()) : null;
        FaultPlan plan = planner.plan(request.toPairs(), config);
        plansByTest.put(request.getTestId(), plan);
        return plan;
    }

    /**
     * Handles {@code test/verdicts} (protocol §4.3): fold the executed runs' outcomes into the
     * report. A failed control run flags the test flaky and excludes <em>all</em> its verdicts
     * (matching the in-process {@code Runner}). Notes are always recorded as not caught. Runs the
     * adapter chose not to execute (e.g. a {@code stop_on_first_catch} skip) simply have no
     * verdict and are left out.
     */
    public void submitVerdicts(VerdictsRequest request) {
        FaultPlan plan = plansByTest.get(request.getTestId());
        if (plan == null) {
            throw new ProtocolException("no plan for testId '" + request.getTestId()
                    + "' — send test/baseline before test/verdicts");
        }
        Map<String, Verdict> byRunId = new HashMap<>();
        for (Verdict v : request.getVerdicts()) {
            byRunId.put(v.getRunId(), v);
        }

        // Control gate: if the unmutated re-run failed, the test is flaky/state-dependent — exclude
        // everything it would have contributed.
        if (plan.getControl() != null) {
            Verdict control = byRunId.get(plan.getControl().getRunId());
            if (control != null && !control.isPassed()) {
                report.markFlaky(request.getTestId());
                return;
            }
        }

        for (PlannedNote note : plan.getNotes()) {
            VerdictScorer.scoreNote(report, request.getTestId(), note);
        }
        for (PlannedRun run : plan.getRuns()) {
            Verdict v = byRunId.get(run.getRunId());
            if (v == null) {
                continue;
            }
            VerdictScorer.scoreFaultRun(report, request.getTestId(), run, v.isPassed(), v.getError());
        }
    }

    /**
     * Handles {@code session/end} (protocol §4.4): writes the JSON report and returns the summary.
     * {@code exitCode} is 0 (healthy) — escapes are reported, not a failure gate (see
     * {@link SessionEndResult}).
     */
    public SessionEndResult end() {
        report.createJSONReport();
        FaultSimulationReport.Counts c = report.counts();
        SessionEndResult.Summary summary = new SessionEndResult.Summary(
                c.faults(), c.caught(), c.escaped(), new ArrayList<>(report.getFlakyTests()));
        return new SessionEndResult(summary, 0);
    }
}
