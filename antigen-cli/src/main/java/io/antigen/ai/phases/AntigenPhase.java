package io.antigen.ai.phases;

import io.antigen.ai.model.EscapedFault;
import lombok.Value;

import java.util.List;

@Value
public class AntigenPhase implements PhaseResult {
    boolean success;
    List<EscapedFault> escapedFaults;
    double faultDetectionRate;
    int totalFaults;
    int caughtFaults;
    // Non-null only for an infrastructure error: the simulation produced no usable report
    // (missing/empty/unparseable), as opposed to a real escaped-fault result. The loop must
    // abort on this rather than feed the agent bogus "strengthen your assertions" guidance.
    String errorMessage;

    public static AntigenPhase success(double detectionRate, int total, int caught) {
        return new AntigenPhase(true, List.of(), detectionRate, total, caught, null);
    }

    public static AntigenPhase failed(List<EscapedFault> escaped, double detectionRate, int total, int caught) {
        return new AntigenPhase(false, escaped, detectionRate, total, caught, null);
    }

    public static AntigenPhase error(String message) {
        return new AntigenPhase(false, List.of(), 0.0, 0, 0, message);
    }

    public boolean isError() {
        return errorMessage != null;
    }

    public boolean hasEscapedFaults() {
        return !escapedFaults.isEmpty();
    }

    @Override
    public String getFeedback() {
        if (isError()) {
            return errorMessage;
        }
        if (success) {
            return String.format("Antigen passed - %.1f%% fault detection rate (%d/%d faults caught)",
                faultDetectionRate * 100, caughtFaults, totalFaults);
        }

        return String.format("""
            ANTIGEN FAILURE - Your tests passed but did NOT catch %d out of %d injected faults (%.1f%% detection rate).

            Some of your assertions are too weak: a response field was mutated to an invalid value
            and your test still passed. You are deliberately NOT told which fields or faults escaped --
            that information is withheld so your assertions verify the API's real contract derived from
            the specification, not a leaked answer key. Do not look for, open, or read the Antigen
            report or anything under build/ -- it is intentionally not part of your input.

            Strengthen the assertions across ALL of your tests, deriving every constraint from the API
            specification:
            - Assert every field in every response body, not just the status code.
            - For each field, assert it is present, non-null, and of the correct JSON type.
            - Assert the value constraints the spec implies: allowed enum values, numeric ranges,
              non-empty strings and arrays, and required formats.
            - Validate nested objects and array elements, not only top-level fields.

            Read the API specification and your existing test files, then rewrite the assertions so
            each response is fully validated.
            """,
            escapedFaults.size(),
            totalFaults,
            faultDetectionRate * 100);
    }
}
