package io.antigen.ai.phases;

public interface PhaseResult {
    boolean isSuccess();
    String getFeedback();

    default boolean failed() {
        return !isSuccess();
    }
}
