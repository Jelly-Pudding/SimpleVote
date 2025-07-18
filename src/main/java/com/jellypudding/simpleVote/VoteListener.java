package com.jellypudding.simpleVote;

import com.jellypudding.simpleVote.events.VoteEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Listens for votes and rewards players with tokens
 */
public class VoteListener implements Listener {
    private final SimpleVote plugin;
    private final TokenManager tokenManager;
    private final int tokensPerVote;

    public VoteListener(SimpleVote plugin, TokenManager tokenManager, int tokensPerVote) {
        this.plugin = plugin;
        this.tokenManager = tokenManager;
        this.tokensPerVote = tokensPerVote;
    }

    /**
     * Handles vote events
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVote(VoteEvent event) {
        String playerName = event.getPlayerName();
        String serviceName = event.getServiceName();
        
        plugin.getLogger().info("Received vote from " + playerName + " through " + serviceName);

        // Find the player (might be offline)
        Player player = Bukkit.getPlayer(playerName);
        UUID playerUUID;
        
        if (player != null) {
            // Player is online
            playerUUID = player.getUniqueId();
            
            // Add tokens
            tokenManager.addTokens(playerUUID, tokensPerVote);
            
            // Notify player with proper singular/plural form
            String tokenText = tokensPerVote == 1 ? "vote token" : "vote tokens";
            player.sendMessage(Component.text("Thanks for voting on " + serviceName + "! You received " 
                    + tokensPerVote + " " + tokenText + ".", NamedTextColor.GREEN));
            
            int totalTokens = tokenManager.getTokens(playerUUID);
            String totalTokenText = totalTokens == 1 ? "token" : "tokens";
            player.sendMessage(Component.text("You now have " + totalTokens + " " + totalTokenText + ".", 
                    NamedTextColor.YELLOW));
        } else {
            // Player is offline, try to find their UUID
            try {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
                if (offlinePlayer.hasPlayedBefore()) {
                    playerUUID = offlinePlayer.getUniqueId();

                    // Add tokens
                    tokenManager.addTokens(playerUUID, tokensPerVote);
                    plugin.getLogger().info("Added " + tokensPerVote + " tokens to offline player " + playerName);
                } else {
                    plugin.getLogger().warning("Vote received for unknown player: " + playerName);
                    return;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Vote received for invalid player name: " + playerName);
                return;
            }
        }

        // Broadcast the vote if enabled
        if (plugin.getConfigManager().getBroadcastVotes()) {
            // Get player's display name if online, otherwise use regular name
            Component playerComponent;
            if (player != null) {
                playerComponent = player.displayName();
            } else {
                playerComponent = Component.text(playerName);
            }
            
            // Create vote message with player's display name and service
            Component voteMessage = Component.empty()
                .append(playerComponent)
                .append(Component.text(" voted for the server on ", NamedTextColor.GREEN))
                .append(Component.text(serviceName, NamedTextColor.YELLOW));
            
            Bukkit.getServer().sendMessage(voteMessage);
        }
        
    }
}