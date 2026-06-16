package io.antigen.core.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.antigen.core.config.InvariantConfig;
import io.antigen.core.config.ResolvedTestConfig;
import io.antigen.core.config.SimulatorConfig;
import io.antigen.core.http.Request;
import io.antigen.core.http.Response;
import io.antigen.core.interceptor.TestContext;
import io.antigen.core.invariant.ConditionEvaluator;
import io.antigen.core.invariant.Mutation;
import io.antigen.core.invariant.ViolationGenerator;
import io.antigen.core.normalizer.EndpointPatternNormalizer;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure decision half of fault simulation (architecture §3, Phase 1).
 *
 * <p>Given a test's captured request/response pairs and its resolved config, produces a
 * {@link FaultPlan}: the exact set of re-runs to execute, each carrying a fully-formed mutated
 * response body, plus pre-determined notes for invariants that need no re-run. It performs
 * <em>no</em> test execution, holds no report state, and never calls {@code joinPoint.proceed()}
 * — that is the adapter loop's job ({@code io.antigen.core.simulation.Runner}).
 *
 * <p>Scheduling concerns that depend on cross-test outcome state ({@code stop_on_first_catch})
 * are intentionally <em>not</em> applied here; the adapter applies them live. See
 * {@code refactor/phase1.md}.
 */
