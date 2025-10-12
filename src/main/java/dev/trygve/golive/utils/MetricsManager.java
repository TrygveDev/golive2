package dev.trygve.golive.utils;

import dev.trygve.golive.GoLive;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for bStats metrics
 */
public class MetricsManager {
    
    private final GoLive plugin;
    private final Metrics metrics;
    
    /**
     * Create a new MetricsManager
     * 
     * @param plugin The plugin instance
     * @param pluginId The bStats plugin ID
     */
    public MetricsManager(@NotNull GoLive plugin, int pluginId) {
        this.plugin = plugin;
        this.metrics = new Metrics(plugin, pluginId);
        
        setupCharts();
    }
    
    /**
     * Setup all metrics charts
     */
    private void setupCharts() {
        // Database type chart
        metrics.addCustomChart(new SimplePie("database_type", () -> {
            String type = plugin.getConfig().getString("database.type", "SQLITE");
            return type.toUpperCase();
        }));
        
        // Vault integration chart
        metrics.addCustomChart(new SimplePie("vault_integration", () -> {
            boolean enabled = plugin.getConfig().getBoolean("vault.enabled", true);
            return enabled ? "Enabled" : "Disabled";
        }));
        
        // GUI enabled chart
        metrics.addCustomChart(new SimplePie("gui_enabled", () -> {
            boolean enabled = plugin.getConfig().getBoolean("gui.enabled", true);
            return enabled ? "Enabled" : "Disabled";
        }));
        
        // Live players chart
        metrics.addCustomChart(new SingleLineChart("live_players", () -> {
            return plugin.getLiveStatusManager().getLivePlayerCount();
        }));
        
        // Total players chart
        metrics.addCustomChart(new SingleLineChart("total_players", () -> {
            return plugin.getServer().getOnlinePlayers().size();
        }));
        
        // Auto-removal enabled chart
        metrics.addCustomChart(new SimplePie("auto_removal_enabled", () -> {
            boolean enabled = plugin.getConfig().getBoolean("announcements.auto-removal.enabled", true);
            return enabled ? "Enabled" : "Disabled";
        }));
        
        // Clickable messages chart
        metrics.addCustomChart(new SimplePie("clickable_messages", () -> {
            boolean enabled = plugin.getConfig().getBoolean("announcements.clickable-messages", true);
            return enabled ? "Enabled" : "Disabled";
        }));
        
        // Title announcements chart
        metrics.addCustomChart(new SimplePie("title_announcements", () -> {
            boolean enabled = plugin.getConfig().getBoolean("announcements.title.enabled", true);
            return enabled ? "Enabled" : "Disabled";
        }));
        
        // Sound effects chart
        metrics.addCustomChart(new SimplePie("sound_effects", () -> {
            boolean enabled = plugin.getConfig().getBoolean("announcements.sound.enabled", true);
            return enabled ? "Enabled" : "Disabled";
        }));
    }
    
    /**
     * Get the metrics instance
     * 
     * @return The metrics instance
     */
    @NotNull
    public Metrics getMetrics() {
        return metrics;
    }
}
