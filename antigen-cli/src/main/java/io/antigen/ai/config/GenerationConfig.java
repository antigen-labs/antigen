package io.antigen.ai.config;

import java.util.ArrayList;
import java.util.List;

public class GenerationConfig {

    public String spec;
    public String output_dir;
    public Integer max_retries;
    public Timeouts timeouts;
    public List<String> requirements = new ArrayList<>();

    public static class Timeouts {
        public Integer build;
        public Integer test;
        public Integer antigen;
        public Integer llm;
    }
}
