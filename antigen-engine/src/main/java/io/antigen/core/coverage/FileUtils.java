package io.antigen.core.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileWriter;
import java.io.IOException;

public class FileUtils {
    public static void saveToJsonFile(String path) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            java.io.File file = new java.io.File(path);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            objectMapper.writeValue(new FileWriter(file), Collector.getData());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
