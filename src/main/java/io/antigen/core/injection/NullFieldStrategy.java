package io.antigen.core.injection;

import io.antigen.core.config.FaultCollection;

import java.util.Map;

public class NullFieldStrategy implements FaultStrategy {
    @Override
    public void apply(Map<String, Object> responseMap, String field) {
        responseMap.put(field, null);
    }

    @Override
    public FaultCollection getFaultType() {
        return FaultCollection.null_field;
    }
}
