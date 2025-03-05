package com.jellypudding.simpleVote;

import com.jellypudding.simpleVote.commands.KeyCommand;
import com.jellypudding.simpleVote.commands.TokenCommand;
import com.jellypudding.simpleVote.commands.TokenTabCompleter;
import com.jellypudding.simpleVote.votifier.VotifierManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
        
        // Add test vote command (for development only)
        PluginCommand testVoteCommand = getCommand("testvote");
        if (testVoteCommand != null) {
            testVoteCommand.setExecutor((sender, command, label, args) -> {
                if (sender instanceof org.bukkit.entity.Player) {
                    org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
                    
                    // Create and call a vote event
                    com.jellypudding.simpleVote.events.VoteEvent voteEvent = new com.jellypudding.simpleVote.events.VoteEvent(
                            player.getName(),
                            "TestService",
                            "127.0.0.1",
                            java.time.Instant.now().toString()
                    );
                    
                    // Call the event
                    org.bukkit.Bukkit.getPluginManager().callEvent(voteEvent);
                    player.sendMessage(net.kyori.adventure.text.Component.text("Test vote simulated!", 
                            net.kyori.adventure.text.format.NamedTextColor.GREEN));
                    return true;
                }
                return false;
            });
        }
    }
    
    public TokenManager getTokenManager() {
        return tokenManager;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public VotifierManager getVotifierManager() {
        return votifierManager;
    }
    
    /**
     * Utility method to colorize strings with color codes
     * 
     * @param message The message to colorize
     * @return The colorized message as a Component
     */
    public static Component colorize(String message) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }
    
    /**
     * Utility method to get a colored component from a string
     * 
     * @param message The message
     * @param color The color to use
     * @return The colored component
     */
    public static Component color(String message, TextColor color) {
        return Component.text(message, color);
    }
}
