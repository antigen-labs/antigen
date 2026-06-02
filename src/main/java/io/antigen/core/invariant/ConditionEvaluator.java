package io.antigen.core.invariant;

import io.antigen.core.config.ConditionConfig;
import io.antigen.core.config.InvariantConfig;
import io.antigen.core.config.SimulatorConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Evaluates invariant conditions against response data.
 * Determines if a response satisfies the configured invariant rules.
 */
public class ConditionEvaluator {

    /**
     * Result of evaluating an invariant condition.
     */
    public static class EvaluationResult {
        private final boolean satisfied;
        private final String message;
        private final Object actualValue;
        private final Object expectedValue;

        public EvaluationResult(boolean satisfied, String message, Object actualValue, Object expectedValue) {
            this.satisfied = satisfied;
            this.message = message;
            this.actualValue = actualValue;
            this.expectedValue = expectedValue;
        }

        public boolean isSatisfied() {
            return satisfied;
        }

        public String getMessage() {
            return message;
        }

        public Object getActualValue() {
            return actualValue;
        }

        public Object getExpectedValue() {
            return expectedValue;
        }

        public static EvaluationResult success() {
            return new EvaluationResult(true, null, null, null);
        }

        public static EvaluationResult success(Object actualValue) {
            return new EvaluationResult(true, null, actualValue, null);
        }

        public static EvaluationResult failure(String message, Object actualValue, Object expectedValue) {
            return new EvaluationResult(false, message, actualValue, expectedValue);
        }

        public static EvaluationResult skipped(String reason) {
            return new EvaluationResult(true, "Skipped: " + reason, null, null);
        }
    }

    /**
     * Evaluates an invariant against response data.
     *
     * @param invariant The invariant configuration to evaluate
     * @param responseMap The response data as a map
     * @return EvaluationResult indicating if the invariant is satisfied
     */
    public EvaluationResult evaluate(InvariantConfig invariant, Map<String, Object> responseMap) {
        if (invariant == null) {
            return EvaluationResult.skipped("null invariant");
        }

        // For conditional invariants (if/then), first check the precondition
        if (invariant.isConditional()) {
            EvaluationResult preconditionResult = evaluateCondition(invariant.getIfCondition(), responseMap);
            if (!preconditionResult.isSatisfied()) {
                // Precondition not met, skip the assertion (invariant doesn't apply)
                return EvaluationResult.skipped("precondition not met");
            }
            // Precondition met, evaluate the then clause
            return evaluateCondition(invariant.getThenCondition(), responseMap);
        }

        // For unconditional invariants, evaluate directly
        return evaluateCondition(invariant.getEffectiveCondition(), responseMap);
    }

    /**
     * Evaluates a condition against response data.
     */
    public EvaluationResult evaluateCondition(ConditionConfig condition, Map<String, Object> responseMap) {
        if (condition == null || condition.getField() == null) {
            return EvaluationResult.skipped("no condition or field specified");
        }

        String field = condition.getField();
        boolean isArrayField = FieldExtractor.isArrayPath(field);

        // Handle array fields with quantifier
        if (isArrayField) {
            return evaluateArrayCondition(condition, responseMap);
        }

        // Extract the field value
        Object actualValue = FieldExtractor.extractValue(responseMap, field);

        // Evaluate each operator that is set
        return evaluateOperators(condition, actualValue, responseMap);
    }

    /**
     * Evaluates a condition on array fields using the configured quantifier.
     */
    @SuppressWarnings("unchecked")
    private EvaluationResult evaluateArrayCondition(ConditionConfig condition, Map<String, Object> responseMap) {
        String field = condition.getField();
        Object extracted = FieldExtractor.extractValueFromResponse(responseMap, field);

        if (!(extracted instanceof List)) {
            return EvaluationResult.failure("Expected array for field " + field, extracted, "List");
        }

        List<Object> values = (List<Object>) extracted;
        if (values.isEmpty()) {
            return EvaluationResult.success(); // Empty array satisfies all quantifiers by vacuous truth
        }

        String quantifier = SimulatorConfig.getDefaultQuantifier();

        int satisfiedCount = 0;
        EvaluationResult lastFailure = null;

        for (Object value : values) {
            EvaluationResult result = evaluateOperators(condition, value, responseMap);
            if (result.isSatisfied()) {
                satisfiedCount++;
            } else {
                lastFailure = result;
            }
        }

        switch (quantifier.toLowerCase()) {
            case "all":
                if (satisfiedCount == values.size()) {
                    return EvaluationResult.success();
                }
                return EvaluationResult.failure(
                        "Not all elements satisfy condition (passed: " + satisfiedCount + "/" + values.size() + ")",
                        lastFailure != null ? lastFailure.getActualValue() : null,
                        lastFailure != null ? lastFailure.getExpectedValue() : null
                );

            case "any":
                if (satisfiedCount > 0) {
                    return EvaluationResult.success();
                }
                return EvaluationResult.failure(
                        "No elements satisfy condition",
                        null,
                        "at least one match"
                );

            case "none":
                if (satisfiedCount == 0) {
                    return EvaluationResult.success();
                }
                return EvaluationResult.failure(
                        "Some elements satisfy condition when none expected (" + satisfiedCount + " matched)",
                        satisfiedCount,
                        0
                );

            default:
                return EvaluationResult.failure("Unknown quantifier: " + quantifier, null, null);
        }
    }

