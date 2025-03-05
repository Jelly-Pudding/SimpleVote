package com.jellypudding.simpleVote;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TokenManager {
    private final SimpleVote plugin;
    private final File tokenFile;
    private FileConfiguration tokenConfig;
    private final Map<UUID, Integer> playerTokens = new HashMap<>();

    public TokenManager(SimpleVote plugin) {
        this.plugin = plugin;
        this.tokenFile = new File(plugin.getDataFolder(), "tokens.yml");
        loadTokens();
    }

    private void loadTokens() {
        if (!tokenFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                tokenFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create tokens.yml file: " + e.getMessage());
            }
        }
        
        tokenConfig = YamlConfiguration.loadConfiguration(tokenFile);
        
        // Load tokens into memory
        if (tokenConfig.contains("tokens")) {
            for (String uuidString : tokenConfig.getConfigurationSection("tokens").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    int amount = tokenConfig.getInt("tokens." + uuidString);
                    playerTokens.put(uuid, amount);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in tokens.yml: " + uuidString);
                }
            }
        }
    }

    public void saveTokens() {
        for (Map.Entry<UUID, Integer> entry : playerTokens.entrySet()) {
            tokenConfig.set("tokens." + entry.getKey().toString(), entry.getValue());
        }
        
        try {
            tokenConfig.save(tokenFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save tokens.yml file: " + e.getMessage());
        }
    }

    public int getTokens(UUID playerUUID) {
        return playerTokens.getOrDefault(playerUUID, 0);
    }

    public void addTokens(UUID playerUUID, int amount) {
        int currentTokens = getTokens(playerUUID);
        playerTokens.put(playerUUID, currentTokens + amount);
    }

    public boolean removeTokens(UUID playerUUID, int amount) {
        int currentTokens = getTokens(playerUUID);
        if (currentTokens >= amount) {
            playerTokens.put(playerUUID, currentTokens - amount);
            return true;
        }
        return false;
    }

    public void setTokens(UUID playerUUID, int amount) {
        playerTokens.put(playerUUID, amount);
    }
} 