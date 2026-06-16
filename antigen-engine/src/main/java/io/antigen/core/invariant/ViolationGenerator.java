package io.antigen.core.invariant;

import io.antigen.core.config.ConditionConfig;
import io.antigen.core.config.InvariantConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates mutations that violate configured invariant rules.
 * Used for fault simulation to test if API tests properly validate business rules.
 */
public class ViolationGenerator {

    /**
     * Generates mutations that would violate the given invariant.
     *
     * @param invariant The invariant configuration to violate
     * @param responseMap The current response data (for context and value references)
     * @return List of mutations that would violate the invariant
     */
    public List<Mutation> generateViolations(InvariantConfig invariant, Map<String, Object> responseMap) {
        List<Mutation> mutations = new ArrayList<>();

        if (invariant == null) {
            return mutations;
        }

        String invariantName = invariant.getName() != null ? invariant.getName() : "unnamed";

        if (invariant.isConditional()) {
            // For conditional invariants, generate violations for the 'then' clause
            // We also check if the precondition is met
            ConditionEvaluator evaluator = new ConditionEvaluator();
            ConditionEvaluator.EvaluationResult preconditionResult =
                    evaluator.evaluateCondition(invariant.getIfCondition(), responseMap);

            if (preconditionResult.isSatisfied()) {
                // Precondition met, generate violations for the assertion
                mutations.addAll(generateConditionViolations(
                        invariantName, invariant.getThenCondition(), responseMap));
            }
            // If precondition not met, the invariant doesn't apply, no violations to generate
        } else {
            // For unconditional invariants, generate violations directly
            mutations.addAll(generateConditionViolations(
                    invariantName, invariant.getEffectiveCondition(), responseMap));
        }

        return mutations;
    }

