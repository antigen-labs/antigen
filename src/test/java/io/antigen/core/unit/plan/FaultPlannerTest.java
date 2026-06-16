package io.antigen.core.unit.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.antigen.core.config.InvariantConfig;
import io.antigen.core.config.ConditionConfig;
import io.antigen.core.config.ResolvedTestConfig;
import io.antigen.core.http.ApacheHTTPResponse;
import io.antigen.core.http.Request;
import io.antigen.core.http.Response;
import io.antigen.core.interceptor.TestContext;
import io.antigen.core.plan.FaultPlan;
import io.antigen.core.plan.FaultPlanner;
import io.antigen.core.plan.PlannedNote;
import io.antigen.core.plan.PlannedRun;
import io.antigen.core.plan.RunKind;
import org.apache.http.HttpVersion;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit coverage for the pure decision half (Phase 1a). Verifies the planner turns captured
 * request/response pairs + config into the right runs and notes, without executing anything.
 */
class FaultPlannerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ENDPOINT = "/api/v1/orders"; // normalizes to itself (no dynamic segments)
    private static final String URL = "http://localhost:8000" + ENDPOINT;

    private FaultPlanner planner;

    @BeforeEach
    void setUp() {
        planner = new FaultPlanner();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static Request request(String url, String method) {
        return new Request() {
            public String getUrl() { return url; }
            public String getMethod() { return method; }
            public Map<String, Object> getHeaders() { return Map.of(); }
            public String getBody() { return null; }
        };
    }

    private static Response response(int status, String body) {
        try {
            BasicHttpResponse http = new BasicHttpResponse(
                    new BasicStatusLine(HttpVersion.HTTP_1_1, status, "OK"));
            http.setEntity(new StringEntity(body));
            http.setHeader("Content-Type", "application/json");
            return new ApacheHTTPResponse(http);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<TestContext.RequestResponsePair> captures(String method, int status, String body) {
        List<TestContext.RequestResponsePair> list = new ArrayList<>();
        list.add(new TestContext.RequestResponsePair(request(URL, method), response(status, body)));
        return list;
    }

    private static ResolvedTestConfig configWith(String method, InvariantConfig... invariants) {
        return new ResolvedTestConfig(
                false, false, "all",
                Map.of(ENDPOINT + "::" + method.toUpperCase(), List.of(invariants)),
                List.of());
    }

    private static InvariantConfig statusIn(List<Object> allowed) {
        InvariantConfig inv = new InvariantConfig();
        inv.setName("valid_status");
        inv.setField("status");
        inv.setIn(allowed);
        return inv;
    }

    // ── tests ──────────────────────────────────────────────────────────────────

    @Test
    void emptyCapturesYieldEmptyPlan() {
        FaultPlan plan = planner.plan(null, configWith("GET", statusIn(List.of("PENDING"))));
        assertTrue(plan.isEmpty());
        assertTrue(planner.plan(List.of(), configWith("GET", statusIn(List.of("PENDING")))).isEmpty());
    }

    @Test
    void satisfiedInvariantProducesFullyFormedViolationRun() throws Exception {
        FaultPlan plan = planner.plan(
                captures("GET", 200, "{\"status\":\"PENDING\",\"quantity\":10}"),
                configWith("GET", statusIn(List.of("PENDING", "FILLED"))));

        assertTrue(plan.getNotes().isEmpty(), "satisfied invariant should not produce a note");
        assertFalse(plan.getRuns().isEmpty(), "should plan at least one violation run");

        PlannedRun run = plan.getRuns().get(0);
        assertEquals(RunKind.INVARIANT_VIOLATION, run.getKind());
        assertEquals(ENDPOINT, run.getEndpoint());
        assertEquals("valid_status", run.getInvariant());
        assertEquals("status", run.getField());
        assertEquals(0, run.getTargetIndex());
        assertEquals("r0", run.getRunId());

        // Body is fully-formed: the target field is mutated to a disallowed value, others preserved.
        Map<String, Object> mutated = MAPPER.readValue(run.getResponseBody(), Map.class);
        assertEquals("INVALID_VALUE", mutated.get("status"));
        assertEquals(10, mutated.get("quantity"));
    }

    @Test
    void baselineAlreadyViolatingProducesOriginalViolationNoteNoRun() {
        FaultPlan plan = planner.plan(
                captures("GET", 200, "{\"status\":\"DELETED\"}"),
                configWith("GET", statusIn(List.of("PENDING", "FILLED"))));

        assertTrue(plan.getRuns().isEmpty(), "no run when baseline already violates");
        assertEquals(1, plan.getNotes().size());
        PlannedNote note = plan.getNotes().get(0);
        assertEquals(PlannedNote.Reason.ORIGINAL_VIOLATION, note.getReason());
        assertEquals("valid_status", note.getInvariant());
        assertEquals(ENDPOINT, note.getEndpoint());
        assertTrue(note.getMessage().contains("[ORIGINAL VIOLATION]"));
    }

    @Test
    void conditionalWithUnmetPreconditionProducesNotApplicableNote() {
        // if status == FILLED then filled_at is_not_null — baseline status is PENDING.
        InvariantConfig inv = new InvariantConfig();
        inv.setName("filled_order_has_timestamp");
        ConditionConfig ifc = new ConditionConfig();
        ifc.setField("status");
        ifc.setEquals("FILLED");
        inv.setIfCondition(ifc);
        ConditionConfig thenc = new ConditionConfig();
        thenc.setField("filled_at");
        thenc.setIsNotNull(true);
        inv.setThenCondition(thenc);

        FaultPlan plan = planner.plan(
                captures("GET", 200, "{\"status\":\"PENDING\",\"filled_at\":null}"),
                configWith("GET", inv));

        assertTrue(plan.getRuns().isEmpty());
        assertEquals(1, plan.getNotes().size());
        assertEquals(PlannedNote.Reason.NOT_APPLICABLE, plan.getNotes().get(0).getReason());
    }

    @Test
    void nonSuccessResponseIsNotSimulated() {
        FaultPlan plan = planner.plan(
                captures("GET", 500, "{\"status\":\"PENDING\"}"),
                configWith("GET", statusIn(List.of("PENDING"))));
        assertTrue(plan.isEmpty(), "non-2xx responses are skipped");
    }

    @Test
    void noInvariantsForEndpointYieldsEmptyPlan() {
        FaultPlan plan = planner.plan(
                captures("GET", 200, "{\"status\":\"PENDING\"}"),
                new ResolvedTestConfig(false, false, "all", Map.of(), List.of()));
        assertTrue(plan.isEmpty());
    }

    @Test
    void planWithActivityHasControlRunReplayingUnmutatedBaseline() {
        String body = "{\"status\":\"PENDING\",\"quantity\":10}";
        FaultPlan plan = planner.plan(
                captures("GET", 200, body),
                configWith("GET", statusIn(List.of("PENDING", "FILLED"))));

        PlannedRun control = plan.getControl();
        assertNotNull(control, "a plan with invariant activity carries a control run");
        assertEquals(RunKind.CONTROL, control.getKind());
        assertEquals(0, control.getTargetIndex());
        assertEquals(body, control.getResponseBody(), "control replays the unmutated baseline body");
        assertNull(control.getInvariant());
    }

    @Test
    void controlRunAlsoEmittedForNoteOnlyPlans() {
        // Baseline already violates -> only a note, but the test should still be gated by a control.
        FaultPlan plan = planner.plan(
                captures("GET", 200, "{\"status\":\"DELETED\"}"),
                configWith("GET", statusIn(List.of("PENDING", "FILLED"))));

        assertNotNull(plan.getControl());
        assertEquals(1, plan.getNotes().size());
    }

    @Test
    void noControlRunWhenNothingToSimulate() {
        FaultPlan plan = planner.plan(
                captures("GET", 200, "{\"status\":\"PENDING\"}"),
                new ResolvedTestConfig(false, false, "all", Map.of(), List.of()));
        assertNull(plan.getControl(), "no invariant activity -> no control run");
        assertTrue(plan.isEmpty());
    }

    @Test
    void runIdsAreSequentialAndUniqueAcrossMutations() {
        // greater_than yields two boundary mutations; combined with status -> >= 3 runs, unique ids.
        InvariantConfig qty = new InvariantConfig();
        qty.setName("positive_quantity");
        qty.setField("quantity");
        qty.setGreaterThan(0);

        FaultPlan plan = planner.plan(
                captures("GET", 200, "{\"status\":\"PENDING\",\"quantity\":10}"),
                configWith("GET", statusIn(List.of("PENDING", "FILLED")), qty));

        List<String> ids = plan.getRuns().stream().map(PlannedRun::getRunId).toList();
        assertTrue(ids.size() >= 3, "expected status + two quantity mutations");
        assertEquals(ids.size(), ids.stream().distinct().count(), "run ids must be unique");
        assertEquals("r0", ids.get(0));
    }
}
