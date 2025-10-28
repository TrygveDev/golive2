package dev.trygve.golive.managers;

import dev.trygve.golive.GoLive;
import dev.trygve.golive.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages all plugin messages with MiniMessage support
 */
public class MessageManager {
    
    private final GoLive plugin;
    private final Map<String, String> messages;
    private String prefix;
    private org.bukkit.configuration.file.YamlConfiguration messagesConfig;
    
    /**
     * Create a new MessageManager
     * 
     * @param plugin The plugin instance
     */
    public MessageManager(@NotNull GoLive plugin) {
        this.plugin = plugin;
        this.messages = new HashMap<>();
    }
    
    /**
     * Initialize the MessageManager
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    @NotNull
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                loadMessages();
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize MessageManager: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    /**
     * Load messages from messages.yml
     */
    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            try {
                plugin.saveResource("messages.yml", false);
                plugin.getLogger().info("Created default messages.yml file");
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create default messages.yml: " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }
        
        try {
            // Load messages.yml as a separate configuration
            this.messagesConfig = new org.bukkit.configuration.file.YamlConfiguration();
            this.messagesConfig.load(messagesFile);
            
            // Validate that messages.yml is not empty
            if (this.messagesConfig.getKeys(false).isEmpty()) {
                plugin.getLogger().severe("messages.yml appears to be empty or corrupted!");
                return;
            }
            
            // Load prefix with fallback
            this.prefix = this.messagesConfig.getString("prefix", "<gradient:#8b5cf6:#4c1d95>[GoLive]</gradient>");
            if (this.prefix == null || this.prefix.trim().isEmpty()) {
                plugin.getLogger().warning("Prefix is empty in messages.yml, using default");
                this.prefix = "<gradient:#8b5cf6:#4c1d95>[GoLive]</gradient>";
            }
            
            // Load all messages
            int loadedCount = 0;
            loadedCount += loadMessagesFromConfig(this.messagesConfig, "general", "general");
            loadedCount += loadMessagesFromConfig(this.messagesConfig, "live", "live");
            loadedCount += loadMessagesFromConfig(this.messagesConfig, "offline", "offline");
            loadedCount += loadMessagesFromConfig(this.messagesConfig, "stream-link", "stream-link");
            loadedCount += loadMessagesFromConfig(this.messagesConfig, "gui", "gui");
            loadedCount += loadMessagesFromConfig(this.messagesConfig, "help", "help");
            loadedCount += loadMessagesFromConfig(this.messagesConfig, "admin", "admin");
            loadedCount += loadMessagesFromConfig(this.messagesConfig, "errors", "errors");
            loadedCount += loadMessagesFromConfig(this.messagesConfig, "placeholders", "placeholders");
            
            if (loadedCount == 0) {
                plugin.getLogger().severe("No messages were loaded from messages.yml! Check file format.");
                return;
            }
            
            if (plugin.getConfig().getBoolean("debug.enabled", false)) {
                plugin.getLogger().info("Loaded " + loadedCount + " messages from messages.yml");
            }
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().severe("Invalid YAML format in messages.yml: " + e.getMessage());
            plugin.getLogger().severe("Please check your messages.yml file for syntax errors");
            e.printStackTrace();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load messages: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load messages from a specific config section
     * 
     * @param config The config to load from
     * @param section The section name
     * @param prefix The prefix for message keys
     * @return Number of messages loaded from this section
     */
    private int loadMessagesFromConfig(@NotNull org.bukkit.configuration.Configuration config, @NotNull String section, @NotNull String prefix) {
        int count = 0;
        if (config.contains(section)) {
            var sectionConfig = config.getConfigurationSection(section);
            if (sectionConfig != null) {
                for (String key : sectionConfig.getKeys(true)) {
                    String fullKey = prefix + "." + key;
                    String value = sectionConfig.getString(key);
                    if (value != null && !value.trim().isEmpty()) {
                        messages.put(fullKey, value);
                        count++;
                    } else if (value != null && value.trim().isEmpty()) {
                        plugin.getLogger().warning("Empty message found for key: " + fullKey);
                    }
                }
            }
        }
        return count;
    }
    
    /**
     * Get a message by key
     * 
     * @param key The message key
     * @return The message, or null if not found
     */
    @Nullable
    public String getMessage(@NotNull String key) {
        return messages.get(key);
    }
    
    /**
     * Get a message by key with default value
     * 
     * @param key The message key
     * @param defaultValue The default value if message not found
     * @return The message or default value
     */
    @NotNull
    public String getMessage(@NotNull String key, @NotNull String defaultValue) {
        return messages.getOrDefault(key, defaultValue);
    }
    
    /**
     * Get a message as a Component
     * 
     * @param key The message key
     * @return The message as a Component
     */
    @NotNull
    public Component getMessageComponent(@NotNull String key) {
        String message = getMessage(key, "<red>Message not found: " + key + "</red>");
        return ColorUtils.toComponent(message);
    }
    
    /**
     * Get a string list from the messages config
     * 
     * @param key The config key
     * @return The string list, or empty list if not found
     */
    @NotNull
    public java.util.List<String> getStringList(@NotNull String key) {
        if (messagesConfig != null && messagesConfig.contains(key)) {
            return messagesConfig.getStringList(key);
        }
        return new java.util.ArrayList<>();
    }
    
    /**
     * Get a message as a Component with placeholders
     * 
     * @param key The message key
     * @param placeholders The placeholders to replace
     * @return The message as a Component
     */
    @NotNull
    public Component getMessageComponent(@NotNull String key, @NotNull String... placeholders) {
        String message = getMessage(key, "<red>Message not found: " + key + "</red>");
        message = ColorUtils.replacePlaceholders(message, placeholders);
        return ColorUtils.toComponent(message);
    }
    
    /**
     * Get the prefix
     * 
     * @return The prefix as a Component
     */
    @NotNull
    public Component getPrefix() {
        return ColorUtils.toComponent(prefix);
    }
    
    /**
     * Send a message to a player
     * 
     * @param player The player
     * @param key The message key
     */
    public void sendMessage(@NotNull Player player, @NotNull String key) {
        Component message = getMessageComponent(key);
        Component prefixedMessage = getPrefix().append(Component.text(" ")).append(message);
        player.sendMessage(prefixedMessage);
    }
    
    /**
     * Send a message to a player with placeholders
     * 
     * @param player The player
     * @param key The message key
     * @param placeholders The placeholders to replace
     */
    public void sendMessage(@NotNull Player player, @NotNull String key, @NotNull String... placeholders) {
        Component message = getMessageComponent(key, placeholders);
        Component prefixedMessage = getPrefix().append(Component.text(" ")).append(message);
        player.sendMessage(prefixedMessage);
    }
    
    /**
     * Send a message to all players
     * 
     * @param key The message key
     */
    public void broadcastMessage(@NotNull String key) {
        Component message = getMessageComponent(key);
        Component prefixedMessage = getPrefix().append(Component.text(" ")).append(message);
        Bukkit.broadcast(prefixedMessage);
    }
    
    /**
     * Send a message to all players with placeholders
     * 
     * @param key The message key
     * @param placeholders The placeholders to replace
     */
    public void broadcastMessage(@NotNull String key, @NotNull String... placeholders) {
        Component message = getMessageComponent(key, placeholders);
        Component prefixedMessage = getPrefix().append(Component.text(" ")).append(message);
        Bukkit.broadcast(prefixedMessage);
    }
    
    /**
     * Send a message to a player without prefix
     * 
     * @param player The player
     * @param key The message key
     */
    public void sendMessageNoPrefix(@NotNull Player player, @NotNull String key) {
        Component message = getMessageComponent(key);
        player.sendMessage(message);
    }
    
    /**
     * Send a message to a player without prefix with placeholders
     * 
     * @param player The player
     * @param key The message key
     * @param placeholders The placeholders to replace
     */
    public void sendMessageNoPrefix(@NotNull Player player, @NotNull String key, @NotNull String... placeholders) {
        Component message = getMessageComponent(key, placeholders);
        player.sendMessage(message);
    }
    
    /**
     * Send a message to all players without prefix
     * 
     * @param key The message key
     */
    public void broadcastMessageNoPrefix(@NotNull String key) {
        Component message = getMessageComponent(key);
        Bukkit.broadcast(message);
    }
    
    /**
     * Send a message to all players without prefix with placeholders
     * 
     * @param key The message key
     * @param placeholders The placeholders to replace
     */
    public void broadcastMessageNoPrefix(@NotNull String key, @NotNull String... placeholders) {
        Component message = getMessageComponent(key, placeholders);
        Bukkit.broadcast(message);
    }
    
    /**
     * Send a title to a player
     * 
     * @param player The player
     * @param titleKey The title message key
     * @param subtitleKey The subtitle message key
     */
    public void sendTitle(@NotNull Player player, @NotNull String titleKey, @NotNull String subtitleKey) {
        Component title = getMessageComponent(titleKey);
        Component subtitle = getMessageComponent(subtitleKey);
        
        Title titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(plugin.getConfig().getInt("announcements.title.fade-in", 10) * 50),
                Duration.ofMillis(plugin.getConfig().getInt("announcements.title.stay", 50) * 50),
                Duration.ofMillis(plugin.getConfig().getInt("announcements.title.fade-out", 10) * 50)
            )
        );
        
        player.showTitle(titleObj);
    }
    
