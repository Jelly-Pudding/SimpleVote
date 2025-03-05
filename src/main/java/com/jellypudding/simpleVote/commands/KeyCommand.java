package com.jellypudding.simpleVote.commands;

import com.jellypudding.simpleVote.SimpleVote;
import com.jellypudding.simpleVote.votifier.VotifierManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Command that displays the public key for Votifier
 */
public class KeyCommand implements CommandExecutor {
    private final SimpleVote plugin;
    private final VotifierManager votifierManager;
    
    public KeyCommand(SimpleVote plugin, VotifierManager votifierManager) {
        this.plugin = plugin;
        this.votifierManager = votifierManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("simplevote.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        if (!votifierManager.isEnabled()) {
            sender.sendMessage(Component.text("Votifier functionality is not enabled.", NamedTextColor.RED));
            return true;
        }
        
        sender.sendMessage(Component.text("=== SimpleVote Public Key ===", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Use this key when registering on voting sites:", NamedTextColor.GREEN));
        sender.sendMessage(Component.text(votifierManager.getRsaUtil().getFormattedPublicKey(), NamedTextColor.WHITE));
        
        // Port info
        int port = plugin.getConfig().getInt("votifier.port", 8192);
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Server Information:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Port: ", NamedTextColor.GREEN).append(Component.text(port, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Make sure this port is open and forwarded to your server.", NamedTextColor.GREEN));
        
        return true;
    }
} 