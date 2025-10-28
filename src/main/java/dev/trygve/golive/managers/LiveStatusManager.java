package dev.trygve.golive.managers;

import dev.trygve.golive.GoLive;
import dev.trygve.golive.database.DatabaseManager;
import dev.trygve.golive.hooks.VaultHook;
import dev.trygve.golive.utils.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages live player status and synchronization
 */
public class LiveStatusManager {
    
    private final GoLive plugin;
    private final DatabaseManager database;
    private final VaultHook vaultHook;
    private final MessageManager messageManager;
    private final ActionBarManager actionBarManager;
    
    // In-memory cache of live players
    private final Map<UUID, LivePlayerData> livePlayers;
    private final Map<UUID, Long> cooldowns;
    
    // Auto-removal task
    private BukkitTask autoRemovalTask;
    
    /**
     * Create a new LiveStatusManager
     * 
     * @param plugin The plugin instance
     * @param database The database manager
     * @param vaultHook The vault hook
     * @param messageManager The message manager
     * @param actionBarManager The action bar manager
     */
    public LiveStatusManager(@NotNull GoLive plugin, @NotNull DatabaseManager database, 
                           @NotNull VaultHook vaultHook, @NotNull MessageManager messageManager,
                           @NotNull ActionBarManager actionBarManager) {
        this.plugin = plugin;
        this.database = database;
        this.vaultHook = vaultHook;
        this.messageManager = messageManager;
        this.actionBarManager = actionBarManager;
        this.livePlayers = new ConcurrentHashMap<>();
        this.cooldowns = new ConcurrentHashMap<>();
    }
    
