package com.jellypudding.simpleVote.votifier;

import com.jellypudding.simpleVote.SimpleVote;

import java.util.UUID;

public class VotifierManager {
    private final SimpleVote plugin;
    private VotifierServer server;
    private RSAUtil rsaUtil;
    private boolean enabled;
    
    public VotifierManager(SimpleVote plugin) {
        this.plugin = plugin;
    }

    public void initialise() {
        // Check if votifier is enabled in config
        if (!plugin.getConfig().getBoolean("votifier.enabled", true)) {
            plugin.getLogger().info("Votifier functionality is disabled in config");
            return;
        }

        rsaUtil = new RSAUtil(plugin.getLogger());
        if (!rsaUtil.initialise(plugin.getDataFolder())) {
            plugin.getLogger().severe("Failed to initialise RSA utilities. Votifier functionality disabled.");
            return;
        }

        // Get port from config
        int port = plugin.getConfig().getInt("votifier.port", 8192);
        boolean debug = plugin.getConfig().getBoolean("debug-mode", false);

        // Load or auto-generate the v2 HMAC token
        String token = plugin.getConfig().getString("votifier.token", "");
        if (token == null || token.isEmpty()) {
            token = UUID.randomUUID().toString().replace("-", "");
            plugin.getConfig().set("votifier.token", token);
            plugin.saveConfig();
            plugin.getLogger().info("Generated new Votifier v2 token. Copy it into your voting site's NuVotifier token field.");
        }

        // Set debug mode for RSA operations
        rsaUtil.setDebug(debug);
        
        // Save the public key into config.yml so it is easy to copy
        String publicKey = rsaUtil.getV1FormattedPublicKey();
        plugin.getConfig().set("votifier.public-key", publicKey);
        plugin.saveConfig();

        // Start the server
        try {
            server = new VotifierServer(plugin, port, debug, rsaUtil, token);
            server.start();
            enabled = true;

            plugin.getLogger().info("=== SimpleVote Votifier Ready ===");
            plugin.getLogger().info("Listening on port " + port);
            plugin.getLogger().info("When registering on voting sites, fill in whichever fields the site shows:");
            plugin.getLogger().info("  Public Key: run /votekey key, or see 'votifier.public-key' in config.yml");
            plugin.getLogger().info("  Token:      run /votekey token, or see 'votifier.token' in config.yml");
            plugin.getLogger().info("Some sites ask for one, some ask for both.");
            plugin.getLogger().info("=================================");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start vote listener: " + e.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error details:", e);
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