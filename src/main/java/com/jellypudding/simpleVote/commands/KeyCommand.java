package com.jellypudding.simpleVote.commands;

import com.jellypudding.simpleVote.SimpleVote;
import com.jellypudding.simpleVote.votifier.VotifierManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class KeyCommand implements CommandExecutor {
    private final SimpleVote plugin;
    private final VotifierManager votifierManager;
    
    public KeyCommand(SimpleVote plugin, VotifierManager votifierManager) {
        this.plugin = plugin;
        this.votifierManager = votifierManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("simplevote.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        if (!votifierManager.isEnabled()) {
            sender.sendMessage(Component.text("Votifier functionality is not enabled.", NamedTextColor.RED));
            return true;
        }

        int port = plugin.getConfig().getInt("votifier.port", 8192);
        String sub = args.length > 0 ? args[0].toLowerCase() : "both";

        if (sub.equals("token")) {
            String token = plugin.getConfig().getString("votifier.token", "");
            sender.sendMessage(Component.text("=== SimpleVote Token ===", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Paste this into the Token field on your voting site:", NamedTextColor.GREEN));
            sender.sendMessage(Component.text(token.isEmpty() ? "(not generated yet)" : token, NamedTextColor.WHITE));
        } else if (sub.equals("key")) {
            sender.sendMessage(Component.text("=== SimpleVote Public Key ===", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Paste this into the Public Key field on your voting site:", NamedTextColor.GREEN));
            sender.sendMessage(Component.text(votifierManager.getRsaUtil().getV1FormattedPublicKey(), NamedTextColor.WHITE));
        } else {
            sender.sendMessage(Component.text("=== SimpleVote Votifier Credentials ===", NamedTextColor.YELLOW));
            sender.sendMessage(Component.empty());
            sender.sendMessage(Component.text("Public Key (paste into the Public Key field):", NamedTextColor.GREEN));
            sender.sendMessage(Component.text(votifierManager.getRsaUtil().getV1FormattedPublicKey(), NamedTextColor.WHITE));
            sender.sendMessage(Component.empty());
            String token = plugin.getConfig().getString("votifier.token", "");
            sender.sendMessage(Component.text("Token (paste into the Token field):", NamedTextColor.GREEN));
            sender.sendMessage(Component.text(token.isEmpty() ? "(not generated yet)" : token, NamedTextColor.WHITE));
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Votifier port: ", NamedTextColor.GREEN)
                .append(Component.text(port, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Tip: /votekey key   /votekey token   to print each separately.", NamedTextColor.GRAY));
        
        return true;
    }
} 