package dev.trygve.golive.commands;

import dev.trygve.golive.GoLive;
import dev.trygve.golive.managers.LiveStatusManager;
import dev.trygve.golive.managers.MessageManager;
import dev.trygve.golive.gui.GuiManager;
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

/**
 * Offline command handler
 */
public class OfflineCommand implements CommandExecutor, TabCompleter {
    
    private final GoLive plugin;
    private final LiveStatusManager liveStatusManager;
    private final MessageManager messageManager;
    private final GuiManager guiManager;
    
    /**
     * Create a new OfflineCommand
     * 
     * @param plugin The plugin instance
     * @param liveStatusManager The live status manager
     * @param messageManager The message manager
     * @param guiManager The GUI manager
     */
    public OfflineCommand(@NotNull GoLive plugin, @NotNull LiveStatusManager liveStatusManager,
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
        if (!player.hasPermission("golive.offline")) {
            messageManager.sendMessage(player, "general.no-permission");
            return true;
        }
        
        if (args.length == 0) {
            // Open GUI or go offline directly
            if (plugin.getConfig().getBoolean("gui.enabled", true)) {
                guiManager.openGui(player);
            } else {
                goOfflineDirect(player);
            }
            return true;
        }
        
        switch (args[0].toLowerCase()) {
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
        if (!(sender instanceof Player player) || !player.hasPermission("golive.offline")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            List<String> completions = Arrays.asList("help");
            return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Go offline directly (without GUI)
     * 
     * @param player The player
     */
    private void goOfflineDirect(@NotNull Player player) {
        // Check if player is actually live
        if (!liveStatusManager.isPlayerLive(player.getUniqueId())) {
            messageManager.sendMessage(player, "You are not currently live.");
            return;
        }
        
        // Set player offline
        liveStatusManager.setPlayerLiveStatus(player.getUniqueId(), false, null);
    }
    
    /**
     * Show help message
     * 
     * @param player The player
     */
    private void showHelp(@NotNull Player player) {
        List<String> helpMessages = plugin.getConfig().getStringList("help.commands");
        
        for (String message : helpMessages) {
            player.sendMessage(messageManager.getMessageComponent("help.commands", message));
        }
    }
}
