package org.modularsoft.consentpvp.metrics;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * bStats metrics, shaded and relocated into this plugin's package.
 *
 * <p>Custom charts: the share of known players with PvP on (bucketed to tens of percent), duels
 * started in the last 24 hours, and whether Bedrock forms are active.
 */
public final class PluginMetrics {

    /**
     * The plugin's bStats id. Register ConsentPvP at https://bstats.org/getting-started and put the
     * id here; while it is 0, metrics are skipped.
     */
    public static final int BSTATS_PLUGIN_ID = 0;

    private Metrics metrics;

    public void start(JavaPlugin plugin, Supplier<Map<UUID, Boolean>> consent, IntSupplier duelsLastDay,
                      BooleanSupplier bedrockActive) {
        if (BSTATS_PLUGIN_ID <= 0) {
            plugin.getLogger().fine("bStats id not set; metrics are off.");
            return;
        }
        metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("consent_on_percentage", () -> bucket(consent.get())));
        metrics.addCustomChart(new SingleLineChart("duels_per_day", duelsLastDay::getAsInt));
        metrics.addCustomChart(new SimplePie("bedrock_support",
                () -> bedrockActive.getAsBoolean() ? "Active" : "Inactive"));
    }

    /** "0-9%", "10-19%" ... "100%", or "No players" when nobody has a setting yet. */
    static String bucket(Map<UUID, Boolean> consent) {
        if (consent.isEmpty()) {
            return "No players";
        }
        long on = consent.values().stream().filter(Boolean::booleanValue).count();
        int percent = (int) Math.floor(on * 100.0 / consent.size());
        if (percent >= 100) {
            return "100%";
        }
        int low = percent / 10 * 10;
        return low + "-" + (low + 9) + "%";
    }

    public void shutdown() {
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }
}
