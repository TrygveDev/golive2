package dev.trygve.golive;

import dev.trygve.golive.commands.GoLiveCommand;
import dev.trygve.golive.commands.LiveCommand;
import dev.trygve.golive.commands.OfflineCommand;
import dev.trygve.golive.database.DatabaseManager;
import dev.trygve.golive.database.MySQLDatabase;
import dev.trygve.golive.database.SQLiteDatabase;
import dev.trygve.golive.gui.GuiManager;
import dev.trygve.golive.hooks.PlaceholderHook;
import dev.trygve.golive.hooks.VaultHook;
import dev.trygve.golive.listeners.GuiListener;
import dev.trygve.golive.listeners.PlayerJoinListener;
import dev.trygve.golive.managers.ActionBarManager;
import dev.trygve.golive.managers.LiveStatusManager;
import dev.trygve.golive.managers.MessageManager;
import dev.trygve.golive.utils.HealthChecker;
import dev.trygve.golive.utils.MetricsManager;
import dev.trygve.golive.utils.UpdateChecker;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * GoLive v2.0 - Modern livestream announcement plugin
 */
public final class GoLive extends JavaPlugin {

    private static GoLive instance;
    
    // Core managers
    private DatabaseManager database;
    private MessageManager messageManager;
    private ActionBarManager actionBarManager;
    private LiveStatusManager liveStatusManager;
    private GuiManager guiManager;
    
    // Hooks
    private VaultHook vaultHook;
    private PlaceholderHook placeholderHook;
    
    // Utilities
    private UpdateChecker updateChecker;
    private HealthChecker healthChecker;
    
    // Cached version for performance
    private String pluginVersion;

    @Override
    public void onEnable() {
        instance = this;
        
        // Cache version for performance
        pluginVersion = getPluginMeta().getVersion();
        
        // Professional startup message
        printStartupHeader();
        
        // Initialize plugin asynchronously
        initializePlugin().thenAccept(success -> {
            if (success) {
                getLogger().info("GoLive v" + pluginVersion + " has been enabled successfully!");
                if (getConfig().getBoolean("debug.enabled", false)) {
                    getLogger().info("Available commands: /golive, /live, /offline");
                }
            } else {
                getLogger().severe("Failed to initialize GoLive v" + pluginVersion + "!");
                getServer().getPluginManager().disablePlugin(this);
            }
        });
    }
    
    /**
     * Print the professional startup header
     */
    private void printStartupHeader() {
        String serverType = getServer().getName();

        getLogger().info("\u001B[35m   ___  ___  _    _____   _____ \u001B[0m");
        getLogger().info("\u001B[35m  / __|/ _ \\| |  |_ _\\ \\ / / __|\u001B[0m");
        getLogger().info("\u001B[35m | (_ | (_) | |__ | | \\ V /| _| \u001B[0m");
        getLogger().info("\u001B[35m  \\___/\\___/|____|___| \\_/ |___|\u001B[0m");
        getLogger().info("\u001B[35m                                \u001B[0m");
        getLogger().info("\u001B[35mGoLive v" + pluginVersion + "\u001B[0m \u001B[90m- Running on \u001B[33m" + serverType + "\u001B[0m");
    }

   
                                  

    @Override
    public void onDisable() {
        if (getConfig().getBoolean("debug.enabled", false)) {
            getLogger().info("GoLive v" + pluginVersion + " is shutting down...");
        }
        
        // Stop health checker
        if (healthChecker != null) {
            healthChecker.stop();
        }
        
        // Shutdown managers
        if (liveStatusManager != null) {
            liveStatusManager.shutdown();
        }
        
        if (guiManager != null) {
            guiManager.shutdown();
        }
        
        if (database != null) {
            database.close();
        }
        
        if (placeholderHook != null) {
            placeholderHook.unregister();
        }
        
        if (getConfig().getBoolean("debug.enabled", false)) {
            getLogger().info("GoLive v" + pluginVersion + " has been disabled!");
        }
    }

