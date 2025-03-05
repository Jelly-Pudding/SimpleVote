package com.jellypudding.simpleVote;

import com.jellypudding.simpleVote.commands.KeyCommand;
import com.jellypudding.simpleVote.commands.TokenCommand;
import com.jellypudding.simpleVote.commands.TokenTabCompleter;
import com.jellypudding.simpleVote.commands.VoteSitesCommand;
import com.jellypudding.simpleVote.votifier.VotifierManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleVote extends JavaPlugin {
    private ConfigManager configManager;
    private TokenManager tokenManager;
    private VotifierManager votifierManager;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        
        // Initialize managers
        configManager = new ConfigManager(this);
        tokenManager = new TokenManager(this);
        
        // Register vote listener
        VoteListener voteListener = new VoteListener(this, tokenManager, configManager.getTokensPerVote());
        getServer().getPluginManager().registerEvents(voteListener, this);
        
        // Initialize built-in Votifier functionality (directly receives votes from voting websites)
        votifierManager = new VotifierManager(this);
        votifierManager.initialize();
        
        // Register commands
        registerCommands();
        
        // Startup message
        getLogger().info("SimpleVote has been enabled!");
        getLogger().info("Set up " + configManager.getTokensPerVote() + " tokens per vote");
        
        // Add debug flag to config if it doesn't exist
        if (!getConfig().contains("debug-mode")) {
            getConfig().set("debug-mode", false);
            saveConfig();
        }
    }

    @Override
    public void onDisable() {
        // Shutdown Votifier
        if (votifierManager != null) {
            votifierManager.shutdown();
        }
        
        // Save token data
        if (tokenManager != null) {
            tokenManager.saveTokens();
        }
        
        getLogger().info("SimpleVote has been disabled!");
    }
    
    private void registerCommands() {
        // Register tokens command
        PluginCommand tokensCommand = getCommand("tokens");
        if (tokensCommand != null) {
            TokenCommand executor = new TokenCommand(this, tokenManager);
            TokenTabCompleter tabCompleter = new TokenTabCompleter();
            
            tokensCommand.setExecutor(executor);
            tokensCommand.setTabCompleter(tabCompleter);
        } else {
            getLogger().severe("Failed to register tokens command!");
        }
        
        // Register votekey command
        PluginCommand keyCommand = getCommand("votekey");
        if (keyCommand != null) {
            keyCommand.setExecutor(new KeyCommand(this, votifierManager));
        } else {
            getLogger().severe("Failed to register votekey command!");
        }
        
        // Register votesites command
        PluginCommand voteSitesCommand = getCommand("votesites");
        if (voteSitesCommand != null) {
            voteSitesCommand.setExecutor(new VoteSitesCommand(this));
        } else {
            getLogger().severe("Failed to register votesites command!");
        }
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }

}
