package io.antigen.core.simulation;

import io.antigen.core.config.ResolvedTestConfig;
import io.antigen.core.config.SimulatorConfig;
import io.antigen.core.http.Response;
import io.antigen.core.interceptor.TestContext;
import io.antigen.core.plan.FaultPlan;
import io.antigen.core.plan.FaultPlanner;
import io.antigen.core.plan.PlannedNote;
import io.antigen.core.plan.PlannedRun;
import org.aspectj.lang.ProceedingJoinPoint;

import java.util.List;

/**
 * Adapter loop (runtime/execution half — architecture §3, Phase 1).
 *
 * <p>Asks the pure {@link FaultPlanner} for a {@link FaultPlan}, then for each planned run sets
 * the test context, re-runs the test via {@code joinPoint.proceed()} (responses served from the
 * cache by {@code AspectExecutor}), and records the caught/escaped verdict. Holds the only
 * dependency on JUnit/AspectJ; the planner stays free of it.
 *
 * <p>{@code stop_on_first_catch} is applied here, live, against the report's caught-state — the
 * faithful translation of the previous inline behavior (see {@code refactor/phase1.md}).
 */
public final class Runner {

    private static final FaultSimulationReport REPORT = FaultSimulationReport.getInstance();
    private static final FaultPlanner PLANNER = new FaultPlanner();

    private Runner() {}

    public static void executeTestWithSimulatedFaults(ProceedingJoinPoint joinPoint, TestContext context) throws Throwable {
        String testName = joinPoint.getSignature().getName();

        List<TestContext.RequestResponsePair> capturedRequests = context.getCapturedRequests();
        if (capturedRequests == null || capturedRequests.isEmpty()) {
            System.err.println("[Antigen-WARN] No requests were captured. Skipping fault simulation.");
            return;
        }

        FaultPlan plan = PLANNER.plan(capturedRequests, context.getResolvedTestConfig());

        System.out.printf("%n[Antigen-Sim] === Starting simulations for test: '%s' ===%n", testName);
        System.out.printf("[Antigen-Sim] Plan: %d run(s), %d note(s)%n",
                plan.getRuns().size(), plan.getNotes().size());

        boolean stopOnFirstCatch = (context.getResolvedTestConfig() != null)
                ? context.getResolvedTestConfig().isStopOnFirstCatch()
                : SimulatorConfig.isStopOnFirstCatchEnabled();

        // Pre-determined outcomes (baseline already violates / conditional not applicable):
        // recorded as not caught, no re-run.
        for (PlannedNote note : plan.getNotes()) {
            TestLevelSimulationResults result = new TestLevelSimulationResults();
            result.setTest(testName);
            result.setCaught(false);
            result.setError(note.getMessage());
            REPORT.recordInvariantResult(note.getEndpoint(), note.getInvariant(), result);
        }

        for (PlannedRun run : plan.getRuns()) {
            if (stopOnFirstCatch && REPORT.isInvariantFaultCaught(run.getEndpoint(), run.getInvariant())) {
                continue;
            }
            executeRun(joinPoint, context, testName, run, capturedRequests, stopOnFirstCatch);
        }

        context.setCurrentSimulationIndex(-1);
        System.out.printf("[Antigen-Sim] === Completed all simulations for test: '%s' ===%n%n", testName);
    }

    private static void executeRun(ProceedingJoinPoint joinPoint, TestContext context, String testName,
                                   PlannedRun run, List<TestContext.RequestResponsePair> capturedRequests,
                                   boolean stopOnFirstCatch) {

        Response baseline = capturedRequests.get(run.getTargetIndex()).getResponse();
        context.setSimulatedResponse(baseline.withBody(run.getResponseBody()));
        context.setCurrentSimulationIndex(run.getTargetIndex());

        System.out.printf("    -> %s [%s] %s%n", run.getRunId(), run.getInvariant(), run.getMutation());

        TestLevelSimulationResults result = new TestLevelSimulationResults();
        result.setTest(testName);
        try {
            context.resetRequestCounter();
            joinPoint.proceed(); // re-run; AspectExecutor serves cached + mutated bodies
            result.setCaught(false);
            System.err.printf("    [ESCAPED] '%s' passed despite violation of '%s' on '%s'%n",
                    testName, run.getInvariant(), run.getField());
        } catch (Throwable t) {
            result.setCaught(true);
            result.setError(t.getMessage());
            System.out.printf("    [CAUGHT] '%s' failed as expected for violation of '%s' on '%s'%n",
                    testName, run.getInvariant(), run.getField());
            if (stopOnFirstCatch) {
                REPORT.markInvariantFaultCaught(run.getEndpoint(), run.getInvariant());
            }
        } finally {
            context.clearSimulation();
        }

        REPORT.recordInvariantResult(run.getEndpoint(), run.getInvariant(), result);
    }
}
