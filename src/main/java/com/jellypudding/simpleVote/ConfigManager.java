package com.jellypudding.simpleVote;

import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final SimpleVote plugin;
    private FileConfiguration config;
    
    private int tokensPerVote;
    private boolean broadcastVotes;
    private String voteMessage;
    
    public ConfigManager(SimpleVote plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    private void loadConfig() {
        // Save default config if it doesn't exist
        plugin.saveDefaultConfig();
        
        // Load config
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        // Set default values if they don't exist
        if (!config.contains("tokens-per-vote")) {
            config.set("tokens-per-vote", 1);
        }
        
        if (!config.contains("broadcast-votes")) {
            config.set("broadcast-votes", true);
        }
        
        if (!config.contains("vote-message")) {
            config.set("vote-message", "&6{player} &avoted for the server on &e{service} &aand received &e{tokens} &atokens!");
        }
        
        // Save config
        plugin.saveConfig();
        
        // Load values
        tokensPerVote = config.getInt("tokens-per-vote");
        broadcastVotes = config.getBoolean("broadcast-votes");
        voteMessage = config.getString("vote-message");
    }
    
    public int getTokensPerVote() {
        return tokensPerVote;
    }
    
    public boolean getBroadcastVotes() {
        return broadcastVotes;
    }
    
    public String getVoteMessage() {
        return voteMessage;
    }
} 