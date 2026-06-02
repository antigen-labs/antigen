package io.antigen.core.unit.config;

import io.antigen.core.config.InvariantConfig;
import io.antigen.core.config.SimulatorConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InvariantConfig parsing and SimulatorConfig integration.
 */
public class InvariantConfigTest {

    @Test
    void testGetInvariantsForOrdersEndpoint() {
        // Get invariants for GET /api/v1/orders/{id}
        List<InvariantConfig> invariants = SimulatorConfig.getInvariantsForEndpoint(
                "/api/v1/orders/{id}", "GET");

        assertFalse(invariants.isEmpty(), "Should have invariants configured for orders endpoint");

        // Find specific invariants
        InvariantConfig positiveQuantity = invariants.stream()
                .filter(r -> "positive_quantity".equals(r.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(positiveQuantity, "Should have positive_quantity invariant");
        assertEquals("quantity", positiveQuantity.getField());
        assertEquals(0, ((Number) positiveQuantity.getGreaterThan()).intValue());
    }

    @Test
    void testConditionalInvariant() {
        List<InvariantConfig> invariants = SimulatorConfig.getInvariantsForEndpoint(
                "/api/v1/orders/{id}", "GET");

        // Find conditional invariant
        InvariantConfig filledOrderHasFilledAt = invariants.stream()
                .filter(r -> "filled_order_has_filled_at".equals(r.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(filledOrderHasFilledAt, "Should have filled_order_has_filled_at invariant");
        assertTrue(filledOrderHasFilledAt.isConditional(), "Should be conditional");
        assertNotNull(filledOrderHasFilledAt.getIfCondition());
        assertNotNull(filledOrderHasFilledAt.getThenCondition());

        // Check the if condition
        assertEquals("status", filledOrderHasFilledAt.getIfCondition().getField());
        assertEquals("FILLED", filledOrderHasFilledAt.getIfCondition().getEquals());

        // Check the then condition
        assertEquals("filled_at", filledOrderHasFilledAt.getThenCondition().getField());
        assertTrue(filledOrderHasFilledAt.getThenCondition().getIsNotNull());
    }

    @Test
    void testHasInvariants() {
        assertTrue(SimulatorConfig.hasInvariants("/api/v1/orders/{id}", "GET"),
                "Should have invariants for orders GET");

        assertFalse(SimulatorConfig.hasInvariants("/nonexistent/endpoint", "GET"),
                "Should not have invariants for nonexistent endpoint");
    }

    @Test
    void testInListInvariant() {
        List<InvariantConfig> invariants = SimulatorConfig.getInvariantsForEndpoint(
                "/api/v1/orders/{id}", "GET");

        InvariantConfig validOrderType = invariants.stream()
                .filter(r -> "valid_order_type".equals(r.getName()))
                .findFirst()
                .orElse(null);

        assertNotNull(validOrderType, "Should have valid_order_type invariant");
        assertNotNull(validOrderType.getIn());
        assertTrue(validOrderType.getIn().contains("BUY"));
        assertTrue(validOrderType.getIn().contains("SELL"));
    }
}
