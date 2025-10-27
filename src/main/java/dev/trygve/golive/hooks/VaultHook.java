package dev.trygve.golive.hooks;

import dev.trygve.golive.GoLive;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Vault integration for permissions and chat (prefix/suffix)
 */
public class VaultHook {
    
    private final GoLive plugin;
    private Permission permission;
    private Chat chat;
    private boolean vaultAvailable = false;
    private boolean chatAvailable = false;
    
    // Configuration values
    private String mode;
    private String livePrefix;
    private int prefixPriority;
    private String liveGroup;
    private String standardGroup;
    private String liveCommand;
    private String offlineCommand;
    
    /**
     * Create a new VaultHook
     * 
     * @param plugin The plugin instance
     */
    public VaultHook(@NotNull GoLive plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }
    
    /**
     * Load configuration values
     */
    private void loadConfiguration() {
        // Load mode
        this.mode = plugin.getConfig().getString("vault.mode", "prefix");
        
        // Load prefix settings
        this.livePrefix = plugin.getConfig().getString("vault.prefix.live", "<gradient:#ff0080:#ff8c00>🔴 LIVE</gradient> ");
        this.prefixPriority = plugin.getConfig().getInt("vault.prefix.priority", 100);
        
        // Load group settings
        this.liveGroup = plugin.getConfig().getString("vault.groups.live", "content-creator-live");
        this.standardGroup = plugin.getConfig().getString("vault.groups.standard", "content-creator");
        
        // Load command settings
        this.liveCommand = plugin.getConfig().getString("vault.commands.live", "lp user %player% meta setprefix %priority% %prefix%");
        this.offlineCommand = plugin.getConfig().getString("vault.commands.offline", "lp user %player% meta removeprefix %priority%");
    }
    
    /**
     * Initialize Vault integration
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    @NotNull
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            if (!plugin.getConfig().getBoolean("vault.enabled", true)) {
                plugin.getLogger().info("Vault integration is disabled in config.");
                return false;
            }
            
            if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                plugin.getLogger().warning("Vault not found! Using command fallback for permissions.");
                return false;
            }
            
            // Setup Permission provider
            RegisteredServiceProvider<Permission> permRsp = Bukkit.getServicesManager().getRegistration(Permission.class);
            if (permRsp == null) {
                plugin.getLogger().warning("No permission provider found! Using command fallback.");
                return false;
            }
            
            permission = permRsp.getProvider();
            if (permission == null) {
                plugin.getLogger().warning("Permission provider is null! Using command fallback.");
                return false;
            }
            
            vaultAvailable = true;
            if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                plugin.getLogger().info("Vault Permission integration enabled! Provider: " + permission.getName());
            }
            
            // Setup Chat provider (for prefix/suffix)
            RegisteredServiceProvider<Chat> chatRsp = Bukkit.getServicesManager().getRegistration(Chat.class);
            if (chatRsp != null) {
                chat = chatRsp.getProvider();
                if (chat != null) {
                    chatAvailable = true;
                    if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                        plugin.getLogger().info("Vault Chat integration enabled! Provider: " + chat.getName());
                    }
                }
            }
            
            if (!chatAvailable && "prefix".equalsIgnoreCase(mode)) {
                plugin.getLogger().warning("Chat provider not available! Prefix mode will use command fallback.");
            }
            
            return true;
        });
    }
    
    /**
     * Check if Vault is available and working
     * 
     * @return True if Vault is available
     */
    public boolean isVaultAvailable() {
        return vaultAvailable && permission != null;
    }
    
