package dev.trygve.golive.commands;

import dev.trygve.golive.GoLive;
import dev.trygve.golive.managers.MessageManager;
import dev.trygve.golive.utils.UpdateChecker;
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
 * Main GoLive command handler
 */
public class GoLiveCommand implements CommandExecutor, TabCompleter {
    
    private final GoLive plugin;
    private final MessageManager messageManager;
    private final UpdateChecker updateChecker;
    
    /**
     * Create a new GoLiveCommand
     * 
     * @param plugin The plugin instance
     * @param messageManager The message manager
     * @param updateChecker The update checker
     */
    public GoLiveCommand(@NotNull GoLive plugin, @NotNull MessageManager messageManager, 
                        @NotNull UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.messageManager = messageManager;
        this.updateChecker = updateChecker;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                           @NotNull String label, @NotNull String[] args) {
        
        // Check permission
        if (!sender.hasPermission("golive.admin")) {
            messageManager.sendMessage((Player) sender, "general.no-permission");
            return true;
        }
        
        if (args.length == 0) {
            // Show plugin info
            showPluginInfo(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "info" -> showPluginInfo(sender);
            case "update" -> handleUpdateCheck(sender);
            case "help" -> showHelp(sender);
            default -> {
                messageManager.sendMessage((Player) sender, "general.invalid-arguments");
                return true;
            }
        }
        
        return true;
    }
    
    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, 
                                    @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("golive.admin")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            List<String> completions = Arrays.asList("reload", "info", "update", "help");
            return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Show plugin information
     * 
     * @param sender The command sender
     */
    private void showPluginInfo(@NotNull CommandSender sender) {
        List<String> infoMessages = plugin.getConfig().getStringList("admin.info");
        
        for (String message : infoMessages) {
            message = message.replace("%version%", plugin.getPluginMeta().getVersion());
            message = message.replace("%database-type%", plugin.getDatabase().getDatabaseType());
            message = message.replace("%vault-status%", plugin.getVaultHook().isVaultAvailable() ? "Enabled" : "Disabled");
            message = message.replace("%placeholderapi-status%", plugin.getPlaceholderHook().isPlaceholderApiAvailable() ? "Enabled" : "Disabled");
            
            sender.sendMessage(messageManager.getMessageComponent("admin.info", message));
        }
    }
    
    /**
     * Handle reload command
     * 
     * @param sender The command sender
     */
    private void handleReload(@NotNull CommandSender sender) {
        CompletableFuture.runAsync(() -> {
            try {
                // Reload config
                plugin.reloadConfig();
                
                // Reload messages
                messageManager.reload();
                
                // Reload vault hook
                plugin.getVaultHook().reload();
                
                // Reload placeholder hook
                plugin.getPlaceholderHook().reload();
                
                // Send success message
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    messageManager.sendMessage((Player) sender, "general.plugin-reloaded");
                });
                
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to reload plugin: " + e.getMessage());
                e.printStackTrace();
                
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    messageManager.sendMessage((Player) sender, "errors.invalid-config");
                });
            }
        });
    }
    
    /**
     * Handle update check command
     * 
     * @param sender The command sender
     */
    private void handleUpdateCheck(@NotNull CommandSender sender) {
        CompletableFuture.runAsync(() -> {
            updateChecker.checkForUpdates().thenAccept(hasUpdate -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (hasUpdate) {
                        updateChecker.notifyAdmins();
                    } else {
                        messageManager.sendMessage((Player) sender, "Plugin is up to date!");
                    }
                });
            });
        });
    }
    
    /**
     * Show help message
     * 
     * @param sender The command sender
     */
    private void showHelp(@NotNull CommandSender sender) {
        List<String> helpMessages = plugin.getConfig().getStringList("help.commands");
        
        for (String message : helpMessages) {
            sender.sendMessage(messageManager.getMessageComponent("help.commands", message));
        }
    }
}
