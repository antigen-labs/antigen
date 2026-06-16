package io.antigen.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Configuration for a condition used in relation rules.
 * Can be used in both 'if' (precondition) and 'then' (assertion) clauses.
 *
 * Field references:
 * - Simple: "status", "quantity"
 * - Nested: "user.name", "order.items"
 * - JSONPath reference: "$.field_name" (for comparing to another field's value)
 * - Array fields: "$[*].field" (applies to all array elements)
 */
@Data
public class ConditionConfig {

    /**
     * Field to check (JSONPath notation supported)
     */
    private String field;

    /**
     * Field value must equal this value.
     * Can be a literal or JSONPath reference ($.other_field)
     */
    private Object equals;

    /**
     * Field value must not equal this value
     */
    @JsonProperty("not_equals")
    private Object notEquals;

    /**
     * Field value must be greater than this number.
     * Can be a literal or JSONPath reference ($.other_field)
     */
    @JsonProperty("greater_than")
    private Object greaterThan;

    /**
     * Field value must be greater than or equal to this number.
     * Can be a literal or JSONPath reference ($.other_field)
     */
    @JsonProperty("greater_than_or_equal")
    private Object greaterThanOrEqual;

    /**
     * Field value must be less than this number.
     * Can be a literal or JSONPath reference ($.other_field)
     */
    @JsonProperty("less_than")
    private Object lessThan;

    /**
     * Field value must be less than or equal to this number.
     * Can be a literal or JSONPath reference ($.other_field)
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
     * Check if this condition has any operator defined
     */
    public boolean hasOperator() {
        return equals != null ||
               notEquals != null ||
               greaterThan != null ||
               greaterThanOrEqual != null ||
               lessThan != null ||
               lessThanOrEqual != null ||
               in != null ||
               notIn != null ||
               isNull != null ||
               isNotNull != null ||
               isNotEmpty != null ||
               isEmpty != null;
    }

    /**
     * Check if a value is a JSONPath reference (starts with $.)
     */
    public static boolean isFieldReference(Object value) {
        return value instanceof String && ((String) value).startsWith("$.");
    }

    /**
     * Extract field name from JSONPath reference ($.field_name -> field_name)
     */
    public static String extractFieldFromReference(String reference) {
        if (reference.startsWith("$.")) {
            return reference.substring(2);
        }
        return reference;
    }
}
