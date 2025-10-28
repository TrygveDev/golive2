package dev.trygve.golive.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.trygve.golive.GoLive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract database manager interface
 */
public abstract class DatabaseManager {
    
    protected final GoLive plugin;
    protected HikariDataSource dataSource;
    protected boolean connected = false;
    
    /**
     * Create a new DatabaseManager
     * 
     * @param plugin The plugin instance
     */
    public DatabaseManager(@NotNull GoLive plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialize the database connection
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    @NotNull
    public abstract CompletableFuture<Boolean> initialize();
    
    /**
     * Close the database connection
     */
    public abstract void close();
    
    /**
     * Check if the database is connected
     * 
     * @return True if connected
     */
    public boolean isConnected() {
        return connected && dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * Get a database connection
     * 
     * @return The database connection
     * @throws SQLException If connection fails
     */
    @NotNull
    public Connection getConnection() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("Database is not connected");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Create the database tables
     * 
     * @return CompletableFuture that completes when tables are created
     */
    @NotNull
    public abstract CompletableFuture<Boolean> createTables();
    
    /**
     * Create a player record in the database
     * 
     * @param uuid The player's UUID
     * @param name The player's name
     * @return CompletableFuture that completes when the player is created
     */
    @NotNull
    public abstract CompletableFuture<Boolean> createPlayer(@NotNull UUID uuid, @NotNull String name);
    
    /**
     * Check if a player exists in the database
     * 
     * @param uuid The player's UUID
     * @return CompletableFuture that completes with true if player exists
     */
    @NotNull
    public abstract CompletableFuture<Boolean> playerExists(@NotNull UUID uuid);
    
    /**
     * Set a player's live status
     * 
     * @param uuid The player's UUID
     * @param isLive Whether the player is live
     * @return CompletableFuture that completes when status is set
     */
    @NotNull
    public abstract CompletableFuture<Boolean> setLiveStatus(@NotNull UUID uuid, boolean isLive);
    
    /**
     * Get a player's live status
     * 
     * @param uuid The player's UUID
     * @return CompletableFuture that completes with true if player is live
     */
    @NotNull
    public abstract CompletableFuture<Boolean> isLive(@NotNull UUID uuid);
    
    /**
     * Set a player's stream link
     * 
     * @param uuid The player's UUID
     * @param streamLink The stream link
     * @return CompletableFuture that completes when link is set
     */
    @NotNull
    public abstract CompletableFuture<Boolean> setStreamLink(@NotNull UUID uuid, @NotNull String streamLink);
    
    /**
     * Get a player's stream link
     * 
     * @param uuid The player's UUID
     * @return CompletableFuture that completes with the stream link
     */
    @NotNull
    public abstract CompletableFuture<String> getStreamLink(@NotNull UUID uuid);
    
    /**
     * Get the count of live players
     * 
     * @return CompletableFuture that completes with the count
     */
    @NotNull
    public abstract CompletableFuture<Integer> getLivePlayerCount();
    
    /**
     * Get all live players
     * 
     * @return CompletableFuture that completes with array of live player UUIDs
     */
    @NotNull
    public abstract CompletableFuture<UUID[]> getLivePlayers();
    
    /**
     * Get all players with stream links (regardless of live status)
     * 
     * @return CompletableFuture that completes with array of player UUIDs who have stream links
     */
    @NotNull
    public abstract CompletableFuture<UUID[]> getPlayersWithStreamLinks();
    
    /**
     * Update a player's last seen timestamp
     * 
     * @param uuid The player's UUID
     * @return CompletableFuture that completes when timestamp is updated
     */
    @NotNull
    public abstract CompletableFuture<Boolean> updateLastSeen(@NotNull UUID uuid);
    
    /**
     * Clean up old records (players who haven't been seen in a while)
     * 
     * @param daysOld Number of days old to consider for cleanup
     * @return CompletableFuture that completes when cleanup is done
     */
    @NotNull
    public abstract CompletableFuture<Integer> cleanupOldRecords(int daysOld);
    
    /**
     * Get the database type
     * 
     * @return The database type
     */
    @NotNull
    public abstract String getDatabaseType();
    
    /**
     * Create a HikariConfig for the database
     * 
     * @return The HikariConfig
     */
    @NotNull
    protected abstract HikariConfig createHikariConfig();
    
    /**
     * Get the table name for the database
     * 
     * @return The table name
     */
    @NotNull
    protected String getTableName() {
        return "golive_players";
    }
    
    /**
     * Get the SQL for creating the table
     * 
     * @return The CREATE TABLE SQL
     */
    @NotNull
    protected String getCreateTableSQL() {
        return "CREATE TABLE IF NOT EXISTS " + getTableName() + " (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(16) NOT NULL, " +
                "is_live BOOLEAN DEFAULT FALSE, " +
                "stream_link TEXT, " +
                "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
    }
    
    /**
     * Execute a SQL statement asynchronously
     * 
     * @param sql The SQL statement
     * @param params The parameters
     * @return CompletableFuture that completes when statement is executed
     */
    @NotNull
    protected CompletableFuture<Boolean> executeAsync(@NotNull String sql, @Nullable Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            Connection connection = null;
            try {
                connection = getConnection();
                try (var statement = connection.prepareStatement(sql)) {
                    if (params != null) {
                        for (int i = 0; i < params.length; i++) {
                            statement.setObject(i + 1, params[i]);
                        }
                    }
                    statement.execute();
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Database error executing SQL: " + sql);
                plugin.getLogger().severe("Error details: " + e.getMessage());
                plugin.getLogger().severe("SQL State: " + e.getSQLState());
                plugin.getLogger().severe("Error Code: " + e.getErrorCode());
                
                // Log parameters for debugging (but not passwords)
                if (params != null && params.length > 0) {
                    StringBuilder paramLog = new StringBuilder("Parameters: ");
                    for (int i = 0; i < params.length; i++) {
                        if (params[i] != null && params[i].toString().toLowerCase().contains("password")) {
                            paramLog.append("[REDACTED]");
                        } else {
                            paramLog.append(params[i]);
                        }
                        if (i < params.length - 1) {
                            paramLog.append(", ");
                        }
                    }
                    plugin.getLogger().info(paramLog.toString());
                }
                
                e.printStackTrace();
                return false;
            } catch (Exception e) {
                plugin.getLogger().severe("Unexpected error executing SQL: " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
                    }
                }
            }
        });
    }
}
