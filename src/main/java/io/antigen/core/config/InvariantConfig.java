package io.antigen.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Configuration for an invariant rule that defines constraints on response fields.
 * Invariants can be unconditional (always checked) or conditional (if/then structure).
 *
 * Examples:
 * - Unconditional: { name: "positive_quantity", field: "quantity", greater_than: 0 }
 * - Conditional: { name: "filled_order_has_timestamp", if: { field: "status", equals: "FILLED" }, then: { field: "filled_at", is_not_null: true } }
 */
@Data
public class InvariantConfig {

    /**
     * Unique name for this invariant rule (used in reports)
     */
    private String name;

    /**
     * Field to check (JSONPath notation: "field", "nested.field", "$[*].field" for arrays)
     * Used for unconditional rules.
     */
    private String field;

    /**
     * Conditional part - if this condition matches, then check the 'then' clause
     */
    @JsonProperty("if")
    private ConditionConfig ifCondition;

    /**
     * The assertion to check when 'if' condition matches (or always if no 'if' clause)
     */
    @JsonProperty("then")
    private ConditionConfig thenCondition;

    // === Operators for unconditional rules (when no if/then structure) ===

    /**
     * Field value must equal this value
     */
    private Object equals;

    /**
     * Field value must not equal this value
     */
    @JsonProperty("not_equals")
    private Object notEquals;

    /**
     * Field value must be greater than this value.
     * Can be a number or field reference ($.other_field)
     */
    @JsonProperty("greater_than")
    private Object greaterThan;

    /**
     * Field value must be greater than or equal to this value.
     * Can be a number or field reference ($.other_field)
     */
    @JsonProperty("greater_than_or_equal")
    private Object greaterThanOrEqual;

    /**
     * Field value must be less than this value.
     * Can be a number or field reference ($.other_field)
     */
    @JsonProperty("less_than")
    private Object lessThan;

    /**
     * Field value must be less than or equal to this value.
     * Can be a number or field reference ($.other_field)
     */
    @JsonProperty("less_than_or_equal")
    private Object lessThanOrEqual;

    /**
     * Field value must be one of these values
     */
    @JsonProperty("in")
    private List<Object> in;

    /**
     * Field value must not be one of these values
     */
    @JsonProperty("not_in")
    private List<Object> notIn;

    /**
     * Field must be null
     */
    @JsonProperty("is_null")
    private Boolean isNull;

    /**
     * Field must not be null
     */
    @JsonProperty("is_not_null")
    private Boolean isNotNull;

    /**
     * Field (string/list) must not be empty
     */
    @JsonProperty("is_not_empty")
    private Boolean isNotEmpty;

    /**
     * Field (string/list) must be empty
     */
    @JsonProperty("is_empty")
    private Boolean isEmpty;

    /**
     * Quantifier for array fields: all (default), any, none
     */
    private String quantifier;

    /**
     * Optional per-invariant scope. When present, restricts THIS invariant to
     * the listed tests, overriding any feature-level include_only. When absent,
     * the invariant inherits the feature-level scope (or auto-detection).
     */
    @JsonProperty("include_only")
    private List<FeatureTestMapping> includeOnly;

    /**
     * Check if this invariant has an if/then conditional structure
     */
    public boolean isConditional() {
        return ifCondition != null;
    }

    /**
     * Check if this invariant is an unconditional field constraint
     */
    public boolean isUnconditional() {
        return ifCondition == null && field != null;
    }

    /**
     * Get the condition to evaluate (either thenCondition or self as condition for unconditional rules)
     */
    public ConditionConfig getEffectiveCondition() {
        if (thenCondition != null) {
            return thenCondition;
        }
        // For unconditional rules, create a condition from this invariant's properties
        ConditionConfig condition = new ConditionConfig();
        condition.setField(this.field);
        condition.setEquals(this.equals);
        condition.setNotEquals(this.notEquals);
        condition.setGreaterThan(this.greaterThan);
        condition.setGreaterThanOrEqual(this.greaterThanOrEqual);
        condition.setLessThan(this.lessThan);
        condition.setLessThanOrEqual(this.lessThanOrEqual);
        condition.setIn(this.in);
        condition.setNotIn(this.notIn);
        condition.setIsNull(this.isNull);
        condition.setIsNotNull(this.isNotNull);
        condition.setIsNotEmpty(this.isNotEmpty);
        condition.setIsEmpty(this.isEmpty);
        return condition;
    }
}
