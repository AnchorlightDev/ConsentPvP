package org.modularsoft.consentpvp.metrics;

import java.util.Map;
import java.util.UUID;

/** Exposes the package-private chart bucketing to tests in another package. */
public final class PluginMetricsAccess {

    private PluginMetricsAccess() {
    }

    public static String bucket(Map<UUID, Boolean> consent) {
        return PluginMetrics.bucket(consent);
    }
}
