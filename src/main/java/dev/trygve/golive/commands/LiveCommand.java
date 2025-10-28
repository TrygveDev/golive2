package dev.trygve.golive.commands;

import dev.trygve.golive.GoLive;
import dev.trygve.golive.managers.LiveStatusManager;
import dev.trygve.golive.managers.MessageManager;
import dev.trygve.golive.gui.GuiManager;
import dev.trygve.golive.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Live command handler
 */
public class LiveCommand implements CommandExecutor, TabCompleter {
    
    private final GoLive plugin;
    private final LiveStatusManager liveStatusManager;
    private final MessageManager messageManager;
    private final GuiManager guiManager;
    
    /**
     * Create a new LiveCommand
     * 
     * @param plugin The plugin instance
     * @param liveStatusManager The live status manager
     * @param messageManager The message manager
     * @param guiManager The GUI manager
     */
    public LiveCommand(@NotNull GoLive plugin, @NotNull LiveStatusManager liveStatusManager,
                      @NotNull MessageManager messageManager, @NotNull GuiManager guiManager) {
        this.plugin = plugin;
        this.liveStatusManager = liveStatusManager;
        this.messageManager = messageManager;
        this.guiManager = guiManager;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                           @NotNull String label, @NotNull String[] args) {
        
        // Check if sender is a player
        if (!(sender instanceof Player player)) {
            messageManager.sendMessage((Player) sender, "general.player-only");
            return true;
        }
        
        // Check permission
        if (!player.hasPermission("golive.live")) {
            messageManager.sendMessage(player, "general.no-permission");
            return true;
        }
        
        if (args.length == 0) {
            // Open GUI or go live directly
            if (plugin.getConfig().getBoolean("gui.enabled", true)) {
                guiManager.openGui(player);
            } else {
                goLiveDirect(player);
            }
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "setlink" -> handleSetLink(player, args);
            case "link" -> handleShowLink(player);
            case "help" -> showHelp(player);
            default -> {
                messageManager.sendMessage(player, "general.invalid-arguments");
                return true;
            }
        }
        
        return true;
    }
    
    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, 
                                    @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("golive.live")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            List<String> completions = Arrays.asList("setlink", "link", "help");
            return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("setlink")) {
            return Arrays.asList("https://twitch.tv/", "https://youtube.com/", "https://kick.com/");
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Go live directly (without GUI)
     * 
     * @param player The player
     */
    private void goLiveDirect(@NotNull Player player) {
        // Check if player has a stream link
        String streamLink = liveStatusManager.getPlayerStreamLink(player.getUniqueId());
        if (streamLink == null) {
            messageManager.sendMessage(player, "stream-link.not-set");
            return;
        }
        
        // Set player live
        liveStatusManager.setPlayerLiveStatus(player.getUniqueId(), true, streamLink);
    }
    
    /**
     * Handle set link command
     * 
     * @param player The player
     * @param args The command arguments
     */
    private void handleSetLink(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            messageManager.sendMessage(player, "general.invalid-arguments");
            return;
        }
        
        String link = args[1];
        
        // Validate link
        if (!isValidStreamLink(link)) {
            messageManager.sendMessage(player, "stream-link.invalid");
            return;
        }
        
        // Set stream link in database
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getDatabase().setStreamLink(player.getUniqueId(), link).thenAccept(success -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Get fresh player reference
                        Player currentPlayer = Bukkit.getPlayer(player.getUniqueId());
                        if (currentPlayer == null || !currentPlayer.isOnline()) {
                            return;
                        }
                        
                        if (success) {
                            // Update the cache in LiveStatusManager
                            liveStatusManager.updatePlayerStreamLink(player.getUniqueId(), link);
                            messageManager.sendMessage(currentPlayer, "stream-link.set", "%link%", link);
                        } else {
                            messageManager.sendMessage(currentPlayer, "errors.database-error");
                        }
                    });
                });
            } catch (Exception e) {
                plugin.getLogger().severe("Error setting stream link: " + e.getMessage());
                e.printStackTrace();
                
                // Send error message on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player currentPlayer = Bukkit.getPlayer(player.getUniqueId());
                    if (currentPlayer != null && currentPlayer.isOnline()) {
                        messageManager.sendMessage(currentPlayer, "errors.database-error");
                    }
                });
            }
        });
    }
    
    /**
     * Handle show link command
     * 
     * @param player The player
     */
    private void handleShowLink(@NotNull Player player) {
        String link = liveStatusManager.getPlayerStreamLink(player.getUniqueId());
        if (link != null) {
            messageManager.sendMessage(player, "stream-link.current", "%link%", link);
        } else {
            messageManager.sendMessage(player, "stream-link.not-set");
        }
    }
    
    /**
     * Show help message
     * 
     * @param player The player
     */
    private void showHelp(@NotNull Player player) {
        List<String> helpMessages = plugin.getConfig().getStringList("help.commands");
        
        for (String message : helpMessages) {
            player.sendMessage(ColorUtils.toComponent(message));
        }
    }
    
    /**
     * Validate stream link
     * 
     * @param link The link to validate
     * @return True if valid
     */
    private boolean isValidStreamLink(@NotNull String link) {
        // Length check
        if (link.length() > 500) {
            return false;
        }
        
        // HTTPS requirement
        if (!link.startsWith("https://")) {
            return false;
        }
        
        // URL structure validation
        try {
            java.net.URI uri = new java.net.URI(link);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            
            // Domain validation (case-insensitive)
            List<String> allowedDomains = plugin.getConfig().getStringList("stream-validation.allowed-domains");
            if (!allowedDomains.isEmpty()) {
                boolean allowed = allowedDomains.stream()
                    .anyMatch(domain -> host.toLowerCase().endsWith(domain.toLowerCase()));
                if (!allowed) {
                    return false;
                }
            }
            
            // Additional security checks
            // Prevent potential XSS or malicious URLs
            if (link.contains("javascript:") || link.contains("data:") || link.contains("vbscript:")) {
                return false;
            }
            
            // Check for suspicious characters
            if (link.contains("<") || link.contains(">") || link.contains("\"") || link.contains("'")) {
                return false;
            }
            
            return true;
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }
}
