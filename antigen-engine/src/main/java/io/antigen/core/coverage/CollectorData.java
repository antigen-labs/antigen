package io.antigen.core.coverage;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class CollectorData {
    @JsonProperty("dateCollected")
    private volatile String dateCollected;

    @JsonProperty("host")
    private volatile String host;

    @JsonProperty("paths")
    private final Map<String, Map<String, EndpointMethodCoverage>> paths;

    public CollectorData() {
        this.paths = new ConcurrentHashMap<>();
    }

    public void setPaths(Map<String, Map<String, EndpointMethodCoverage>> paths) {
        this.paths.clear();
        this.paths.putAll(paths);
    }
}
