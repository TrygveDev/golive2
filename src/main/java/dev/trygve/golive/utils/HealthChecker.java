package dev.trygve.golive.utils;

import dev.trygve.golive.GoLive;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Health monitoring system for the GoLive plugin
 */
public class HealthChecker {
    
    private final GoLive plugin;
    private BukkitTask healthCheckTask;
    private final AtomicLong lastHealthCheck = new AtomicLong(System.currentTimeMillis());
    
    // Health metrics
    private volatile boolean databaseHealthy = true;
    private volatile boolean vaultHealthy = true;
    private volatile boolean placeholderApiHealthy = true;
    private volatile int activeActionBars = 0;
    private volatile int livePlayerCount = 0;
    
    /**
     * Create a new HealthChecker
     * 
     * @param plugin The plugin instance
     */
    public HealthChecker(@NotNull GoLive plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Start the health monitoring
     */
    public void start() {
        if (!plugin.getConfig().getBoolean("debug.enabled", false)) {
            return; // Only run in debug mode
        }
        
        // Run health check every 5 minutes
        healthCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            performHealthCheck();
        }, 20L * 60 * 5, 20L * 60 * 5); // Every 5 minutes
        
        plugin.getLogger().info("Health monitoring started");
    }
    
    /**
     * Stop the health monitoring
     */
    public void stop() {
        if (healthCheckTask != null) {
            healthCheckTask.cancel();
            healthCheckTask = null;
        }
        
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("Health monitoring stopped");
        }
    }
    
    /**
     * Perform a comprehensive health check
     */
    private void performHealthCheck() {
        try {
            lastHealthCheck.set(System.currentTimeMillis());
            
            // Check database health
            checkDatabaseHealth();
            
            // Check Vault health
            checkVaultHealth();
            
            // Check PlaceholderAPI health
            checkPlaceholderApiHealth();
            
            // Update metrics
            updateMetrics();
            
            // Log health status
            logHealthStatus();
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error during health check: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Check database health
     */
    private void checkDatabaseHealth() {
        try {
            boolean isConnected = plugin.getDatabase().isConnected();
            if (!isConnected) {
                databaseHealthy = false;
                plugin.getLogger().warning("Database health check failed: Not connected");
            } else {
                databaseHealthy = true;
            }
        } catch (Exception e) {
            databaseHealthy = false;
            plugin.getLogger().warning("Database health check failed: " + e.getMessage());
        }
    }
    
    /**
     * Check Vault health
     */
    private void checkVaultHealth() {
        try {
            vaultHealthy = plugin.getVaultHook().isVaultAvailable();
            if (!vaultHealthy) {
                plugin.getLogger().warning("Vault health check failed: Not available");
            }
        } catch (Exception e) {
            vaultHealthy = false;
            plugin.getLogger().warning("Vault health check failed: " + e.getMessage());
        }
    }
    
    /**
     * Check PlaceholderAPI health
     */
    private void checkPlaceholderApiHealth() {
        try {
            placeholderApiHealthy = plugin.getPlaceholderHook().isPlaceholderApiAvailable();
            if (!placeholderApiHealthy) {
                plugin.getLogger().warning("PlaceholderAPI health check failed: Not available");
            }
        } catch (Exception e) {
            placeholderApiHealthy = false;
            plugin.getLogger().warning("PlaceholderAPI health check failed: " + e.getMessage());
        }
    }
    
    /**
     * Update health metrics
     */
    private void updateMetrics() {
        try {
            activeActionBars = plugin.getActionBarManager().getActiveTaskCount();
            livePlayerCount = plugin.getLiveStatusManager().getLivePlayerCount();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update health metrics: " + e.getMessage());
        }
    }
    
    /**
     * Log current health status
     */
    private void logHealthStatus() {
        StringBuilder status = new StringBuilder("Health Status: ");
        status.append("DB=").append(databaseHealthy ? "OK" : "FAIL");
        status.append(", Vault=").append(vaultHealthy ? "OK" : "FAIL");
        status.append(", PAPI=").append(placeholderApiHealthy ? "OK" : "FAIL");
        status.append(", LivePlayers=").append(livePlayerCount);
        status.append(", ActionBars=").append(activeActionBars);
        
        plugin.getLogger().info(status.toString());
    }
    
    /**
     * Get overall health status
     * 
     * @return True if all critical systems are healthy
     */
    public boolean isHealthy() {
        return databaseHealthy && vaultHealthy;
    }
    
    /**
     * Get database health status
     * 
     * @return True if database is healthy
     */
    public boolean isDatabaseHealthy() {
        return databaseHealthy;
    }
    
    /**
     * Get Vault health status
     * 
     * @return True if Vault is healthy
     */
    public boolean isVaultHealthy() {
        return vaultHealthy;
    }
    
    /**
     * Get PlaceholderAPI health status
     * 
     * @return True if PlaceholderAPI is healthy
     */
    public boolean isPlaceholderApiHealthy() {
        return placeholderApiHealthy;
    }
    
    /**
     * Get the number of active action bars
     * 
     * @return Number of active action bars
     */
    public int getActiveActionBars() {
        return activeActionBars;
    }
    
    /**
     * Get the number of live players
     * 
     * @return Number of live players
     */
    public int getLivePlayerCount() {
        return livePlayerCount;
    }
    
    /**
     * Get the last health check timestamp
     * 
     * @return Last health check timestamp
     */
    public long getLastHealthCheck() {
        return lastHealthCheck.get();
    }
}