    /**
     * Initialize the plugin
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    @NotNull
    private CompletableFuture<Boolean> initializePlugin() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Save default config
                saveDefaultConfig();
                
                // Validate configuration
                if (!validateConfiguration()) {
                    return false;
                }
                
                // Initialize database
                if (!initializeDatabase()) {
                    return false;
                }
                
                // Initialize message manager
                messageManager = new MessageManager(this);
                if (!messageManager.initialize().join()) {
                    return false;
                }
                
                // Initialize vault hook
                vaultHook = new VaultHook(this);
                vaultHook.initialize().join();
                
                // Initialize action bar manager
                actionBarManager = new ActionBarManager(this, messageManager);
                if (!actionBarManager.initialize()) {
                    return false;
                }
                
                // Initialize live status manager
                liveStatusManager = new LiveStatusManager(this, database, vaultHook, messageManager, actionBarManager);
                if (!liveStatusManager.initialize().join()) {
                    return false;
                }
                
                // Set the live status manager in action bar manager
                actionBarManager.setLiveStatusManager(liveStatusManager);
                
                // Initialize GUI manager
                guiManager = new GuiManager(this, liveStatusManager, messageManager);
                if (!guiManager.initialize().join()) {
                    return false;
                }
                
                // Initialize placeholder hook
                placeholderHook = new PlaceholderHook(this);
                placeholderHook.initialize().join();
                
                // Initialize update checker
                updateChecker = new UpdateChecker(this, 88288);
                
                // Initialize health checker
                healthChecker = new HealthChecker(this);
                healthChecker.start();
                
                // Initialize metrics
                if (getConfig().getBoolean("metrics.enabled", true)) {
                    try {
                        new MetricsManager(this, 11803);
                        if (getConfig().getBoolean("debug.enabled", false)) {
                            getLogger().info("Metrics enabled successfully!");
                        }
                    } catch (Exception e) {
                        getLogger().warning("Failed to initialize metrics: " + e.getMessage());
                        if (getConfig().getBoolean("debug.enabled", false)) {
                            getLogger().info("Continuing without metrics...");
                        }
                    }
                }
                
                // Register commands
                registerCommands();
                
                // Register event listeners
                registerListeners();
                
                // Check for updates
                if (getConfig().getBoolean("update-checker.enabled", true)) {
                    updateChecker.checkAndNotify(getConfig().getBoolean("update-checker.notify-admins", true));
                }
                
                return true;
            } catch (Exception e) {
                getLogger().severe("Error during plugin initialization: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Validate configuration values
     * 
     * @return True if configuration is valid
     */
    private boolean validateConfiguration() {
        try {
            // Validate database type
            String databaseType = getConfig().getString("database.type", "SQLITE").toUpperCase();
            if (!databaseType.equals("SQLITE") && !databaseType.equals("MYSQL")) {
                getLogger().severe("Invalid database type: " + databaseType + ". Must be SQLITE or MYSQL.");
                return false;
            }
            
            // Validate MySQL settings if using MySQL
            if (databaseType.equals("MYSQL")) {
                String host = getConfig().getString("database.mysql.host");
                int port = getConfig().getInt("database.mysql.port");
                String database = getConfig().getString("database.mysql.database");
                String username = getConfig().getString("database.mysql.username");
                String password = getConfig().getString("database.mysql.password");
                
                if (host == null || host.trim().isEmpty()) {
                    getLogger().severe("MySQL host cannot be empty!");
                    return false;
                }
                
                if (port <= 0 || port > 65535) {
                    getLogger().severe("Invalid MySQL port: " + port + ". Must be between 1 and 65535.");
                    return false;
                }
                
                if (database == null || database.trim().isEmpty()) {
                    getLogger().severe("MySQL database name cannot be empty!");
                    return false;
                }
                
                if (username == null || username.trim().isEmpty()) {
                    getLogger().severe("MySQL username cannot be empty!");
                    return false;
                }
                
                if (password == null) {
                    getLogger().severe("MySQL password cannot be null!");
                    return false;
                }
                
                // Check for default insecure password
                if (password.equals("password") || password.equals("your_secure_password")) {
                    getLogger().warning("WARNING: You are using a default/insecure MySQL password! Please change it in config.yml");
                }
            }
            
            // Validate action bar settings
            long updateInterval = getConfig().getLong("action-bar.update-interval", 20L);
            if (updateInterval < 1 || updateInterval > 200) {
                getLogger().warning("Action bar update interval is outside recommended range (1-200 ticks). Current: " + updateInterval);
            }
            
            // Validate cooldown settings
            int cooldownDuration = getConfig().getInt("cooldown.duration", 300);
            if (cooldownDuration < 0) {
                getLogger().severe("Cooldown duration cannot be negative!");
                return false;
            }
            
            // Validate auto-removal settings
            int autoRemovalDelay = getConfig().getInt("announcements.auto-removal.delay", 43200);
            if (autoRemovalDelay < 0) {
                getLogger().severe("Auto-removal delay cannot be negative!");
                return false;
            }
            
            if (getConfig().getBoolean("debug.enabled", false)) {
                getLogger().info("Configuration validation passed!");
            }
            
            return true;
        } catch (Exception e) {
            getLogger().severe("Error validating configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Initialize the database
     * 
     * @return True if successful
     */
    private boolean initializeDatabase() {
        try {
            String databaseType = getConfig().getString("database.type", "SQLITE").toUpperCase();
            
            if (databaseType.equals("MYSQL")) {
                database = new MySQLDatabase(this);
            } else {
                database = new SQLiteDatabase(this);
            }
            
            if (!database.initialize().join()) {
                getLogger().severe("Failed to initialize database!");
                return false;
            }
            
            if (!database.createTables().join()) {
                getLogger().severe("Failed to create database tables!");
                return false;
            }
            
            if (getConfig().getBoolean("debug.enabled", false)) {
                getLogger().info("Database initialized successfully! Type: " + database.getDatabaseType());
            }
            return true;
        } catch (Exception e) {
            getLogger().severe("Database initialization error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Register commands
     */
    private void registerCommands() {
        // Main command
        GoLiveCommand goLiveCommand = new GoLiveCommand(this, messageManager, updateChecker);
        getCommand("golive").setExecutor(goLiveCommand);
        getCommand("golive").setTabCompleter(goLiveCommand);
        
        // Live command
        LiveCommand liveCommand = new LiveCommand(this, liveStatusManager, messageManager, guiManager);
        getCommand("live").setExecutor(liveCommand);
        getCommand("live").setTabCompleter(liveCommand);
        
        // Offline command
        OfflineCommand offlineCommand = new OfflineCommand(this, liveStatusManager, messageManager, guiManager);
        getCommand("offline").setExecutor(offlineCommand);
        getCommand("offline").setTabCompleter(offlineCommand);
    }

    /**
     * Register event listeners
     */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
            new PlayerJoinListener(this, liveStatusManager, updateChecker), this);
        getServer().getPluginManager().registerEvents(
            new GuiListener(this, guiManager), this);
    }

    // Getters for other classes
    @NotNull
    public static GoLive getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Plugin instance is not available yet!");
        }
        return instance;
    }

    @NotNull
    public DatabaseManager getDatabase() {
        if (database == null) {
            throw new IllegalStateException("Database is not initialized yet!");
        }
        return database;
    }

    @NotNull
    public MessageManager getMessageManager() {
        if (messageManager == null) {
            throw new IllegalStateException("MessageManager is not initialized yet!");
        }
        return messageManager;
    }

    @NotNull
    public ActionBarManager getActionBarManager() {
        if (actionBarManager == null) {
            throw new IllegalStateException("ActionBarManager is not initialized yet!");
        }
        return actionBarManager;
    }

    @NotNull
    public LiveStatusManager getLiveStatusManager() {
        if (liveStatusManager == null) {
            throw new IllegalStateException("LiveStatusManager is not initialized yet!");
        }
        return liveStatusManager;
    }

    @NotNull
    public GuiManager getGuiManager() {
        if (guiManager == null) {
            throw new IllegalStateException("GuiManager is not initialized yet!");
        }
        return guiManager;
    }

    @NotNull
    public VaultHook getVaultHook() {
        if (vaultHook == null) {
            throw new IllegalStateException("VaultHook is not initialized yet!");
        }
        return vaultHook;
    }

    @NotNull
    public PlaceholderHook getPlaceholderHook() {
        if (placeholderHook == null) {
            throw new IllegalStateException("PlaceholderHook is not initialized yet!");
        }
        return placeholderHook;
    }

    @NotNull
    public UpdateChecker getUpdateChecker() {
        if (updateChecker == null) {
            throw new IllegalStateException("UpdateChecker is not initialized yet!");
        }
        return updateChecker;
    }
    
    @NotNull
    public HealthChecker getHealthChecker() {
        if (healthChecker == null) {
            throw new IllegalStateException("HealthChecker is not initialized yet!");
        }
        return healthChecker;
    }
}