    /**
     * Set a player's live status using Vault or command fallback
     * 
     * @param player The player
     * @param isLive Whether the player is live
     * @return CompletableFuture that completes when status is set
     */
    @NotNull
    public CompletableFuture<Boolean> setLiveStatus(@NotNull Player player, boolean isLive) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if ("prefix".equalsIgnoreCase(mode)) {
                    // Try Vault Chat API first (works with all Vault-compatible plugins)
                    if (chatAvailable && setLiveStatusVaultChat(player, isLive)) {
                        return true;
                    }
                    // Fallback to command execution
                    return setLiveStatusCommand(player, isLive);
                } else {
                    // Group mode - use Vault Permission API or commands
                    if (vaultAvailable) {
                        return setLiveStatusVaultGroup(player, isLive);
                    }
                    return setLiveStatusCommand(player, isLive);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to set live status for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    /**
     * Set live status using Vault Chat API (prefix/suffix)
     * Works with all Vault-compatible permission plugins
     * 
     * @param player The player
     * @param isLive Whether the player is live
     * @return True if successful
     */
    private boolean setLiveStatusVaultChat(@NotNull Player player, boolean isLive) {
        try {
            // For better compatibility, we'll use a different approach
            // Instead of modifying the main prefix, we'll use a separate prefix slot
            
            if (isLive) {
                // Try to set a temporary prefix that can be layered
                // This approach works better with most permission plugins
                String currentPrefix = chat.getPlayerPrefix(player.getWorld().getName(), player);
                if (currentPrefix == null) currentPrefix = "";
                
                // Check if already has live prefix to avoid duplicates
                if (!currentPrefix.contains("🔴") && !currentPrefix.contains("LIVE")) {
                    // Add live prefix at the beginning
                    String newPrefix = livePrefix + currentPrefix;
                    chat.setPlayerPrefix(player, newPrefix);
                    if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                        plugin.getLogger().info("Added live prefix for " + player.getName() + " using Vault Chat API");
                    }
                }
            } else {
                // Get current prefix and remove only the live part
                String currentPrefix = chat.getPlayerPrefix(player.getWorld().getName(), player);
                if (currentPrefix != null && (currentPrefix.contains("🔴") || currentPrefix.contains("LIVE"))) {
                    // Remove the live prefix part while preserving the rest
                    String newPrefix = currentPrefix.replace(livePrefix, "");
                    // Clean up any extra spaces
                    newPrefix = newPrefix.replaceAll("^\\s+", "");
                    chat.setPlayerPrefix(player, newPrefix);
                    if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                        plugin.getLogger().info("Removed live prefix for " + player.getName() + " using Vault Chat API");
                    }
                }
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Vault Chat API error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Set live status using Vault Permission API (group mode)
     * 
     * @param player The player
     * @param isLive Whether the player is live
     * @return True if successful
     */
    private boolean setLiveStatusVaultGroup(@NotNull Player player, boolean isLive) {
        try {
            String targetGroup = isLive ? liveGroup : standardGroup;
            
            // Remove from current groups first
            for (String currentGroup : permission.getPlayerGroups(player)) {
                permission.playerRemoveGroup(player, currentGroup);
            }
            
            // Add to new group
            boolean success = permission.playerAddGroup(player, targetGroup);
            
            if (success) {
                plugin.getLogger().info("Set group for " + player.getName() + " to " + targetGroup + " using Vault Permission API");
            }
            
            return success;
        } catch (Exception e) {
            plugin.getLogger().warning("Vault Permission API error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Set live status using command
     * 
     * @param player The player
     * @param isLive Whether the player is live
     * @return True if successful
     */
    private boolean setLiveStatusCommand(@NotNull Player player, boolean isLive) {
        try {
            String command;
            
            if ("prefix".equalsIgnoreCase(mode)) {
                // Use prefix mode with better command templates
                if (isLive) {
                    // Try LuckPerms meta approach first (preserves existing prefix)
                    command = "lp user %player% meta setprefix %priority% %prefix%";
                    command = command.replace("%player%", player.getName());
                    command = command.replace("%prefix%", livePrefix);
                    command = command.replace("%priority%", String.valueOf(prefixPriority));
                } else {
                    // Remove only the live prefix
                    command = "lp user %player% meta removeprefix %priority%";
                    command = command.replace("%player%", player.getName());
                    command = command.replace("%priority%", String.valueOf(prefixPriority));
                }
            } else {
                // Use group mode (legacy)
                if (isLive) {
                    command = liveCommand.replace("%player%", player.getName());
                } else {
                    command = offlineCommand.replace("%player%", player.getName());
                }
            }
            
            // Execute command as console
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            
            if (success) {
                if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                    plugin.getLogger().info("Set live status for " + player.getName() + " to " + isLive + " using " + mode + " mode");
                }
            } else {
                plugin.getLogger().warning("Failed to execute command: " + command);
            }
            
            return success;
        } catch (Exception e) {
            plugin.getLogger().severe("Command execution error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get a player's current groups
     * 
     * @param player The player
     * @return Array of group names
     */
    @NotNull
    public String[] getPlayerGroups(@NotNull Player player) {
        if (isVaultAvailable()) {
            try {
                return permission.getPlayerGroups(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get player groups: " + e.getMessage());
            }
        }
        return new String[0];
    }
    
    /**
     * Check if a player has a specific permission
     * 
     * @param player The player
     * @param permission The permission to check
     * @return True if the player has the permission
     */
    public boolean hasPermission(@NotNull Player player, @NotNull String permission) {
        if (isVaultAvailable()) {
            try {
                return this.permission.has(player, permission);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check permission: " + e.getMessage());
            }
        }
        return player.hasPermission(permission);
    }
    
    /**
     * Check if a player is in a specific group
     * 
     * @param player The player
     * @param group The group to check
     * @return True if the player is in the group
     */
    public boolean playerInGroup(@NotNull Player player, @NotNull String group) {
        if (isVaultAvailable()) {
            try {
                return permission.playerInGroup(player, group);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check group membership: " + e.getMessage());
            }
        }
        return false;
    }
    
    /**
     * Get the permission provider name
     * 
     * @return The permission provider name, or null if not available
     */
    @Nullable
    public String getPermissionProviderName() {
        if (isVaultAvailable()) {
            return permission.getName();
        }
        return null;
    }
    
    /**
     * Get the current mode
     * 
     * @return The mode ("prefix" or "group")
     */
    @NotNull
    public String getMode() {
        return mode;
    }
    
    /**
     * Get the live prefix
     * 
     * @return The live prefix
     */
    @NotNull
    public String getLivePrefix() {
        return livePrefix;
    }
    
    /**
     * Get the live group name (for group mode)
     * 
     * @return The live group name
     */
    @NotNull
    public String getLiveGroup() {
        return liveGroup;
    }
    
    /**
     * Get the standard group name (for group mode)
     * 
     * @return The standard group name
     */
    @NotNull
    public String getStandardGroup() {
        return standardGroup;
    }
    
    /**
     * Get the live command template
     * 
     * @return The live command template
     */
    @NotNull
    public String getLiveCommand() {
        return liveCommand;
    }
    
    /**
     * Get the offline command template
     * 
     * @return The offline command template
     */
    @NotNull
    public String getOfflineCommand() {
        return offlineCommand;
    }
    
    /**
     * Reload the Vault hook configuration
     */
    public void reload() {
        // Reload configuration
        loadConfiguration();
        // Re-initialize Vault connection
        initialize();
    }
}
