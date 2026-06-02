package io.antigen.core.unit.invariant;

import io.antigen.core.config.ConditionConfig;
import io.antigen.core.config.InvariantConfig;
import io.antigen.core.invariant.Mutation;
import io.antigen.core.invariant.ViolationGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ViolationGeneratorTest {

    private ViolationGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ViolationGenerator();
    }

    @Test
    void testGenerateViolationForIsNotNull() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setName("field_required");
        invariant.setField("name");
        invariant.setIsNotNull(true);

        Map<String, Object> response = new HashMap<>();
        response.put("name", "Test Value");

        List<Mutation> mutations = generator.generateViolations(invariant, response);

        assertFalse(mutations.isEmpty());
        assertTrue(mutations.stream().anyMatch(m -> m.getType() == Mutation.MutationType.SET_NULL));
    }

    @Test
    void testGenerateViolationForGreaterThan() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setName("positive_quantity");
        invariant.setField("quantity");
        invariant.setGreaterThan(0);

        Map<String, Object> response = new HashMap<>();
        response.put("quantity", 10);

        List<Mutation> mutations = generator.generateViolations(invariant, response);

        assertFalse(mutations.isEmpty());
        // Should generate mutations for boundary (0) and below (0 - 1 = -1)
        assertTrue(mutations.stream().anyMatch(m ->
                m.getType() == Mutation.MutationType.SET_VALUE &&
                        m.getValue() instanceof Number &&
                        ((Number) m.getValue()).doubleValue() <= 0));
    }

    @Test
    void testGenerateViolationForInList() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setName("valid_status");
        invariant.setField("status");
        invariant.setIn(Arrays.asList("PENDING", "FILLED", "REJECTED"));

        Map<String, Object> response = new HashMap<>();
        response.put("status", "PENDING");

        List<Mutation> mutations = generator.generateViolations(invariant, response);

        assertFalse(mutations.isEmpty());
        assertTrue(mutations.stream().anyMatch(m ->
                m.getType() == Mutation.MutationType.SET_VALUE &&
                        "INVALID_VALUE".equals(m.getValue())));
    }

    @Test
    void testGenerateViolationForConditionalInvariant() {
        // if status == FILLED then filled_at is_not_null
        InvariantConfig invariant = new InvariantConfig();
        invariant.setName("filled_order_has_timestamp");

        ConditionConfig ifCondition = new ConditionConfig();
        ifCondition.setField("status");
        ifCondition.setEquals("FILLED");
        invariant.setIfCondition(ifCondition);

        ConditionConfig thenCondition = new ConditionConfig();
        thenCondition.setField("filled_at");
        thenCondition.setIsNotNull(true);
        invariant.setThenCondition(thenCondition);

        // Response where precondition IS met
        Map<String, Object> response = new HashMap<>();
        response.put("status", "FILLED");
        response.put("filled_at", "2024-01-15T10:00:00Z");

        List<Mutation> mutations = generator.generateViolations(invariant, response);

        // Should generate mutation to set filled_at to null
        assertFalse(mutations.isEmpty());
        assertTrue(mutations.stream().anyMatch(m ->
                "filled_at".equals(m.getField()) &&
                        m.getType() == Mutation.MutationType.SET_NULL));
    }

    @Test
    void testNoViolationWhenPreconditionNotMet() {
        // if status == FILLED then filled_at is_not_null
        InvariantConfig invariant = new InvariantConfig();
        invariant.setName("filled_order_has_timestamp");

        ConditionConfig ifCondition = new ConditionConfig();
        ifCondition.setField("status");
        ifCondition.setEquals("FILLED");
        invariant.setIfCondition(ifCondition);

        ConditionConfig thenCondition = new ConditionConfig();
        thenCondition.setField("filled_at");
        thenCondition.setIsNotNull(true);
        invariant.setThenCondition(thenCondition);

        // Response where precondition is NOT met (status != FILLED)
        Map<String, Object> response = new HashMap<>();
        response.put("status", "PENDING");
        response.put("filled_at", null);

        List<Mutation> mutations = generator.generateViolations(invariant, response);

        // Should NOT generate any mutations since precondition is not met
        assertTrue(mutations.isEmpty(), "Should not generate mutations when precondition is not met");
    }

    @Test
    void testGenerateViolationForIsNotEmpty() {
        InvariantConfig invariant = new InvariantConfig();
        invariant.setName("name_required");
        invariant.setField("name");
        invariant.setIsNotEmpty(true);

        Map<String, Object> response = new HashMap<>();
        response.put("name", "Test");

        List<Mutation> mutations = generator.generateViolations(invariant, response);

        assertFalse(mutations.isEmpty());
        assertTrue(mutations.stream().anyMatch(m ->
                m.getType() == Mutation.MutationType.SET_EMPTY_STRING));
    }
}