    /**
     * Generates mutations that violate a specific condition.
     */
    private List<Mutation> generateConditionViolations(String invariantName,
                                                        ConditionConfig condition,
                                                        Map<String, Object> responseMap) {
        List<Mutation> mutations = new ArrayList<>();

        if (condition == null || condition.getField() == null) {
            return mutations;
        }

        String field = condition.getField();

        // For array fields, extract the base field name for mutation
        if (FieldExtractor.isArrayPath(field)) {
            // For $[*].field, we need to handle array element mutations
            // For now, we'll skip array mutations as they're more complex
            return mutations;
        }

        // is_not_null -> set to null
        if (condition.getIsNotNull() != null && condition.getIsNotNull()) {
            mutations.add(Mutation.setNull(invariantName, field));
        }

        // is_null -> set to non-null value
        if (condition.getIsNull() != null && condition.getIsNull()) {
            Object currentValue = FieldExtractor.extractValue(responseMap, field);
            // If already null, we can't generate a violation that would fail
            // Generate a non-null value
            mutations.add(Mutation.setValue(invariantName, field, "non_null_value", currentValue));
        }

        // is_not_empty -> set to empty
        if (condition.getIsNotEmpty() != null && condition.getIsNotEmpty()) {
            Object currentValue = FieldExtractor.extractValue(responseMap, field);
            if (currentValue instanceof String) {
                mutations.add(Mutation.setEmptyString(invariantName, field));
            } else if (currentValue instanceof List) {
                mutations.add(Mutation.setEmptyList(invariantName, field));
            } else {
                // Default to empty string
                mutations.add(Mutation.setEmptyString(invariantName, field));
            }
        }

        // is_empty -> set to non-empty value
        if (condition.getIsEmpty() != null && condition.getIsEmpty()) {
            Object currentValue = FieldExtractor.extractValue(responseMap, field);
            mutations.add(Mutation.setValue(invariantName, field, "non_empty_value", currentValue));
        }

        // equals -> set to different value
        if (condition.getEquals() != null) {
            Object expected = resolveValue(condition.getEquals(), responseMap);
            Object violatingValue = generateDifferentValue(expected);
            mutations.add(Mutation.setValue(invariantName, field, violatingValue, expected));
        }

        // not_equals -> set to the disallowed value
        if (condition.getNotEquals() != null) {
            Object disallowed = resolveValue(condition.getNotEquals(), responseMap);
            mutations.add(Mutation.setValue(invariantName, field, disallowed,
                    FieldExtractor.extractValue(responseMap, field)));
        }

        // greater_than -> set to value <= threshold
        if (condition.getGreaterThan() != null) {
            Object threshold = resolveValue(condition.getGreaterThan(), responseMap);
            if (threshold instanceof Number) {
                double thresholdValue = ((Number) threshold).doubleValue();
                // Set to exactly threshold (boundary violation)
                mutations.add(Mutation.setValue(invariantName, field, thresholdValue,
                        FieldExtractor.extractValue(responseMap, field)));
                // Set to below threshold
                mutations.add(Mutation.setValue(invariantName, field, thresholdValue - 1,
                        FieldExtractor.extractValue(responseMap, field)));
            }
        }

        // greater_than_or_equal -> set to value < threshold
        if (condition.getGreaterThanOrEqual() != null) {
            Object threshold = resolveValue(condition.getGreaterThanOrEqual(), responseMap);
            if (threshold instanceof Number) {
                double thresholdValue = ((Number) threshold).doubleValue();
                // Set to just below threshold
                mutations.add(Mutation.setValue(invariantName, field, thresholdValue - 1,
                        FieldExtractor.extractValue(responseMap, field)));
            }
        }

        // less_than -> set to value >= threshold
        if (condition.getLessThan() != null) {
            Object threshold = resolveValue(condition.getLessThan(), responseMap);
            if (threshold instanceof Number) {
                double thresholdValue = ((Number) threshold).doubleValue();
                // Set to exactly threshold (boundary violation)
                mutations.add(Mutation.setValue(invariantName, field, thresholdValue,
                        FieldExtractor.extractValue(responseMap, field)));
                // Set to above threshold
                mutations.add(Mutation.setValue(invariantName, field, thresholdValue + 1,
                        FieldExtractor.extractValue(responseMap, field)));
            }
        }

        // less_than_or_equal -> set to value > threshold
        if (condition.getLessThanOrEqual() != null) {
            Object threshold = resolveValue(condition.getLessThanOrEqual(), responseMap);
            if (threshold instanceof Number) {
                double thresholdValue = ((Number) threshold).doubleValue();
                // Set to just above threshold
                mutations.add(Mutation.setValue(invariantName, field, thresholdValue + 1,
                        FieldExtractor.extractValue(responseMap, field)));
            } else if (threshold instanceof String) {
                // For string comparison (dates), we need to handle differently
                String thresholdStr = (String) threshold;
                // Generate a value that would be greater (lexicographically)
                mutations.add(Mutation.setValue(invariantName, field, thresholdStr + "Z",
                        FieldExtractor.extractValue(responseMap, field)));
            }
        }

        // in -> set to value not in list
        if (condition.getIn() != null && !condition.getIn().isEmpty()) {
            Object invalidValue = generateValueNotIn(condition.getIn());
            mutations.add(Mutation.setValue(invariantName, field, invalidValue,
                    FieldExtractor.extractValue(responseMap, field)));
        }

        // not_in -> set to value in disallowed list
        if (condition.getNotIn() != null && !condition.getNotIn().isEmpty()) {
            // Pick first disallowed value
            Object disallowedValue = condition.getNotIn().get(0);
            mutations.add(Mutation.setValue(invariantName, field, disallowedValue,
                    FieldExtractor.extractValue(responseMap, field)));
        }

        return mutations;
    }

    /**
     * Resolves a value that might be a field reference.
     */
    private Object resolveValue(Object value, Map<String, Object> responseMap) {
        if (FieldExtractor.isFieldReference(value)) {
            String refField = FieldExtractor.extractReferenceField((String) value);
            return FieldExtractor.extractValue(responseMap, refField);
        }
        return value;
    }

    /**
     * Generates a value different from the expected value.
     */
    private Object generateDifferentValue(Object expected) {
        if (expected == null) {
            return "non_null";
        }
        if (expected instanceof String) {
            return expected + "_INVALID";
        }
        if (expected instanceof Number) {
            return ((Number) expected).doubleValue() + 999;
        }
        if (expected instanceof Boolean) {
            return !((Boolean) expected);
        }
        return "DIFFERENT_VALUE";
    }

    /**
     * Generates a value that is not in the given list.
     */
    private Object generateValueNotIn(List<Object> allowedValues) {
        if (allowedValues.isEmpty()) {
            return "ANY_VALUE";
        }

        Object first = allowedValues.get(0);
        if (first instanceof String) {
            return "INVALID_VALUE";
        }
        if (first instanceof Number) {
            // Find max and add 1
            double max = allowedValues.stream()
                    .filter(v -> v instanceof Number)
                    .mapToDouble(v -> ((Number) v).doubleValue())
                    .max()
                    .orElse(0);
            return max + 999;
        }
        return "INVALID";
    }
}
