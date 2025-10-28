package dev.trygve.golive.commands;

import dev.trygve.golive.GoLive;
import dev.trygve.golive.managers.MessageManager;
import dev.trygve.golive.utils.ColorUtils;
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
            // Show help by default
            showHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
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
            List<String> completions = Arrays.asList("reload", "update", "help");
            return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        return new ArrayList<>();
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
                
                // Reload placeholder hook (must be done synchronously)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        plugin.getPlaceholderHook().reload();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to reload PlaceholderAPI hook: " + e.getMessage());
                    }
                });
                
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
        // Send checking message
        messageManager.sendMessage((Player) sender, "admin.update-checking");
        
        CompletableFuture.runAsync(() -> {
            try {
                updateChecker.checkForUpdates().thenAccept(hasUpdate -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            if (hasUpdate) {
                                // Send update available message to the sender
                                String downloadUrl = updateChecker.getDownloadUrl();
                                messageManager.sendMessage((Player) sender, "admin.update-available",
                                    "%current-version%", updateChecker.getCurrentVersion(),
                                    "%latest-version%", updateChecker.getLatestVersion(),
                                    "%download-url%", downloadUrl);
                                
                                // Also notify other admins
                                updateChecker.notifyAdmins();
                            } else {
                                // Send up-to-date message
                                messageManager.sendMessage((Player) sender, "admin.update-current",
                                    "%version%", updateChecker.getCurrentVersion());
                            }
                        } catch (Exception e) {
                            plugin.getLogger().severe("Error handling update check result: " + e.getMessage());
                            e.printStackTrace();
                            messageManager.sendMessage((Player) sender, "admin.update-error");
                        }
                    });
                });
            } catch (Exception e) {
                plugin.getLogger().severe("Error during update check: " + e.getMessage());
                e.printStackTrace();
                
                // Send error message on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    messageManager.sendMessage((Player) sender, "admin.update-error");
                });
            }
        });
    }
    
    /**
     * Show help message
     * 
     * @param sender The command sender
     */
    private void showHelp(@NotNull CommandSender sender) {
        // Send header with version
        String header = messageManager.getMessage("help.header");
        header = header.replace("%version%", plugin.getPluginMeta().getVersion());
        sender.sendMessage(ColorUtils.toComponent(header));
        
        // Send command list
        List<String> helpMessages = messageManager.getStringList("help.commands");
        
        for (String message : helpMessages) {
            sender.sendMessage(ColorUtils.toComponent(message));
        }
        
        // Send footer
        sender.sendMessage(messageManager.getMessageComponent("help.footer"));
    }
}
