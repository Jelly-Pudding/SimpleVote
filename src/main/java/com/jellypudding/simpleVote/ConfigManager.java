package com.jellypudding.simpleVote;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final SimpleVote plugin;
    
    private int tokensPerVote;
    private boolean broadcastVotes;
    private List<Map<String, String>> votingSites;
    
    public ConfigManager(SimpleVote plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    private void loadConfig() {
        // Save default config if it doesn't exist
        plugin.saveDefaultConfig();
        
        // Load config
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        
        // Set default values if they don't exist
        if (!config.contains("tokens-per-vote")) {
            config.set("tokens-per-vote", 1);
        }
        
        if (!config.contains("broadcast-votes")) {
            config.set("broadcast-votes", true);
        }
        
        // Set up default voting sites if they don't exist
        if (!config.contains("voting-sites")) {
            List<Map<String, Object>> defaultSites = new ArrayList<>();
            
            Map<String, Object> site1 = new HashMap<>();
            site1.put("name", "PlanetMinecraft");
            site1.put("url", "https://planetminecraft.com/server/your-server-name/vote/");
            defaultSites.add(site1);
            
            Map<String, Object> site2 = new HashMap<>();
            site2.put("name", "Minecraft Server List");
            site2.put("url", "https://minecraft-server-list.com/server/your-server-id/vote/");
            defaultSites.add(site2);
            
            config.set("voting-sites", defaultSites);
        }
        
        // Save config
        plugin.saveConfig();
        
        // Load values
        tokensPerVote = config.getInt("tokens-per-vote");
        broadcastVotes = config.getBoolean("broadcast-votes");
        
        // Load voting sites
        votingSites = new ArrayList<>();
        List<?> sitesList = config.getList("voting-sites");
        if (sitesList != null) {
            for (Object siteObj : sitesList) {
                if (siteObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> siteMap = (Map<String, Object>) siteObj;
                    Map<String, String> site = new HashMap<>();
                    
                    if (siteMap.containsKey("name") && siteMap.containsKey("url")) {
                        site.put("name", siteMap.get("name").toString());
                        site.put("url", siteMap.get("url").toString());
                        votingSites.add(site);
                    }
                }
            }
        }
    }
    
    public int getTokensPerVote() {
        return tokensPerVote;
    }
    
    public boolean getBroadcastVotes() {
        return broadcastVotes;
    }
    
    public List<Map<String, String>> getVotingSites() {
        return votingSites;
    }
} 