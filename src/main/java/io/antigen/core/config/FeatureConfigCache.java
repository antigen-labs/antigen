package io.antigen.core.config;

import java.util.List;

/**
 * Singleton cache for feature configurations.
 * Scans resources/antigen/simulation/invariants/ once on first access and serves
 * the loaded list to {@link ConfigResolver}.
 *
 * Scoping (which tests an invariant applies to) is resolved per-invariant in
 * ConfigResolver via the include_only cascade:
 *   1. invariant-level include_only (if present) — wins
 *   2. feature-level include_only (if present)   — fallback
 *   3. neither → auto: any test that exercises the matching endpoint
 */
public class FeatureConfigCache {

    private static final FeatureConfigCache INSTANCE = new FeatureConfigCache();

    /** All loaded feature configs — small list, iterated per resolve */
    private volatile List<FeatureConfig> allFeatures = null;

    private final Object initLock = new Object();

    private FeatureConfigCache() {}

    public static FeatureConfigCache getInstance() {
        return INSTANCE;
    }

    /** Returns every loaded feature. Per-test scoping is applied downstream. */
    public List<FeatureConfig> getAllFeatures() {
        ensureLoaded();
        return allFeatures;
    }

    private void ensureLoaded() {
        if (allFeatures != null) return;
        synchronized (initLock) {
            if (allFeatures != null) return;
            allFeatures = new FeatureConfigScanner().scanAll();
        }
    }
}
