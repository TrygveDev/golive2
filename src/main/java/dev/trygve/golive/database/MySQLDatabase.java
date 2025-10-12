package dev.trygve.golive.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.trygve.golive.GoLive;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MySQL database implementation
 */
public class MySQLDatabase extends DatabaseManager {
    
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean ssl;
    private final int poolSize;
    
    /**
     * Create a new MySQLDatabase
     * 
     * @param plugin The plugin instance
     */
    public MySQLDatabase(@NotNull GoLive plugin) {
        super(plugin);
        this.host = plugin.getConfig().getString("database.mysql.host", "localhost");
        this.port = plugin.getConfig().getInt("database.mysql.port", 3306);
        this.database = plugin.getConfig().getString("database.mysql.database", "golive");
        this.username = plugin.getConfig().getString("database.mysql.username", "root");
        this.password = plugin.getConfig().getString("database.mysql.password", "password");
        this.ssl = plugin.getConfig().getBoolean("database.mysql.ssl", false);
        this.poolSize = plugin.getConfig().getInt("database.mysql.pool-size", 10);
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create HikariConfig
                HikariConfig config = createHikariConfig();
                dataSource = new HikariDataSource(config);
                
                // Test connection
                try (Connection connection = dataSource.getConnection()) {
                    connected = true;
                    plugin.getLogger().info("MySQL database connected successfully!");
                    return true;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize MySQL database: " + e.getMessage());
                e.printStackTrace();
                connected = false;
                return false;
            }
        });
    }
    
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            connected = false;
            plugin.getLogger().info("MySQL database connection closed.");
        }
    }
    
    @Override
    @NotNull
    protected HikariConfig createHikariConfig() {
        HikariConfig config = new HikariConfig();
        
        // Build JDBC URL
        StringBuilder jdbcUrl = new StringBuilder("jdbc:mysql://")
                .append(host)
                .append(":")
                .append(port)
                .append("/")
                .append(database)
                .append("?useSSL=")
                .append(ssl)
                .append("&allowPublicKeyRetrieval=true")
                .append("&serverTimezone=UTC");
        
        config.setJdbcUrl(jdbcUrl.toString());
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        config.setPoolName("GoLive-MySQL");
        
        // MySQL specific settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        return config;
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> createTables() {
        return executeAsync(getCreateTableSQL());
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> createPlayer(@NotNull UUID uuid, @NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                String sql = "INSERT IGNORE INTO " + getTableName() + " (uuid, name) VALUES (?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, name);
                    statement.execute();
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create player: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> playerExists(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                String sql = "SELECT 1 FROM " + getTableName() + " WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return resultSet.next();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to check if player exists: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> setLiveStatus(@NotNull UUID uuid, boolean isLive) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                String sql = "UPDATE " + getTableName() + " SET is_live = ?, last_seen = NOW() WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setBoolean(1, isLive);
                    statement.setString(2, uuid.toString());
                    int rowsAffected = statement.executeUpdate();
                    return rowsAffected > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to set live status: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> isLive(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                String sql = "SELECT is_live FROM " + getTableName() + " WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getBoolean("is_live");
                        }
                        return false;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to check live status: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> setStreamLink(@NotNull UUID uuid, @NotNull String streamLink) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                String sql = "UPDATE " + getTableName() + " SET stream_link = ?, last_seen = NOW() WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, streamLink);
                    statement.setString(2, uuid.toString());
                    int rowsAffected = statement.executeUpdate();
                    return rowsAffected > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to set stream link: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<String> getStreamLink(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                String sql = "SELECT stream_link FROM " + getTableName() + " WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getString("stream_link");
                        }
                        return null;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get stream link: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<Integer> getLivePlayerCount() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                String sql = "SELECT COUNT(*) FROM " + getTableName() + " WHERE is_live = TRUE";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getInt(1);
                        }
                        return 0;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get live player count: " + e.getMessage());
                e.printStackTrace();
                return 0;
            }
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<UUID[]> getLivePlayers() {
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> livePlayers = new ArrayList<>();
            try (Connection connection = getConnection()) {
                String sql = "SELECT uuid FROM " + getTableName() + " WHERE is_live = TRUE";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            String uuidString = resultSet.getString("uuid");
                            try {
                                livePlayers.add(UUID.fromString(uuidString));
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Invalid UUID in database: " + uuidString);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get live players: " + e.getMessage());
                e.printStackTrace();
            }
            return livePlayers.toArray(new UUID[0]);
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<UUID[]> getPlayersWithStreamLinks() {
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> playersWithLinks = new ArrayList<>();
            try (Connection connection = getConnection()) {
                String sql = "SELECT uuid FROM " + getTableName() + " WHERE stream_link IS NOT NULL AND stream_link != ''";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            String uuidString = resultSet.getString("uuid");
                            try {
                                playersWithLinks.add(UUID.fromString(uuidString));
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Invalid UUID in database: " + uuidString);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get players with stream links: " + e.getMessage());
                e.printStackTrace();
            }
            return playersWithLinks.toArray(new UUID[0]);
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> updateLastSeen(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                String sql = "UPDATE " + getTableName() + " SET last_seen = NOW() WHERE uuid = ?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    int rowsAffected = statement.executeUpdate();
                    return rowsAffected > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update last seen: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<Integer> cleanupOldRecords(int daysOld) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                String sql = "DELETE FROM " + getTableName() + " WHERE last_seen < DATE_SUB(NOW(), INTERVAL ? DAY)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, daysOld);
                    int rowsAffected = statement.executeUpdate();
                    if (rowsAffected > 0) {
                        plugin.getLogger().info("Cleaned up " + rowsAffected + " old records from database.");
                    }
                    return rowsAffected;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to cleanup old records: " + e.getMessage());
                e.printStackTrace();
                return 0;
            }
        });
    }
    
    @Override
    @NotNull
    public String getDatabaseType() {
        return "MySQL";
    }
}
