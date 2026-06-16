package io.antigen.core.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code session/start} message (protocol §4.1): the adapter announces its protocol version
 * and where to find config/spec, and identifies itself. The engine rejects a mismatched
 * {@link #protocolVersion} here, before any work.
 *
 * <p>Loading config from {@link #configDir}/{@link #specPath} on the filesystem (rather than the
 * JVM classpath) is Phase 3b — see {@code refactor/phase3.md}.
 */
public final class SessionStartRequest {

    /** Adapter identity (protocol §4.1). */
    public static final class AdapterInfo {
        private final String name;
        private final String version;

        @JsonCreator
        public AdapterInfo(@JsonProperty("name") String name,
                           @JsonProperty("version") String version) {
            this.name = name;
            this.version = version;
        }

        public String getName() { return name; }
        public String getVersion() { return version; }
    }

    private final String protocolVersion;
    private final String configDir;
    private final String specPath;
    private final AdapterInfo adapter;

    @JsonCreator
    public SessionStartRequest(@JsonProperty("protocolVersion") String protocolVersion,
                               @JsonProperty("configDir") String configDir,
                               @JsonProperty("specPath") String specPath,
                               @JsonProperty("adapter") AdapterInfo adapter) {
        this.protocolVersion = protocolVersion;
        this.configDir = configDir;
        this.specPath = specPath;
        this.adapter = adapter;
    }

    public String getProtocolVersion() { return protocolVersion; }
    public String getConfigDir() { return configDir; }
    public String getSpecPath() { return specPath; }
    public AdapterInfo getAdapter() { return adapter; }
}