    /**
     * Evaluates all operators in a condition against an actual value.
     */
    private EvaluationResult evaluateOperators(ConditionConfig condition, Object actualValue, Map<String, Object> responseMap) {
        // is_null check
        if (condition.getIsNull() != null && condition.getIsNull()) {
            if (actualValue != null) {
                return EvaluationResult.failure("Expected null", actualValue, null);
            }
            return EvaluationResult.success(actualValue);
        }

        // is_not_null check
        if (condition.getIsNotNull() != null && condition.getIsNotNull()) {
            if (actualValue == null) {
                return EvaluationResult.failure("Expected not null", null, "non-null value");
            }
            return EvaluationResult.success(actualValue);
        }

        // is_empty check
        if (condition.getIsEmpty() != null && condition.getIsEmpty()) {
            if (!isEmpty(actualValue)) {
                return EvaluationResult.failure("Expected empty", actualValue, "empty");
            }
            return EvaluationResult.success(actualValue);
        }

        // is_not_empty check
        if (condition.getIsNotEmpty() != null && condition.getIsNotEmpty()) {
            if (isEmpty(actualValue)) {
                return EvaluationResult.failure("Expected not empty", actualValue, "non-empty value");
            }
            return EvaluationResult.success(actualValue);
        }

        // equals check
        if (condition.getEquals() != null) {
            Object expected = resolveValue(condition.getEquals(), responseMap);
            if (!valuesEqual(actualValue, expected)) {
                return EvaluationResult.failure("Values not equal", actualValue, expected);
            }
        }

        // not_equals check
        if (condition.getNotEquals() != null) {
            Object notExpected = resolveValue(condition.getNotEquals(), responseMap);
            if (valuesEqual(actualValue, notExpected)) {
                return EvaluationResult.failure("Values should not be equal", actualValue, "not " + notExpected);
            }
        }

        // greater_than check
        if (condition.getGreaterThan() != null) {
            Object threshold = resolveValue(condition.getGreaterThan(), responseMap);
            int comparison = compareValues(actualValue, threshold);
            if (comparison <= 0) {
                return EvaluationResult.failure("Value not greater than threshold", actualValue, "> " + threshold);
            }
        }

        // greater_than_or_equal check
        if (condition.getGreaterThanOrEqual() != null) {
            Object threshold = resolveValue(condition.getGreaterThanOrEqual(), responseMap);
            int comparison = compareValues(actualValue, threshold);
            if (comparison < 0) {
                return EvaluationResult.failure("Value not >= threshold", actualValue, ">= " + threshold);
            }
        }

        // less_than check
        if (condition.getLessThan() != null) {
            Object threshold = resolveValue(condition.getLessThan(), responseMap);
            int comparison = compareValues(actualValue, threshold);
            if (comparison >= 0) {
                return EvaluationResult.failure("Value not less than threshold", actualValue, "< " + threshold);
            }
        }

        // less_than_or_equal check
        if (condition.getLessThanOrEqual() != null) {
            Object threshold = resolveValue(condition.getLessThanOrEqual(), responseMap);
            int comparison = compareValues(actualValue, threshold);
            if (comparison > 0) {
                return EvaluationResult.failure("Value not <= threshold", actualValue, "<= " + threshold);
            }
        }

        // in check
        if (condition.getIn() != null && !condition.getIn().isEmpty()) {
            boolean found = false;
            for (Object allowed : condition.getIn()) {
                if (valuesEqual(actualValue, allowed)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return EvaluationResult.failure("Value not in allowed list", actualValue, condition.getIn());
            }
        }

        // not_in check
        if (condition.getNotIn() != null && !condition.getNotIn().isEmpty()) {
            for (Object disallowed : condition.getNotIn()) {
                if (valuesEqual(actualValue, disallowed)) {
                    return EvaluationResult.failure("Value in disallowed list", actualValue, "not in " + condition.getNotIn());
                }
            }
        }

        return EvaluationResult.success(actualValue);
    }

    /**
     * Resolves a value, handling field references.
     */
    private Object resolveValue(Object value, Map<String, Object> responseMap) {
        if (FieldExtractor.isFieldReference(value)) {
            String refField = FieldExtractor.extractReferenceField((String) value);
            return FieldExtractor.extractValue(responseMap, refField);
        }
        return value;
    }

    /**
     * Checks if a value is empty (null, empty string, or empty collection).
     */
    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String) {
            return ((String) value).isEmpty();
        }
        if (value instanceof List) {
            return ((List<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        return false;
    }

    /**
     * Compares two values for equality, handling type coercion.
     */
    private boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }

        // Handle numeric comparison with type coercion
        if (isNumeric(a) && isNumeric(b)) {
            return toBigDecimal(a).compareTo(toBigDecimal(b)) == 0;
        }

        // String comparison
        return a.toString().equals(b.toString());
    }

    /**
     * Compares two values numerically or lexicographically.
     * Returns negative if a < b, 0 if equal, positive if a > b.
     */
    private int compareValues(Object a, Object b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Cannot compare null values");
        }

        // Numeric comparison
        if (isNumeric(a) && isNumeric(b)) {
            return toBigDecimal(a).compareTo(toBigDecimal(b));
        }

        // String comparison for dates and other comparable strings
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b);
        }

        // Fallback to string comparison
        return a.toString().compareTo(b.toString());
    }

    /**
     * Checks if a value is numeric.
     */
    private boolean isNumeric(Object value) {
        if (value instanceof Number) {
            return true;
        }
        if (value instanceof String) {
            try {
                new BigDecimal((String) value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Converts a value to BigDecimal for numeric comparison.
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        if (value instanceof String) {
            return new BigDecimal((String) value);
        }
        throw new IllegalArgumentException("Cannot convert to BigDecimal: " + value);
    }
}
