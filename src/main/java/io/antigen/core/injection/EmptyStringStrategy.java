package io.antigen.core.injection;

import io.antigen.core.config.FaultCollection;

import java.util.Map;

public class EmptyStringStrategy implements FaultStrategy {
    @Override
    public void apply(Map<String, Object> responseMap, String field) {
        Object value = responseMap.get(field);

        if (value instanceof String) {
            responseMap.put(field, "");
        }
    }

    @Override
    public FaultCollection getFaultType() {
        return FaultCollection.empty_string;
    }
}