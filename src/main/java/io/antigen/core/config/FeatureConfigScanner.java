package io.antigen.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans src/test/resources/antigen/simulation/invariants/ for all *.yml files.
 *
 * Uses a multi-strategy approach to locate the directory because ClassLoader
 * directory resolution is inconsistent across JVM versions and build tools:
 *
 *   1. ClassLoader.getResource("antigen/simulation/invariants")   — standard, works on most JVMs
 *   2. ClassLoader.getResources("antigen/simulation/invariants")  — multi-classloader variant
 *   3. java.class.path system property scanning                    — reliable fallback for Gradle workers
 */
public class FeatureConfigScanner {

    private static final String INVARIANTS_RELATIVE = "antigen/simulation/invariants";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public List<FeatureConfig> scanAll() {
        List<FeatureConfig> features = new ArrayList<>();

        for (File dir : resolveDirectories()) {
            scanDirectory(dir, features);
        }

        if (!features.isEmpty()) {
            System.out.println("[Antigen] Loaded " + features.size()
                    + " invariant file(s) from antigen/simulation/invariants/");
        }

        return features;
    }

    // ── Directory resolution ──────────────────────────────────────────────────

    private Set<File> resolveDirectories() {
        Set<File> dirs = new LinkedHashSet<>();

        // Strategy 1: ClassLoader.getResource (singular)
        resolveViaGetResource(Thread.currentThread().getContextClassLoader(), dirs);
        resolveViaGetResource(FeatureConfigScanner.class.getClassLoader(), dirs);

        // Strategy 2: ClassLoader.getResources (plural — multiple classpath entries)
        resolveViaGetResources(Thread.currentThread().getContextClassLoader(), dirs);

        // Strategy 3: java.class.path system property (reliable for Gradle workers)
        resolveViaClasspath(dirs);

        return dirs;
    }

    private void resolveViaGetResource(ClassLoader cl, Set<File> dirs) {
        if (cl == null) return;
        for (String candidate : new String[]{INVARIANTS_RELATIVE, INVARIANTS_RELATIVE + "/"}) {
            URL url = cl.getResource(candidate);
            toFile(url).ifPresent(dirs::add);
        }
    }

    private void resolveViaGetResources(ClassLoader cl, Set<File> dirs) {
        if (cl == null) return;
        try {
            Enumeration<URL> urls = cl.getResources(INVARIANTS_RELATIVE);
            while (urls.hasMoreElements()) {
                toFile(urls.nextElement()).ifPresent(dirs::add);
            }
        } catch (IOException ignored) {}
    }

    private void resolveViaClasspath(Set<File> dirs) {
        String classpath = System.getProperty("java.class.path");
        if (classpath == null || classpath.isBlank()) return;

        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            File root = new File(entry.trim());
            if (!root.isDirectory()) continue;
            File candidate = new File(root, INVARIANTS_RELATIVE.replace("/", File.separator));
            if (candidate.isDirectory()) {
                dirs.add(candidate);
            }
        }
    }

    private java.util.Optional<File> toFile(URL url) {
        if (url == null || !"file".equals(url.getProtocol())) return java.util.Optional.empty();
        try {
            File f = new File(url.toURI());
            return f.isDirectory() ? java.util.Optional.of(f) : java.util.Optional.empty();
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    // ── File parsing ──────────────────────────────────────────────────────────

    private void scanDirectory(File dir, List<FeatureConfig> features) {
        File[] files = dir.listFiles(f ->
                f.isFile() && (f.getName().endsWith(".yml") || f.getName().endsWith(".yaml")));

        if (files == null) return;

        for (File file : files) {
            try (InputStream is = new FileInputStream(file)) {
                FeatureConfig config = YAML_MAPPER.readValue(is, FeatureConfig.class);
                if (config.getName() == null || config.getName().isBlank()) {
                    System.err.println("[Antigen] Skipping invariant file (missing 'name:' field): "
                            + file.getName());
                    continue;
                }
                features.add(config);
                System.out.println("[Antigen] Loaded invariants: '" + config.getName()
                        + "' (" + file.getName() + ")");
            } catch (IOException e) {
                System.err.println("[Antigen] Failed to parse invariant file '"
                        + file.getName() + "': " + e.getMessage());
            }
        }
    }
}
