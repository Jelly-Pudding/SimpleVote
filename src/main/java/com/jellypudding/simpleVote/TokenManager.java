package com.jellypudding.simpleVote;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player vote tokens with per-player file storage
 */
public class TokenManager {
    private final SimpleVote plugin;
    private final File tokensDirectory;
    private final Map<UUID, Integer> cachedTokens = new ConcurrentHashMap<>();

    public TokenManager(SimpleVote plugin) {
        this.plugin = plugin;
        this.tokensDirectory = new File(plugin.getDataFolder(), "tokens");
        
        // Create tokens directory if it doesn't exist
        if (!tokensDirectory.exists() && !tokensDirectory.mkdirs()) {
            plugin.getLogger().severe("Failed to create tokens directory!");
        }
    }

    /**
     * Get the file for a player's token data
     * 
     * @param playerUUID The player's UUID
     * @return The file for the player's token data
     */
    private File getPlayerTokenFile(UUID playerUUID) {
        return new File(tokensDirectory, playerUUID.toString() + ".yml");
    }

    /**
     * Load a player's tokens from their file
     * 
     * @param playerUUID The player's UUID
     * @return The number of tokens the player has
     */
    private int loadPlayerTokens(UUID playerUUID) {
        File playerFile = getPlayerTokenFile(playerUUID);
        if (playerFile.exists()) {
            FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
            return playerConfig.getInt("tokens", 0);
        }
        return 0;
    }

    /**
     * Get the number of tokens a player has
     * 
     * @param playerUUID The player's UUID
     * @return The number of tokens the player has
     */
    public int getTokens(UUID playerUUID) {
        return cachedTokens.computeIfAbsent(playerUUID, this::loadPlayerTokens);
    }

    /**
     * Add tokens to a player
     * 
     * @param playerUUID The player's UUID
     * @param amount The amount of tokens to add
     */
    public void addTokens(UUID playerUUID, int amount) {
        int currentTokens = getTokens(playerUUID);
        setTokens(playerUUID, currentTokens + amount);
    }

    /**
     * Remove tokens from a player
     * 
     * @param playerUUID The player's UUID
     * @param amount The amount of tokens to remove
     * @return True if the player had enough tokens, false otherwise
     */
    public boolean removeTokens(UUID playerUUID, int amount) {
        int currentTokens = getTokens(playerUUID);
        if (currentTokens >= amount) {
            setTokens(playerUUID, currentTokens - amount);
            return true;
        }
        return false;
    }

    /**
     * Set a player's tokens without saving to disk
     * 
     * @param playerUUID The player's UUID
     * @param amount The new amount of tokens
     */
    public void setTokens(UUID playerUUID, int amount) {
        cachedTokens.put(playerUUID, amount);
    }

    /**
     * Save tokens for a specific player to disk
     * 
     * @param playerUUID The player's UUID
     */
    public void savePlayerTokens(UUID playerUUID) {
        Integer amount = cachedTokens.get(playerUUID);
        if (amount != null) {
            File playerFile = getPlayerTokenFile(playerUUID);
            FileConfiguration playerConfig = new YamlConfiguration();
            playerConfig.set("tokens", amount);
            
            try {
                playerConfig.save(playerFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save token file for player " + playerUUID + ": " + e.getMessage());
            }
        }
    }

    /**
     * Save all cached tokens to files
     * This is called on server shutdown
     */
    public void saveTokens() {
        plugin.getLogger().info("Saving tokens for " + cachedTokens.size() + " players...");
        for (Map.Entry<UUID, Integer> entry : cachedTokens.entrySet()) {
            File playerFile = getPlayerTokenFile(entry.getKey());
            FileConfiguration playerConfig = new YamlConfiguration();
            playerConfig.set("tokens", entry.getValue());
            
            try {
                playerConfig.save(playerFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save token file for player " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }
} 