    /**
     * Initialize the LiveStatusManager
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    @NotNull
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Load live players from database
                loadLivePlayersFromDatabase();
                
                // Load all stream links from database
                loadAllStreamLinksFromDatabase();
                
                // Start auto-removal task
                startAutoRemovalTask();
                
                if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                    plugin.getLogger().info("LiveStatusManager initialized with " + livePlayers.size() + " live players.");
                }
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize LiveStatusManager: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    /**
     * Load live players from database synchronously to avoid race conditions
     */
    private void loadLivePlayersFromDatabase() {
        try {
            UUID[] livePlayerUuids = database.getLivePlayers().join();
            for (UUID uuid : livePlayerUuids) {
                String streamLink = database.getStreamLink(uuid).join();
                if (streamLink != null) {
                    livePlayers.put(uuid, new LivePlayerData(streamLink, System.currentTimeMillis()));
                }
            }
            
            if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                plugin.getLogger().info("Loaded " + livePlayerUuids.length + " live players from database.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load live players from database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load all stream links from database (for players who have set links but are not live)
     */
    private void loadAllStreamLinksFromDatabase() {
        try {
            UUID[] playersWithLinks = database.getPlayersWithStreamLinks().join();
            int loadedCount = 0;
            
            for (UUID uuid : playersWithLinks) {
                // Only load if not already in livePlayers (not currently live)
                if (!livePlayers.containsKey(uuid)) {
                    String streamLink = database.getStreamLink(uuid).join();
                    if (streamLink != null) {
                        // Store stream link for offline players (with 0 timestamp to indicate not live)
                        livePlayers.put(uuid, new LivePlayerData(streamLink, 0));
                        loadedCount++;
                    }
                }
            }
            
            if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                plugin.getLogger().info("Loaded " + loadedCount + " offline players with stream links from database.");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load stream links from database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Start the auto-removal task
     */
    private void startAutoRemovalTask() {
        if (!plugin.getConfig().getBoolean("announcements.auto-removal.enabled", true)) {
            return;
        }
        
        int delay = plugin.getConfig().getInt("announcements.auto-removal.delay", 43200); // 12 hours
        int interval = 3600; // Check every hour
        
        autoRemovalTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            long removalTime = delay * 1000L;
            
            for (Map.Entry<UUID, LivePlayerData> entry : livePlayers.entrySet()) {
                UUID uuid = entry.getKey();
                LivePlayerData data = entry.getValue();
                
                if (currentTime - data.getWentLiveTime() > removalTime) {
                    // Auto-remove from live status
                    setPlayerLiveStatus(uuid, false, null);
                }
            }
        }, 20L * interval, 20L * interval);
    }
    
    /**
     * Set a player's live status
     * 
     * @param uuid The player's UUID
     * @param isLive Whether the player is live
     * @param streamLink The stream link (can be null)
     * @return CompletableFuture that completes when status is set
     */
    @NotNull
    public CompletableFuture<Boolean> setPlayerLiveStatus(@NotNull UUID uuid, boolean isLive, @Nullable String streamLink) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // All Bukkit API calls must be on main thread
                return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    try {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player == null) {
                            plugin.getLogger().warning("Player with UUID " + uuid + " is not online.");
                            return false;
                        }
                        
                        // Check cooldown
                        if (isLive && isOnCooldown(uuid)) {
                            long remainingTime = getCooldownRemainingTime(uuid);
                            messageManager.sendMessage(player, "general.cooldown-active", "%time%", String.valueOf(remainingTime));
                            return false;
                        }
                        
                        // Update in-memory cache first
                        if (isLive) {
                            if (streamLink != null) {
                                livePlayers.put(uuid, new LivePlayerData(streamLink, System.currentTimeMillis()));
                                // Set cooldown
                                setCooldown(uuid);
                            } else {
                                plugin.getLogger().warning("Cannot set live status without stream link for " + player.getName());
                                return false;
                            }
                        } else {
                            // When going offline, keep the stream link but mark as not live (timestamp = 0)
                            LivePlayerData existingData = livePlayers.get(uuid);
                            if (existingData != null) {
                                // Keep the stream link but set timestamp to 0 to indicate not live
                                livePlayers.put(uuid, new LivePlayerData(existingData.getStreamLink(), 0));
                            }
                            removeCooldown(uuid);
                        }
                        
                        // Set live status in database asynchronously
                        database.setLiveStatus(uuid, isLive).thenAccept(success -> {
                            if (!success) {
                                plugin.getLogger().warning("Failed to update live status in database for " + player.getName());
                            }
                        });
                        
                        // Update permissions asynchronously
                        vaultHook.setLiveStatus(player, isLive).thenAccept(success -> {
                            if (!success) {
                                plugin.getLogger().warning("Failed to update permissions for " + player.getName());
                            }
                        });
                        
                        // Send messages and effects
                        if (isLive) {
                            sendLiveAnnouncement(player, streamLink);
                            // Start action bar for live player
                            actionBarManager.startActionBar(player);
                        } else {
                            sendOfflineAnnouncement(player);
                            // Stop action bar for offline player
                            actionBarManager.stopActionBar(uuid);
                        }
                        
                        return true;
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error setting live status: " + e.getMessage());
                        e.printStackTrace();
                        return false;
                    }
                }).get();
            } catch (Exception e) {
                plugin.getLogger().severe("Error in setPlayerLiveStatus: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    /**
     * Check if a player is live
     * 
     * @param uuid The player's UUID
     * @return True if the player is live
     */
    public boolean isPlayerLive(@NotNull UUID uuid) {
        LivePlayerData data = livePlayers.get(uuid);
        return data != null && data.getWentLiveTime() > 0;
    }
    
    /**
     * Get a player's stream link
     * 
     * @param uuid The player's UUID
     * @return The stream link, or null if not live
     */
    @Nullable
    public String getPlayerStreamLink(@NotNull UUID uuid) {
        LivePlayerData data = livePlayers.get(uuid);
        return data != null ? data.getStreamLink() : null;
    }
    
    /**
     * Update a player's stream link in the cache
     * 
     * @param uuid The player's UUID
     * @param streamLink The new stream link
     */
    public void updatePlayerStreamLink(@NotNull UUID uuid, @NotNull String streamLink) {
        LivePlayerData data = livePlayers.get(uuid);
        if (data != null) {
            // Replace the existing data with new stream link, preserve live status
            livePlayers.put(uuid, new LivePlayerData(streamLink, data.getWentLiveTime()));
        } else {
            // Create new data entry if player is not in cache (not live, so timestamp = 0)
            livePlayers.put(uuid, new LivePlayerData(streamLink, 0));
        }
    }
    
    /**
     * Get when a player went live
     * 
     * @param uuid The player's UUID
     * @return The timestamp when the player went live, or 0 if not live
     */
    public long getPlayerWentLiveTime(@NotNull UUID uuid) {
        LivePlayerData data = livePlayers.get(uuid);
        return data != null ? data.getWentLiveTime() : 0;
    }
    
    /**
     * Get the count of live players
     * 
     * @return The count of live players
     */
    public int getLivePlayerCount() {
        return livePlayers.size();
    }
    
    /**
     * Get all live player UUIDs
     * 
     * @return Array of live player UUIDs
     */
    @NotNull
    public UUID[] getLivePlayers() {
        return livePlayers.keySet().toArray(new UUID[0]);
    }
    
    /**
     * Send live announcement
     * 
     * @param player The player
     * @param streamLink The stream link
     */
    private void sendLiveAnnouncement(@NotNull Player player, @NotNull String streamLink) {
        // Send broadcast message (without GoLive prefix for cleaner announcements)
        messageManager.broadcastMessageNoPrefix("live.announcement", 
            "%player%", player.getName(),
            "%streamlink%", streamLink
        );
        
        // Send title
        if (plugin.getConfig().getBoolean("announcements.title.enabled", true)) {
            messageManager.sendTitle(player, "live.title", "live.subtitle",
                "%player%", player.getName(),
                "%streamlink%", streamLink
            );
        }
        
        // Play sound
        if (plugin.getConfig().getBoolean("announcements.sound.enabled", true)) {
            String soundType = plugin.getConfig().getString("announcements.sound.type", "entity.player.levelup");
            float volume = (float) plugin.getConfig().getDouble("announcements.sound.volume", 1.0);
            float pitch = (float) plugin.getConfig().getDouble("announcements.sound.pitch", 1.0);
            SoundUtils.playSound(player, soundType, volume, pitch);
        }
        
        // Send confirmation message (with GoLive prefix)
        messageManager.sendMessage(player, "live.confirmation");
    }
    
    /**
     * Send offline announcement
     * 
     * @param player The player
     */
    private void sendOfflineAnnouncement(@NotNull Player player) {
        // Send broadcast message (without GoLive prefix for cleaner announcements)
        messageManager.broadcastMessageNoPrefix("offline.announcement", "%player%", player.getName());
        
        // Send confirmation message (with GoLive prefix)
        messageManager.sendMessage(player, "offline.confirmation");
    }
    
    /**
     * Check if a player is on cooldown
     * 
     * @param uuid The player's UUID
     * @return True if on cooldown
     */
    public boolean isOnCooldown(@NotNull UUID uuid) {
        if (!plugin.getConfig().getBoolean("cooldown.enabled", true)) {
            return false;
        }
        
        Long cooldownEnd = cooldowns.get(uuid);
        if (cooldownEnd == null) {
            return false;
        }
        
        if (System.currentTimeMillis() > cooldownEnd) {
            cooldowns.remove(uuid);
            return false;
        }
        
        return true;
    }
    
    /**
     * Get remaining cooldown time in seconds
     * 
     * @param uuid The player's UUID
     * @return Remaining cooldown time in seconds
     */
    public long getCooldownRemainingTime(@NotNull UUID uuid) {
        Long cooldownEnd = cooldowns.get(uuid);
        if (cooldownEnd == null) {
            return 0;
        }
        
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }
    
    /**
     * Set cooldown for a player
     * 
     * @param uuid The player's UUID
     */
    private void setCooldown(@NotNull UUID uuid) {
        if (!plugin.getConfig().getBoolean("cooldown.enabled", true)) {
            return;
        }
        
        int duration = plugin.getConfig().getInt("cooldown.duration", 300); // 5 minutes
        long cooldownEnd = System.currentTimeMillis() + (duration * 1000L);
        cooldowns.put(uuid, cooldownEnd);
    }
    
    /**
     * Remove cooldown for a player
     * 
     * @param uuid The player's UUID
     */
    private void removeCooldown(@NotNull UUID uuid) {
        cooldowns.remove(uuid);
    }
    
    /**
     * Save all live players to database
     * 
     * @return CompletableFuture that completes when save is done
     */
    @NotNull
    public CompletableFuture<Boolean> saveLivePlayers() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                for (Map.Entry<UUID, LivePlayerData> entry : livePlayers.entrySet()) {
                    UUID uuid = entry.getKey();
                    LivePlayerData data = entry.getValue();
                    
                    database.setLiveStatus(uuid, true);
                    database.setStreamLink(uuid, data.getStreamLink());
                }
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save live players: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    /**
     * Shutdown the LiveStatusManager
     */
    public void shutdown() {
        if (autoRemovalTask != null) {
            autoRemovalTask.cancel();
        }
        
        // Stop all action bars
        actionBarManager.shutdown();
        
        // Save live players
        saveLivePlayers();
        
        // Clean up stale data
        cleanupStaleData();
        
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            plugin.getLogger().info("LiveStatusManager shutdown complete");
        }
    }
    
    /**
     * Clean up stale data (offline players, expired cooldowns)
     */
    private void cleanupStaleData() {
        try {
            // Clean up cooldowns for offline players
            cooldowns.entrySet().removeIf(entry -> {
                UUID uuid = entry.getKey();
                return Bukkit.getPlayer(uuid) == null || System.currentTimeMillis() > entry.getValue();
            });
            
            // Clean up live player data for offline players
            livePlayers.entrySet().removeIf(entry -> {
                UUID uuid = entry.getKey();
                Player player = Bukkit.getPlayer(uuid);
                return player == null || !player.isOnline();
            });
            
            if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                plugin.getLogger().info("Cleaned up stale data: " + livePlayers.size() + " live players, " + cooldowns.size() + " cooldowns");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error during cleanup: " + e.getMessage());
        }
    }
    
    /**
     * Data class for live player information
     */
    private static class LivePlayerData {
        private final String streamLink;
        private final long wentLiveTime;
        
        public LivePlayerData(@NotNull String streamLink, long wentLiveTime) {
            this.streamLink = streamLink;
            this.wentLiveTime = wentLiveTime;
        }
        
        @NotNull
        public String getStreamLink() {
            return streamLink;
        }
        
        public long getWentLiveTime() {
            return wentLiveTime;
        }
    }
}

