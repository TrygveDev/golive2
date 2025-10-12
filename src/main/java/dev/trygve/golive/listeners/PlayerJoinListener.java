package dev.trygve.golive.listeners;

import dev.trygve.golive.GoLive;
import dev.trygve.golive.managers.LiveStatusManager;
import dev.trygve.golive.utils.UpdateChecker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Handles player join/quit events
 */
public class PlayerJoinListener implements Listener {
    
    private final GoLive plugin;
    private final LiveStatusManager liveStatusManager;
    private final UpdateChecker updateChecker;
    
    /**
     * Create a new PlayerJoinListener
     * 
     * @param plugin The plugin instance
     * @param liveStatusManager The live status manager
     * @param updateChecker The update checker
     */
    public PlayerJoinListener(@NotNull GoLive plugin, @NotNull LiveStatusManager liveStatusManager,
                            @NotNull UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.liveStatusManager = liveStatusManager;
        this.updateChecker = updateChecker;
    }
    
    /**
     * Handle player join event
     * 
     * @param event The player join event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check for updates and notify admins
        if (player.hasPermission("golive.admin") && plugin.getConfig().getBoolean("update-checker.notify-admins", true)) {
            checkForUpdates(player);
        }
        
        // Create player in database if they don't exist
        if (player.hasPermission("golive.live")) {
            createPlayerInDatabase(player);
        }
        
        // Restore live status if player was live before
        restoreLiveStatus(player);
    }
    
    /**
     * Handle player quit event
     * 
     * @param event The player quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Save player's live status
        if (liveStatusManager.isPlayerLive(player.getUniqueId())) {
            // Update last seen timestamp
            plugin.getDatabase().updateLastSeen(player.getUniqueId());
        }
    }
    
    /**
     * Check for updates and notify player
     * 
     * @param player The player to notify
     */
    private void checkForUpdates(@NotNull Player player) {
        CompletableFuture.runAsync(() -> {
            if (updateChecker.isUpdateAvailable()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    updateChecker.notifyAdmins();
                });
            }
        });
    }
    
    /**
     * Create player in database if they don't exist
     * 
     * @param player The player
     */
    private void createPlayerInDatabase(@NotNull Player player) {
        CompletableFuture.runAsync(() -> {
            plugin.getDatabase().playerExists(player.getUniqueId()).thenAccept(exists -> {
                if (!exists) {
                    plugin.getDatabase().createPlayer(player.getUniqueId(), player.getName());
                }
            });
        });
    }
    
    /**
     * Restore live status if player was live before
     * 
     * @param player The player
     */
    private void restoreLiveStatus(@NotNull Player player) {
        CompletableFuture.runAsync(() -> {
            plugin.getDatabase().isLive(player.getUniqueId()).thenAccept(isLive -> {
                if (isLive) {
                    plugin.getDatabase().getStreamLink(player.getUniqueId()).thenAccept(streamLink -> {
                        if (streamLink != null) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                // Restore live status in memory
                                liveStatusManager.setPlayerLiveStatus(player.getUniqueId(), true, streamLink);
                            });
                        }
                    });
                }
            });
        });
    }
}
