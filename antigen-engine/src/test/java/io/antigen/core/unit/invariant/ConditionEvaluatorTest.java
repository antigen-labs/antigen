package io.antigen.core.unit.invariant;

import io.antigen.core.config.ConditionConfig;
import io.antigen.core.invariant.ConditionEvaluator;
import io.antigen.core.config.InvariantConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {

    private ConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionEvaluator();
    }

    @Test
    void testEqualsCondition() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setField("status");
        invariant.setEquals("ACTIVE");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "ACTIVE");

        ConditionEvaluator.EvaluationResult result = evaluator.evaluate(invariant, response);
        assertTrue(result.isSatisfied());
    }

    @Test
    void testEqualsConditionFailure() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setField("status");
        invariant.setEquals("ACTIVE");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "INACTIVE");

        ConditionEvaluator.EvaluationResult result = evaluator.evaluate(invariant, response);
        assertFalse(result.isSatisfied());
    }

    @Test
    void testGreaterThanCondition() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setField("quantity");
        invariant.setGreaterThan(0);

        Map<String, Object> response = new HashMap<>();
        response.put("quantity", 10);

        ConditionEvaluator.EvaluationResult result = evaluator.evaluate(invariant, response);
        assertTrue(result.isSatisfied());
    }

    @Test
    void testGreaterThanConditionFailure() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setField("quantity");
        invariant.setGreaterThan(0);

        Map<String, Object> response = new HashMap<>();
        response.put("quantity", 0);

        ConditionEvaluator.EvaluationResult result = evaluator.evaluate(invariant, response);
        assertFalse(result.isSatisfied());
    }

    @Test
    void testIsNotNullCondition() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setField("name");
        invariant.setIsNotNull(true);

        Map<String, Object> response = new HashMap<>();
        response.put("name", "Test");

        ConditionEvaluator.EvaluationResult result = evaluator.evaluate(invariant, response);
        assertTrue(result.isSatisfied());
    }

    @Test
    void testIsNotNullConditionFailure() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setField("name");
        invariant.setIsNotNull(true);

        Map<String, Object> response = new HashMap<>();
        response.put("name", null);

        ConditionEvaluator.EvaluationResult result = evaluator.evaluate(invariant, response);
        assertFalse(result.isSatisfied());
    }

    @Test
    void testInListCondition() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setField("type");
        invariant.setIn(Arrays.asList("BUY", "SELL"));

        Map<String, Object> response = new HashMap<>();
        response.put("type", "BUY");

        ConditionEvaluator.EvaluationResult result = evaluator.evaluate(invariant, response);
        assertTrue(result.isSatisfied());
    }

    @Test
    void testInListConditionFailure() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setField("type");
        invariant.setIn(Arrays.asList("BUY", "SELL"));

        Map<String, Object> response = new HashMap<>();
        response.put("type", "INVALID");

        ConditionEvaluator.EvaluationResult result = evaluator.evaluate(invariant, response);
        assertFalse(result.isSatisfied());
    }

    @Test
    void testConditionalInvariant_PreconditionMet() {
        // if status == FILLED then filled_at is_not_null
        InvariantConfig invariant = new InvariantConfig();
        invariant.setName("filled_order_has_filled_at");

        ConditionConfig ifCondition = new ConditionConfig();
        ifCondition.setField("status");
        ifCondition.setEquals("FILLED");
        invariant.setIfCondition(ifCondition);

        ConditionConfig thenCondition = new ConditionConfig();
        thenCondition.setField("filled_at");
        thenCondition.setIsNotNull(true);
        invariant.setThenCondition(thenCondition);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "FILLED");
        response.put("filled_at", "2024-01-15T10:00:00Z");

        ConditionEvaluator.EvaluationResult result = evaluator.evaluate(invariant, response);
        assertTrue(result.isSatisfied());
    }

    @Test
    void testConditionalInvariant_PreconditionNotMet() {
        // if status == FILLED then filled_at is_not_null
        // Should be skipped when status != FILLED
        InvariantConfig invariant = new InvariantConfig();
        invariant.setName("filled_order_has_filled_at");

        ConditionConfig ifCondition = new ConditionConfig();
        ifCondition.setField("status");
        ifCondition.setEquals("FILLED");
        invariant.setIfCondition(ifCondition);

        ConditionConfig thenCondition = new ConditionConfig();
        thenCondition.setField("filled_at");
        thenCondition.setIsNotNull(true);
        invariant.setThenCondition(thenCondition);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "PENDING");
        response.put("filled_at", null);  // This would fail if precondition was met

        ConditionEvaluator.EvaluationResult result = evaluator.evaluate(invariant, response);
        assertTrue(result.isSatisfied(), "Should be satisfied because precondition is not met (skipped)");
    }

    @Test
    void testFieldReference() {
        // created_at <= $.updated_at
        InvariantConfig invariant = new InvariantConfig();
        invariant.setField("created_at");
        invariant.setLessThanOrEqual("$.updated_at");

        Map<String, Object> response = new HashMap<>();
        response.put("created_at", "2024-01-01T00:00:00Z");
        response.put("updated_at", "2024-01-15T00:00:00Z");

        ConditionEvaluator.EvaluationResult result = evaluator.evaluate(invariant, response);
        assertTrue(result.isSatisfied());
    }
}
