package dev.trygve.golive.placeholders;

import dev.trygve.golive.GoLive;
import dev.trygve.golive.managers.LiveStatusManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * PlaceholderAPI expansion for GoLive
 */
public class GoLivePlaceholders extends PlaceholderExpansion {
    
    private final GoLive plugin;
    private final LiveStatusManager liveStatusManager;
    
    /**
     * Create a new GoLivePlaceholders
     * 
     * @param plugin The plugin instance
     */
    public GoLivePlaceholders(@NotNull GoLive plugin) {
        this.plugin = plugin;
        this.liveStatusManager = plugin.getLiveStatusManager();
    }
    
    @Override
    @NotNull
    public String getIdentifier() {
        return "golive";
    }
    
    @Override
    @NotNull
    public String getAuthor() {
        return plugin.getPluginMeta().getAuthors().toString();
    }
    
    @Override
    @NotNull
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    @Nullable
    public String onPlaceholderRequest(@Nullable Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        
        return switch (params.toLowerCase()) {
            case "status" -> getPlayerStatus(player);
            case "link" -> getPlayerStreamLink(player);
            case "since" -> getTimeSinceLive(player);
            case "islive" -> isPlayerLive(player);
            case "count" -> getLivePlayerCount();
            case "count_total" -> getTotalPlayerCount();
            case "time" -> getLiveTime(player);
            case "platform" -> getStreamPlatform(player);
            default -> null;
        };
    }
    
    /**
     * Get player's live status
     * 
     * @param player The player
     * @return The status string
     */
    @NotNull
    private String getPlayerStatus(@NotNull Player player) {
        if (liveStatusManager.isPlayerLive(player.getUniqueId())) {
            return plugin.getMessageManager().getMessage("placeholders.status.live", "Live");
        } else {
            return plugin.getMessageManager().getMessage("placeholders.status.offline", "Offline");
        }
    }
    
    /**
     * Get player's stream link
     * 
     * @param player The player
     * @return The stream link
     */
    @NotNull
    private String getPlayerStreamLink(@NotNull Player player) {
        String link = liveStatusManager.getPlayerStreamLink(player.getUniqueId());
        return link != null ? link : "Not set";
    }
    
    /**
     * Get time since player went live
     * 
     * @param player The player
     * @return The time since string
     */
    @NotNull
    private String getTimeSinceLive(@NotNull Player player) {
        if (!liveStatusManager.isPlayerLive(player.getUniqueId())) {
            return plugin.getMessageManager().getMessage("placeholders.time-since.just-now", "Just now");
        }
        
        long wentLiveTime = liveStatusManager.getPlayerWentLiveTime(player.getUniqueId());
        if (wentLiveTime == 0) {
            return plugin.getMessageManager().getMessage("placeholders.time-since.just-now", "Just now");
        }
        
        Duration duration = Duration.between(
            Instant.ofEpochMilli(wentLiveTime),
            Instant.now()
        );
        
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        
        if (days > 0) {
            return plugin.getMessageManager().getMessage("placeholders.time-since.days-ago", "%days% days ago")
                .replace("%days%", String.valueOf(days));
        } else if (hours > 0) {
            return plugin.getMessageManager().getMessage("placeholders.time-since.hours-ago", "%hours% hours ago")
                .replace("%hours%", String.valueOf(hours));
        } else if (minutes > 0) {
            return plugin.getMessageManager().getMessage("placeholders.time-since.minutes-ago", "%minutes% minutes ago")
                .replace("%minutes%", String.valueOf(minutes));
        } else {
            return plugin.getMessageManager().getMessage("placeholders.time-since.just-now", "Just now");
        }
    }
    
    /**
     * Check if player is live
     * 
     * @param player The player
     * @return True/false string
     */
    @NotNull
    private String isPlayerLive(@NotNull Player player) {
        return liveStatusManager.isPlayerLive(player.getUniqueId()) ? "true" : "false";
    }
    
    /**
     * Get count of live players
     * 
     * @return The count string
     */
    @NotNull
    private String getLivePlayerCount() {
        return String.valueOf(liveStatusManager.getLivePlayerCount());
    }
    
    /**
     * Get total player count
     * 
     * @return The total count string
     */
    @NotNull
    private String getTotalPlayerCount() {
        return String.valueOf(plugin.getServer().getOnlinePlayers().size());
    }
    
    /**
     * Get live time for player
     * 
     * @param player The player
     * @return The live time string
     */
    @NotNull
    private String getLiveTime(@NotNull Player player) {
        if (!liveStatusManager.isPlayerLive(player.getUniqueId())) {
            return "0";
        }
        
        long wentLiveTime = liveStatusManager.getPlayerWentLiveTime(player.getUniqueId());
        if (wentLiveTime == 0) {
            return "0";
        }
        
        long liveTime = System.currentTimeMillis() - wentLiveTime;
        return String.valueOf(liveTime / 1000); // Return in seconds
    }
    
    /**
     * Get stream platform for player
     * 
     * @param player The player
     * @return The platform string
     */
    @NotNull
    private String getStreamPlatform(@NotNull Player player) {
        String link = liveStatusManager.getPlayerStreamLink(player.getUniqueId());
        if (link == null) {
            return "None";
        }
        
        String lowerLink = link.toLowerCase();
        if (lowerLink.contains("twitch.tv")) {
            return "Twitch";
        } else if (lowerLink.contains("youtube.com") || lowerLink.contains("youtu.be")) {
            return "YouTube";
        } else if (lowerLink.contains("kick.com")) {
            return "Kick";
        } else if (lowerLink.contains("tiktok.com")) {
            return "TikTok";
        } else if (lowerLink.contains("facebook.com")) {
            return "Facebook";
        } else if (lowerLink.contains("instagram.com")) {
            return "Instagram";
        } else {
            return "Other";
        }
    }
}
