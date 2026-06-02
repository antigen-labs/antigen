package io.antigen.core.config;

import io.antigen.core.config.MetaTestConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

public class MetaTestConfigTest {
    
    @BeforeEach
    public void setUp() {
        System.clearProperty("antigen.api.key");
        System.clearProperty("antigen.project.id");
        System.clearProperty("antigen.api.url");
        MetaTestConfig.resetInstance();
    }
    
    @AfterEach 
    public void tearDown() {
        System.clearProperty("antigen.api.key");
        System.clearProperty("antigen.project.id");
        System.clearProperty("antigen.api.url");
        MetaTestConfig.resetInstance();
    }
    
    @Test
    public void testSystemPropertyConfiguration() {
        System.setProperty("antigen.api.key", "mt_proj_secret123");
        System.setProperty("antigen.project.id", "550e8400-e29b-41d4-a716-446655440000");
        System.setProperty("antigen.api.url", "http://localhost:8080");
        
        MetaTestConfig config = MetaTestConfig.getInstance();
        
        assertEquals("mt_proj_secret123", config.getApiKey());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", config.getProjectId());
        assertEquals("http://localhost:8080", config.getApiBaseUrl());
    }
    
    @Test
    public void testUrlGeneration() {
        System.setProperty("antigen.api.key", "mt_proj_secret123");
        System.setProperty("antigen.project.id", "550e8400-e29b-41d4-a716-446655440000");
        System.setProperty("antigen.api.url", "http://localhost:8080");
        
        MetaTestConfig config = MetaTestConfig.getInstance();
        
        String expectedFaultStrategiesUrl = "http://localhost:8080/api/v1/projects/550e8400-e29b-41d4-a716-446655440000/fault-strategies/contract";
        String expectedSimulationResultsUrl = "http://localhost:8080/api/v1/projects/550e8400-e29b-41d4-a716-446655440000/simulation-results";
        
        assertEquals(expectedFaultStrategiesUrl, config.getFaultStrategiesUrl());
        assertEquals(expectedSimulationResultsUrl, config.getSimulationResultsUrl());
    }
    
    @Test
    public void testDefaultApiUrl() {
        System.setProperty("antigen.api.key", "mt_proj_secret123");
        System.setProperty("antigen.project.id", "550e8400-e29b-41d4-a716-446655440000");

        MetaTestConfig config = MetaTestConfig.getInstance();
        
        assertEquals("http://localhost:8080", config.getApiBaseUrl());
    }
    
    @Test
    @Disabled
    public void testMissingApiKeyThrowsException() {
        System.setProperty("antigen.project.id", "550e8400-e29b-41d4-a716-446655440000");
        System.clearProperty("antigen.api.key");
        
        Exception exception = assertThrows(RuntimeException.class, () -> {
            MetaTestConfig.getInstance();
        });
        
        assertTrue(exception.getMessage().contains("Metatest API key not configured"),
                "Expected message about API key, but got: " + exception.getMessage());
    }
    
    @Test
    @Disabled
    public void testMissingProjectIdThrowsException() {
        System.setProperty("antigen.api.key", "mt_proj_test123");
        // Don't set project ID - explicitly clear it
        System.clearProperty("antigen.project.id");
        
        Exception exception = assertThrows(RuntimeException.class, () -> {
            MetaTestConfig.getInstance();
        });
        
        assertTrue(exception.getMessage().contains("Metatest project ID not configured"),
                "Expected message about project ID, but got: " + exception.getMessage());
    }
    
    @Test
    public void testToStringMasksApiKey() {
        System.setProperty("antigen.api.key", "mt_proj_secret123");
        System.setProperty("antigen.project.id", "550e8400-e29b-41d4-a716-446655440000");
        
        MetaTestConfig config = MetaTestConfig.getInstance();
        String configString = config.toString();
        
        assertFalse(configString.contains("mt_proj_secret123"));
        assertTrue(configString.contains("***CONFIGURED***"));
        assertTrue(configString.contains("550e8400-e29b-41d4-a716-446655440000"));
    }
}