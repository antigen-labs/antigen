package io.antigen.core.injection;

import io.antigen.core.config.FaultCollection;

import java.util.Map;

public interface FaultStrategy {


    void apply(Map<String, Object> responseMap, String field);

    FaultCollection getFaultType();
}
