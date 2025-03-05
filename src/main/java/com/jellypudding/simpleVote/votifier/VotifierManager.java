package com.jellypudding.simpleVote.votifier;

import com.jellypudding.simpleVote.SimpleVote;

/**
 * Manager for our built-in Votifier functionality.
 * This handles receiving votes directly from voting websites by implementing the Votifier protocol.
 */
public class VotifierManager {
    private final SimpleVote plugin;
    private VotifierServer server;
    private RSAUtil rsaUtil;
    private boolean enabled;
    
    public VotifierManager(SimpleVote plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialize the votifier functionality
     */
    public void initialize() {
        // Check if votifier is enabled in config
        if (!plugin.getConfig().getBoolean("votifier.enabled", true)) {
            plugin.getLogger().info("Votifier functionality is disabled in config");
            return;
        }
        
        // Initialize RSA utilities
        rsaUtil = new RSAUtil(plugin.getLogger());
        if (!rsaUtil.initialize(plugin.getDataFolder())) {
            plugin.getLogger().severe("Failed to initialize RSA utilities. Votifier functionality disabled.");
            return;
        }
        
        // Get port from config
        int port = plugin.getConfig().getInt("votifier.port", 8192);
        boolean debug = plugin.getConfig().getBoolean("votifier.debug", false);
        
        // Start the server
        try {
            server = new VotifierServer(plugin, port, debug, rsaUtil);
            server.start();
            enabled = true;
            
            // Log public key for server administrator
            plugin.getLogger().info("Vote listener started. Use this public key when registering your server on voting sites:");
            plugin.getLogger().info(rsaUtil.getFormattedPublicKey());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start vote listener: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Shutdown the votifier functionality
     */
    public void shutdown() {
        if (server != null) {
            server.shutdown();
            server = null;
        }
    }
    
    /**
     * Check if votifier functionality is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get the RSA utility
     */
    public RSAUtil getRsaUtil() {
        return rsaUtil;
    }
} 