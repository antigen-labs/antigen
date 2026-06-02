package io.antigen.core.config;

import java.util.List;


public interface ConfigurationSource {

    List<FaultCollection> getEnabledFaults();


    boolean isEndpointExcluded(String endpoint);


    boolean isTestExcluded(String testName);


    String getSourceName();

    /**
     * Returns the full configuration object.
     * @return The SimulatorConfig instance
     */
    SimulatorConfig getConfig();
}
