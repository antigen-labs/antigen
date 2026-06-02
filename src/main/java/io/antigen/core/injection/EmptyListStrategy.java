package io.antigen.core.injection;

import io.antigen.core.config.FaultCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EmptyListStrategy implements FaultStrategy {
    @Override
    public void apply(Map<String, Object> responseMap, String field) {
        Object value = responseMap.get(field);

        if (value instanceof List || value instanceof Object[]) {
            responseMap.put(field, new ArrayList<>());
        }
    }

    @Override
    public FaultCollection getFaultType() {
        return FaultCollection.empty_list;
    }
}