package dev.trygve.golive.hooks;

import dev.trygve.golive.GoLive;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Vault integration for permissions
 */
public class VaultHook {
    
    private final GoLive plugin;
    private Permission permission;
    private boolean vaultAvailable = false;
    
    // Configuration values
    private final String liveGroup;
    private final String standardGroup;
    private final String liveCommand;
    private final String standardCommand;
    
    /**
     * Create a new VaultHook
     * 
     * @param plugin The plugin instance
     */
    public VaultHook(@NotNull GoLive plugin) {
        this.plugin = plugin;
        
        // Load configuration
        this.liveGroup = plugin.getConfig().getString("vault.groups.live", "content-creator-live");
        this.standardGroup = plugin.getConfig().getString("vault.groups.standard", "content-creator");
        this.liveCommand = plugin.getConfig().getString("vault.fallback-commands.live", "lp user %player% parent set content-creator-live");
        this.standardCommand = plugin.getConfig().getString("vault.fallback-commands.standard", "lp user %player% parent set content-creator");
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
            
            RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);
            if (rsp == null) {
                plugin.getLogger().warning("No permission provider found! Using command fallback for permissions.");
                return false;
            }
            
            permission = rsp.getProvider();
            if (permission == null) {
                plugin.getLogger().warning("Permission provider is null! Using command fallback for permissions.");
                return false;
            }
            
            vaultAvailable = true;
            plugin.getLogger().info("Vault integration enabled! Using " + permission.getName() + " for permissions.");
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
                if (isVaultAvailable()) {
                    return setLiveStatusVault(player, isLive);
                } else {
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
     * Set live status using Vault
     * 
     * @param player The player
     * @param isLive Whether the player is live
     * @return True if successful
     */
    private boolean setLiveStatusVault(@NotNull Player player, boolean isLive) {
        try {
            String group = isLive ? liveGroup : standardGroup;
            
            // Remove from current groups first
            for (String currentGroup : permission.getPlayerGroups(player)) {
                permission.playerRemoveGroup(player, currentGroup);
            }
            
            // Add to new group
            return permission.playerAddGroup(player, group);
        } catch (Exception e) {
            plugin.getLogger().severe("Vault error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Set live status using command fallback
     * 
     * @param player The player
     * @param isLive Whether the player is live
     * @return True if successful
     */
    private boolean setLiveStatusCommand(@NotNull Player player, boolean isLive) {
        try {
            String command = isLive ? liveCommand : standardCommand;
            command = command.replace("%player%", player.getName());
            
            // Execute command as console
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            
            if (!success) {
                plugin.getLogger().warning("Failed to execute permission command: " + command);
            }
            
            return success;
        } catch (Exception e) {
            plugin.getLogger().severe("Command fallback error: " + e.getMessage());
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
     * Get the live group name
     * 
     * @return The live group name
     */
    @NotNull
    public String getLiveGroup() {
        return liveGroup;
    }
    
    /**
     * Get the standard group name
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
     * Get the standard command template
     * 
     * @return The standard command template
     */
    @NotNull
    public String getStandardCommand() {
        return standardCommand;
    }
    
    /**
     * Reload the Vault hook configuration
     */
    public void reload() {
        // Re-initialize to pick up config changes
        initialize();
    }
}
