package dev.trygve.golive.managers;

import dev.trygve.golive.GoLive;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages action bar display for live players
 */
public class ActionBarManager {
    
    private final GoLive plugin;
    private LiveStatusManager liveStatusManager;
    private final MessageManager messageManager;
    private final MiniMessage miniMessage;
    
    // Track players who should see action bar
    private final Map<UUID, BukkitTask> actionBarTasks;
    
    /**
     * Create a new ActionBarManager
     * 
     * @param plugin The plugin instance
     * @param messageManager The message manager
     */
    public ActionBarManager(@NotNull GoLive plugin, @NotNull MessageManager messageManager) {
        this.plugin = plugin;
        this.liveStatusManager = null; // Will be set later
        this.messageManager = messageManager;
        this.miniMessage = MiniMessage.miniMessage();
        this.actionBarTasks = new ConcurrentHashMap<>();
    }
    
    /**
     * Set the live status manager (called after initialization)
     * 
     * @param liveStatusManager The live status manager
     */
    public void setLiveStatusManager(@NotNull LiveStatusManager liveStatusManager) {
        this.liveStatusManager = liveStatusManager;
    }
    
    /**
     * Initialize the ActionBarManager
     * 
     * @return True if successful
     */
    public boolean initialize() {
        if (!plugin.getConfig().getBoolean("action-bar.enabled", true)) {
            if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                plugin.getLogger().info("ActionBarManager disabled in config.");
            }
            return true;
        }
        
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("ActionBarManager initialized successfully.");
        }
        return true;
    }
    
    /**
     * Start showing action bar for a live player
     * 
     * @param player The player to show action bar for
     */
    public void startActionBar(@NotNull Player player) {
        if (!plugin.getConfig().getBoolean("action-bar.enabled", true)) {
            return;
        }
        
        UUID uuid = player.getUniqueId();
        
        // Stop existing action bar if running
        stopActionBar(uuid);
        
        // Get update interval from config
        long interval = plugin.getConfig().getLong("action-bar.update-interval", 20L);
        
        // Start new action bar task
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Get fresh player reference to avoid stale references
            Player currentPlayer = Bukkit.getPlayer(uuid);
            if (currentPlayer == null || !currentPlayer.isOnline()) {
                stopActionBar(uuid);
                return;
            }
            
            if (!liveStatusManager.isPlayerLive(uuid)) {
                stopActionBar(uuid);
                return;
            }
            
            // Send action bar message on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player mainThreadPlayer = Bukkit.getPlayer(uuid);
                if (mainThreadPlayer != null && mainThreadPlayer.isOnline()) {
                    sendActionBar(mainThreadPlayer);
                }
            });
        }, 0L, interval);
        
        actionBarTasks.put(uuid, task);
        
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("Started action bar for player: " + player.getName());
        }
    }
    
    /**
     * Stop showing action bar for a player
     * 
     * @param uuid The player's UUID
     */
    public void stopActionBar(@NotNull UUID uuid) {
        BukkitTask task = actionBarTasks.remove(uuid);
        if (task != null) {
            task.cancel();
            
            if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    plugin.getLogger().info("Stopped action bar for player: " + player.getName());
                }
            }
        }
    }
    
    /**
     * Send action bar message to a player
     * 
     * @param player The player to send the message to
     */
    private void sendActionBar(@NotNull Player player) {
        try {
            // Get message from messages.yml
            String message = messageManager.getMessage("live.action-bar");
            
            // Parse MiniMessage and send as action bar
            Component component = miniMessage.deserialize(message);
            player.sendActionBar(component);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send action bar to " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Check if action bar is enabled
     * 
     * @return True if action bar is enabled
     */
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("action-bar.enabled", true);
    }
    
    /**
     * Get the number of active action bar tasks
     * 
     * @return Number of active action bar tasks
     */
    public int getActiveTaskCount() {
        return actionBarTasks.size();
    }
    
    /**
     * Stop all action bar tasks
     */
    public void stopAllActionBars() {
        for (BukkitTask task : actionBarTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        actionBarTasks.clear();
        
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("Stopped all action bar tasks.");
        }
    }
    
    /**
     * Clean up stale tasks for offline players
     */
    public void cleanupStaleTasks() {
        actionBarTasks.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            BukkitTask task = entry.getValue();
            
            // Remove if task is cancelled or player is offline
            if (task == null || task.isCancelled() || Bukkit.getPlayer(uuid) == null) {
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
                return true;
            }
            return false;
        });
    }
    
    /**
     * Shutdown the ActionBarManager
     */
    public void shutdown() {
        stopAllActionBars();
        
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("ActionBarManager shutdown complete.");
        }
    }
}
