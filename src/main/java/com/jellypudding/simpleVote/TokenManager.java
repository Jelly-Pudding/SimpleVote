package com.jellypudding.simpleVote;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages player vote tokens using an SQLite database.
 */
public class TokenManager {
    private final SimpleVote plugin;
    private final Map<UUID, Integer> cachedTokens = new ConcurrentHashMap<>();
    private Connection connection;
    private final String databaseUrl;

    public TokenManager(SimpleVote plugin) {
        this.plugin = plugin;
        File databaseFile = new File(plugin.getDataFolder(), "tokens.db");
        this.databaseUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

        // Ensure plugin data folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        try {
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            // Establish connection
            this.connection = DriverManager.getConnection(databaseUrl);
            initializeDatabase();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to SQLite database: " + e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found. Make sure it's included in your plugin JAR.", e);
        }
    }

    /**
     * Creates the necessary database table if it doesn't exist.
     */
    private void initializeDatabase() {
        // Use try-with-resources for automatic closing of the statement
        try (Statement statement = connection.createStatement()) {
            // Removed NOT NULL constraint for tokens to avoid potential issues with default values or insertion
            statement.execute("CREATE TABLE IF NOT EXISTS player_tokens (" +
                              "uuid TEXT PRIMARY KEY, " +
                              "tokens INTEGER DEFAULT 0" + // Set default to 0
                              ");");
            plugin.getLogger().info("Database table 'player_tokens' initialised successfully.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create database table: " + e.getMessage(), e);
        }
    }

    /**
     * Close the database connection. Should be called on plugin disable.
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    plugin.getLogger().info("Database connection closed.");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error closing database connection: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Load a player's tokens from the database.
     * This is called internally by computeIfAbsent when a player's tokens aren't cached.
     *
     * @param playerUUID The player's UUID
     * @return The number of tokens the player has
     */
    private int loadPlayerTokens(UUID playerUUID) {
        String sqlSelect = "SELECT tokens FROM player_tokens WHERE uuid = ?";
        try (PreparedStatement pstmtSelect = connection.prepareStatement(sqlSelect)) {
            pstmtSelect.setString(1, playerUUID.toString());
            try (ResultSet rs = pstmtSelect.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("tokens");
                } else {
                    // Player not in DB yet. Insert a new record with 0 tokens.
                    // DO NOT call setTokens() here to avoid recursive update on the cache.
                    String sqlInsert = "INSERT OR IGNORE INTO player_tokens (uuid, tokens) VALUES (?, ?)";
                    try (PreparedStatement pstmtInsert = connection.prepareStatement(sqlInsert)) {
                        pstmtInsert.setString(1, playerUUID.toString());
                        pstmtInsert.setInt(2, 0);
                        pstmtInsert.executeUpdate();
                        // The computeIfAbsent call in getTokens will handle caching this '0'
                    } catch (SQLException insertEx) {
                        plugin.getLogger().log(Level.SEVERE, "Could not insert initial token record for player " + playerUUID + ": " + insertEx.getMessage(), insertEx);
                        // Still return 0, but log the error
                    }
                    return 0;
                }
            }
        } catch (SQLException selectEx) {
            plugin.getLogger().log(Level.SEVERE, "Could not load tokens for player " + playerUUID + ": " + selectEx.getMessage(), selectEx);
            return 0; // Return default value on error
        }
    }

    /**
     * Get the number of tokens a player has (from cache or database).
     *
     * @param playerUUID The player's UUID
     * @return The number of tokens the player has
     */
    public int getTokens(UUID playerUUID) {
        // Attempt to get from cache, if not present, load from DB (loadPlayerTokens)
        return cachedTokens.computeIfAbsent(playerUUID, this::loadPlayerTokens);
    }

    /**
     * Add tokens to a player. Updates cache and database.
     *
     * @param playerUUID The player's UUID
     * @param amount The amount of tokens to add
     */
    public void addTokens(UUID playerUUID, int amount) {
        int currentTokens = getTokens(playerUUID); // Ensures player is loaded if not already
        setTokens(playerUUID, currentTokens + amount);
    }

    /**
     * Remove tokens from a player. Updates cache and database.
     *
     * @param playerUUID The player's UUID
     * @param amount The amount of tokens to remove
     * @return True if the player had enough tokens, false otherwise
     */
    public boolean removeTokens(UUID playerUUID, int amount) {
        int currentTokens = getTokens(playerUUID); // Ensures player is loaded
        if (currentTokens >= amount) {
            setTokens(playerUUID, currentTokens - amount);
            return true;
        }
        return false;
    }

    /**
     * Set a player's tokens. Updates cache and saves to the database immediately.
     *
     * @param playerUUID The player's UUID
     * @param amount The new amount of tokens
     */
    public void setTokens(UUID playerUUID, int amount) {
        if (amount < 0) amount = 0; // Ensure tokens don't go below zero

        // Update cache first for responsiveness
        cachedTokens.put(playerUUID, amount);

        // Use INSERT OR REPLACE (or UPSERT) to handle both new and existing players
        // This is more efficient than separate SELECT then INSERT/UPDATE
        String sql = "INSERT OR REPLACE INTO player_tokens (uuid, tokens) VALUES (?, ?)";

        // Use try-with-resources for automatic closing of the PreparedStatement
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.setInt(2, amount);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not set tokens for player " + playerUUID + ": " + e.getMessage(), e);
            // Consider how to handle this error - maybe remove from cache or retry?
            // For now, we just log the error. The cache will have the new value,
            // but the database might be out of sync until the next successful write.
        }
    }
} 