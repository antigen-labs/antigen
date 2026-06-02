package io.antigen.core.injection;

import io.antigen.core.config.FaultCollection;

import java.util.Map;

public class MissingFieldStrategy implements FaultStrategy {
    @Override
    public void apply(Map<String, Object> responseMap, String field) {
        responseMap.remove(field);
    }

    @Override
    public FaultCollection getFaultType() {
        return FaultCollection.missing_field;
    }
}