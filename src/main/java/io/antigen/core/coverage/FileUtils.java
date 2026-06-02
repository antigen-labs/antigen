package io.antigen.core.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileWriter;
import java.io.IOException;

public class FileUtils {
    public static void saveToJsonFile(String path) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.writeValue(new FileWriter(path), Collector.getData());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
