package dev.trygve.golive.utils;

import dev.trygve.golive.GoLive;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Utility class for checking plugin updates from SpigotMC
 */
public class UpdateChecker {
    
    private final GoLive plugin;
    private final int resourceId;
    private final String currentVersion;
    private String latestVersion;
    private boolean updateAvailable = false;
    
    /**
     * Create a new UpdateChecker
     * 
     * @param plugin The plugin instance
     * @param resourceId The SpigotMC resource ID
     */
    public UpdateChecker(@NotNull GoLive plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }
    
    /**
     * Check for updates asynchronously
     * 
     * @return CompletableFuture that completes when the check is done
     */
    @NotNull
    public CompletableFuture<Boolean> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = URI.create("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId);
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "GoLive/" + currentVersion);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String response = reader.readLine();
                    reader.close();
                    
                    if (response != null && !response.isEmpty()) {
                        this.latestVersion = response.trim();
                        this.updateAvailable = isNewerVersion(latestVersion, currentVersion);
                        
                        // Log the result
                        if (updateAvailable) {
                            plugin.getLogger().log(Level.INFO, "Update available! Current: " + currentVersion + ", Latest: " + latestVersion);
                        } else {
                            plugin.getLogger().log(Level.INFO, "Plugin is up to date! Version: " + currentVersion);
                        }
                        
                        return updateAvailable;
                    }
                } else {
                    plugin.getLogger().log(Level.WARNING, "Failed to check for updates. Response code: " + responseCode);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to check for updates: " + e.getMessage());
            }
            
            return false;
        });
    }
    
    /**
     * Check for updates and notify admins if available
     * 
     * @param notifyAdmins Whether to notify admins about updates
     */
    public void checkAndNotify(boolean notifyAdmins) {
        checkForUpdates().thenAccept(hasUpdate -> {
            if (hasUpdate && notifyAdmins) {
                notifyAdmins();
            }
        });
    }
    
    /**
     * Notify all online admins about the update
     */
    public void notifyAdmins() {
        if (!updateAvailable) {
            return;
        }
        
        String downloadUrl = getDownloadUrl();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("golive.admin")) {
                // Send message without prefix to avoid ugly brackets
                plugin.getMessageManager().sendMessageNoPrefix(player, "admin.update-available",
                    "%current-version%", currentVersion,
                    "%latest-version%", latestVersion,
                    "%download-url%", downloadUrl);
            }
        }
    }
    
    /**
     * Get the current version
     * 
     * @return The current version
     */
    @NotNull
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    /**
     * Get the latest version
     * 
     * @return The latest version, or null if not checked yet
     */
    public String getLatestVersion() {
        return latestVersion;
    }
    
    /**
     * Check if an update is available
     * 
     * @return True if an update is available
     */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }
    
    /**
     * Get the download URL for the latest version
     * 
     * @return The download URL
     */
    @NotNull
    public String getDownloadUrl() {
        return "https://www.spigotmc.org/resources/" + resourceId;
    }
    
    /**
     * Compare two version strings
     * 
     * @param version1 First version
     * @param version2 Second version
     * @return -1 if version1 < version2, 0 if equal, 1 if version1 > version2
     */
    public static int compareVersions(@NotNull String version1, @NotNull String version2) {
        String[] v1Parts = version1.split("\\.");
        String[] v2Parts = version2.split("\\.");
        
        int maxLength = Math.max(v1Parts.length, v2Parts.length);
        
        for (int i = 0; i < maxLength; i++) {
            int v1Part = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
            int v2Part = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;
            
            if (v1Part < v2Part) {
                return -1;
            } else if (v1Part > v2Part) {
                return 1;
            }
        }
        
        return 0;
    }
    
    /**
     * Check if a version is newer than another
     * 
     * @param version1 First version
     * @param version2 Second version
     * @return True if version1 is newer than version2
     */
    public static boolean isNewerVersion(@NotNull String version1, @NotNull String version2) {
        return compareVersions(version1, version2) > 0;
    }
}
