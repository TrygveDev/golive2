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
     */
    public LiveStatusManager(@NotNull GoLive plugin, @NotNull DatabaseManager database, 
                           @NotNull VaultHook vaultHook, @NotNull MessageManager messageManager) {
        this.plugin = plugin;
        this.database = database;
        this.vaultHook = vaultHook;
        this.messageManager = messageManager;
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
                
                plugin.getLogger().info("LiveStatusManager initialized with " + livePlayers.size() + " live players.");
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize LiveStatusManager: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    /**
     * Load live players from database
     */
    private void loadLivePlayersFromDatabase() {
        database.getLivePlayers().thenAccept(uuids -> {
            for (UUID uuid : uuids) {
                database.getStreamLink(uuid).thenAccept(streamLink -> {
                    if (streamLink != null) {
                        livePlayers.put(uuid, new LivePlayerData(streamLink, System.currentTimeMillis()));
                    }
                });
            }
        });
    }
    
    /**
     * Load all stream links from database (for players who have set links but are not live)
     */
    private void loadAllStreamLinksFromDatabase() {
        database.getPlayersWithStreamLinks().thenAccept(uuids -> {
            for (UUID uuid : uuids) {
                // Only load if not already in livePlayers (not currently live)
                if (!livePlayers.containsKey(uuid)) {
                    database.getStreamLink(uuid).thenAccept(streamLink -> {
                        if (streamLink != null) {
                            // Store stream link for offline players (with 0 timestamp to indicate not live)
                            livePlayers.put(uuid, new LivePlayerData(streamLink, 0));
                        }
                    });
                }
            }
        });
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
                
                // Set live status in database
                database.setLiveStatus(uuid, isLive).thenAccept(success -> {
                    if (!success) {
                        plugin.getLogger().warning("Failed to update live status in database for " + player.getName());
                    }
                });
                
                // Update in-memory cache
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
                
                // Update permissions
                vaultHook.setLiveStatus(player, isLive).thenAccept(success -> {
                    if (!success) {
                        plugin.getLogger().warning("Failed to update permissions for " + player.getName());
                    }
                });
                
                // Send messages and effects
                if (isLive) {
                    sendLiveAnnouncement(player, streamLink);
                } else {
                    sendOfflineAnnouncement(player);
                }
                
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Error setting live status: " + e.getMessage());
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
        // Send broadcast message
        if (plugin.getConfig().getBoolean("announcements.clickable-messages", true)) {
            messageManager.broadcastMessage("live.announcement", 
                "%player%", player.getName(),
                "%streamlink%", streamLink
            );
        } else {
            messageManager.broadcastMessage("live.announcement", 
                "%player%", player.getName(),
                "%streamlink%", streamLink
            );
        }
        
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
        
        // Send confirmation message
        messageManager.sendMessage(player, "live.confirmation");
    }
    
    /**
     * Send offline announcement
     * 
     * @param player The player
     */
    private void sendOfflineAnnouncement(@NotNull Player player) {
        // Send broadcast message
        messageManager.broadcastMessage("offline.announcement", "%player%", player.getName());
        
        // Send confirmation message
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
        
        // Save live players
        saveLivePlayers();
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