    /**
     * Send a title to a player with placeholders
     * 
     * @param player The player
     * @param titleKey The title message key
     * @param subtitleKey The subtitle message key
     * @param placeholders The placeholders to replace
     */
    public void sendTitle(@NotNull Player player, @NotNull String titleKey, @NotNull String subtitleKey, @NotNull String... placeholders) {
        Component title = getMessageComponent(titleKey, placeholders);
        Component subtitle = getMessageComponent(subtitleKey, placeholders);
        
        Title titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(plugin.getConfig().getInt("announcements.title.fade-in", 10) * 50),
                Duration.ofMillis(plugin.getConfig().getInt("announcements.title.stay", 50) * 50),
                Duration.ofMillis(plugin.getConfig().getInt("announcements.title.fade-out", 10) * 50)
            )
        );
        
        player.showTitle(titleObj);
    }
    
    /**
     * Send an action bar message to a player
     * 
     * @param player The player
     * @param key The message key
     */
    public void sendActionBar(@NotNull Player player, @NotNull String key) {
        Component message = getMessageComponent(key);
        player.sendActionBar(message);
    }
    
    /**
     * Send an action bar message to a player with placeholders
     * 
     * @param player The player
     * @param key The message key
     * @param placeholders The placeholders to replace
     */
    public void sendActionBar(@NotNull Player player, @NotNull String key, @NotNull String... placeholders) {
        Component message = getMessageComponent(key, placeholders);
        player.sendActionBar(message);
    }
    
    /**
     * Get the update available message
     * 
     * @param currentVersion The current version
     * @param latestVersion The latest version
     * @return The update message as a Component
     */
    @NotNull
    public Component getUpdateAvailableMessage(@NotNull String currentVersion, @NotNull String latestVersion) {
        return getMessageComponent("admin.update-available", 
            "%current-version%", currentVersion,
            "%latest-version%", latestVersion,
            "%download-url%", "https://www.spigotmc.org/resources/golive.12345"
        );
    }
    
    /**
     * Reload messages
     * 
     * @return CompletableFuture that completes when reload is done
     */
    @NotNull
    public CompletableFuture<Boolean> reload() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                messages.clear();
                loadMessages();
                plugin.getLogger().info("Messages reloaded successfully!");
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to reload messages: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
}
