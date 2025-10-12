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
import dev.trygve.golive.managers.LiveStatusManager;
import dev.trygve.golive.managers.MessageManager;
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
    private LiveStatusManager liveStatusManager;
    private GuiManager guiManager;
    
    // Hooks
    private VaultHook vaultHook;
    private PlaceholderHook placeholderHook;
    
    // Utilities
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        instance = this;
        
        getLogger().info("==========================================");
        getLogger().info("     GoLive v2.0 - Modern Edition       ");
        getLogger().info("==========================================");
        
        // Initialize plugin asynchronously
        initializePlugin().thenAccept(success -> {
            if (success) {
                getLogger().info("GoLive v2.0 has been enabled successfully!");
                getLogger().info("Available commands: /golive, /live, /offline");
                getLogger().info("==========================================");
            } else {
                getLogger().severe("Failed to initialize GoLive v2.0!");
                getServer().getPluginManager().disablePlugin(this);
            }
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("GoLive v2.0 is shutting down...");
        
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
        
        getLogger().info("GoLive v2.0 has been disabled!");
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
                
                // Initialize live status manager
                liveStatusManager = new LiveStatusManager(this, database, vaultHook, messageManager);
                if (!liveStatusManager.initialize().join()) {
                    return false;
                }
                
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
                
                // Initialize metrics
                if (getConfig().getBoolean("metrics.enabled", true)) {
                    try {
                        new MetricsManager(this, 11803);
                        getLogger().info("Metrics enabled successfully!");
                    } catch (Exception e) {
                        getLogger().warning("Failed to initialize metrics: " + e.getMessage());
                        getLogger().info("Continuing without metrics...");
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
            
            getLogger().info("Database initialized successfully! Type: " + database.getDatabaseType());
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
}
