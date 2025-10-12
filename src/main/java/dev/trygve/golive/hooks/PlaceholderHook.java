package dev.trygve.golive.hooks;

import dev.trygve.golive.GoLive;
import dev.trygve.golive.placeholders.GoLivePlaceholders;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * PlaceholderAPI integration
 */
public class PlaceholderHook {
    
    private final GoLive plugin;
    private GoLivePlaceholders expansion;
    private boolean placeholderApiAvailable = false;
    
    /**
     * Create a new PlaceholderHook
     * 
     * @param plugin The plugin instance
     */
    public PlaceholderHook(@NotNull GoLive plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialize PlaceholderAPI integration
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    @NotNull
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                plugin.getLogger().info("PlaceholderAPI not found. Placeholders will not work.");
                return false;
            }
            
            try {
                // PlaceholderAPI registration must be done synchronously on the main thread
                return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    expansion = new GoLivePlaceholders(plugin);
                    if (expansion.register()) {
                        placeholderApiAvailable = true;
                        plugin.getLogger().info("PlaceholderAPI integration enabled!");
                        return true;
                    } else {
                        plugin.getLogger().warning("Failed to register PlaceholderAPI expansion!");
                        return false;
                    }
                }).get();
            } catch (Exception e) {
                plugin.getLogger().severe("Error initializing PlaceholderAPI: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    /**
     * Check if PlaceholderAPI is available
     * 
     * @return True if PlaceholderAPI is available
     */
    public boolean isPlaceholderApiAvailable() {
        return placeholderApiAvailable && expansion != null;
    }
    
    /**
     * Get the expansion instance
     * 
     * @return The expansion instance, or null if not available
     */
    @Nullable
    public GoLivePlaceholders getExpansion() {
        return expansion;
    }
    
    /**
     * Unregister the expansion
     */
    public void unregister() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
            placeholderApiAvailable = false;
            plugin.getLogger().info("PlaceholderAPI expansion unregistered.");
        }
    }
    
    /**
     * Reload the PlaceholderAPI hook
     */
    public void reload() {
        if (isPlaceholderApiAvailable()) {
            unregister();
        }
        initialize();
    }
}