public class FaultPlanner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ViolationGenerator violationGenerator = new ViolationGenerator();
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

    /**
     * Builds the fault plan for a single test.
     *
     * @param capturedRequests the baseline request/response pairs captured for this test, in order
     * @param resolvedConfig   the merged per-test config; may be {@code null} (falls back to the
     *                         global {@link SimulatorConfig})
     */
    public FaultPlan plan(List<TestContext.RequestResponsePair> capturedRequests,
                          ResolvedTestConfig resolvedConfig) {
        FaultPlan plan = new FaultPlan();
        if (capturedRequests == null || capturedRequests.isEmpty()) {
            return plan;
        }

        int[] runCounter = {0};
        int firstSimulatedIndex = -1;
        String firstSimulatedBody = null;
        for (TestContext.RequestResponsePair pair : filterRequestsByStrategy(capturedRequests)) {
            int requestIndex = capturedRequests.indexOf(pair);
            Request request = pair.getRequest();
            Response response = pair.getResponse();
            if (request == null || response == null) {
                continue;
            }

            if (!SimulatorConfig.shouldSimulateResponse(
                    response.getStatusCode(), response.getResponseAsMap(), response.getBody())) {
                continue;
            }

            String endpointPath = URI.create(request.getUrl()).getPath();
            String endpoint = EndpointPatternNormalizer.normalize(endpointPath);
            String method = request.getMethod();

            int before = plan.getRuns().size() + plan.getNotes().size();
            planRequest(plan, runCounter, endpoint, method, response, requestIndex, resolvedConfig);

            // Remember the first request that contributed invariant activity — the control run
            // replays the test with this request's unmutated baseline body still in place.
            if (firstSimulatedIndex == -1 && plan.getRuns().size() + plan.getNotes().size() > before) {
                firstSimulatedIndex = requestIndex;
                firstSimulatedBody = response.getBody();
            }
        }

        // One control run per test, gating all invariant verdicts. Only when there is activity to gate.
        if (firstSimulatedIndex != -1) {
            plan.setControl(PlannedRun.control(firstSimulatedIndex, firstSimulatedBody));
        }
        return plan;
    }

    private void planRequest(FaultPlan plan, int[] runCounter, String endpoint, String method,
                             Response response, int requestIndex, ResolvedTestConfig resolvedConfig) {

        List<InvariantConfig> invariants = (resolvedConfig != null)
                ? resolvedConfig.getInvariantsFor(endpoint, method)
                : SimulatorConfig.getInvariantsForEndpoint(endpoint, method);
        if (invariants.isEmpty()) {
            return;
        }

        Map<String, Object> responseMap = response.getResponseAsMap();

        for (InvariantConfig invariant : invariants) {
            String name = invariant.getName() != null ? invariant.getName() : "unnamed_invariant";

            // Baseline must satisfy the invariant; otherwise the test passed on an already-violating
            // response — an assertion gap recorded as not caught (no re-run possible).
            ConditionEvaluator.EvaluationResult baseline = conditionEvaluator.evaluate(invariant, responseMap);
            if (!baseline.isSatisfied()) {
                plan.addNote(PlannedNote.of(endpoint, name, PlannedNote.Reason.ORIGINAL_VIOLATION,
                        "[ORIGINAL VIOLATION] Baseline response already fails this invariant: "
                                + baseline.getMessage()));
                continue;
            }

            List<Mutation> mutations = violationGenerator.generateViolations(invariant, responseMap);
            if (mutations.isEmpty()) {
                plan.addNote(PlannedNote.of(endpoint, name, PlannedNote.Reason.NOT_APPLICABLE,
                        "[NOT APPLICABLE] No mutations could be generated "
                                + "(conditional invariant with unmet precondition)"));
                continue;
            }

            for (Mutation mutation : mutations) {
                String body = mutatedBody(responseMap, mutation);
                if (body == null) {
                    continue; // serialization failed; skip this mutation
                }
                plan.addRun(PlannedRun.invariantViolation(
                        "r" + runCounter[0]++, endpoint, name,
                        mutation.getField(), mutation.getDescription(), requestIndex, body));
            }
        }
    }

    /** Applies a mutation to a copy of the baseline response map and serializes it. */
    private String mutatedBody(Map<String, Object> responseMap, Mutation mutation) {
        try {
            Map<String, Object> mutated = new HashMap<>(responseMap);
            applyMutation(mutated, mutation);
            return OBJECT_MAPPER.writeValueAsString(mutated);
        } catch (JsonProcessingException e) {
            System.err.printf("[Antigen-Plan] Failed to serialize mutation for invariant '%s': %s%n",
                    mutation.getInvariantName(), e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void applyMutation(Map<String, Object> responseMap, Mutation mutation) {
        String[] parts = mutation.getField().split("\\.");
        Map<String, Object> current = responseMap;

        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                Map<String, Object> newMap = new HashMap<>();
                current.put(parts[i], newMap);
                current = newMap;
            }
        }

        String targetField = parts[parts.length - 1];
        switch (mutation.getType()) {
            case SET_NULL -> current.put(targetField, null);
            case SET_VALUE, SET_EMPTY_STRING, SET_EMPTY_LIST -> current.put(targetField, mutation.getValue());
            case REMOVE_FIELD -> current.remove(targetField);
        }
    }

    /** Mirrors the request-selection strategy formerly in {@code Runner}. */
    private static List<TestContext.RequestResponsePair> filterRequestsByStrategy(
            List<TestContext.RequestResponsePair> capturedRequests) {

        SimulatorConfig.MultipleEndpointsStrategy strategy = SimulatorConfig.getMultipleEndpointsStrategy();
        List<TestContext.RequestResponsePair> filtered = new ArrayList<>();

        if (strategy.test_only_last_endpoint) {
            if (!capturedRequests.isEmpty()) {
                filtered.add(capturedRequests.get(capturedRequests.size() - 1));
            }
        } else {
            filtered.addAll(capturedRequests);
        }

        if (strategy.exclude_endpoints != null && !strategy.exclude_endpoints.isEmpty()) {
            List<TestContext.RequestResponsePair> afterExclusion = new ArrayList<>();
            for (TestContext.RequestResponsePair pair : filtered) {
                String endpointPattern = EndpointPatternNormalizer.normalize(
                        URI.create(pair.getRequest().getUrl()).getPath());
                boolean excluded = strategy.exclude_endpoints.stream().anyMatch(endpointPattern::matches);
                if (!excluded) afterExclusion.add(pair);
            }
            return afterExclusion;
        }

        return filtered;
    }
}
