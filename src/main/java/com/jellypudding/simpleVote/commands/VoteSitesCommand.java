package com.jellypudding.simpleVote.commands;

import com.jellypudding.simpleVote.SimpleVote;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

/**
 * Command to display a list of voting sites as clickable links
 */
public class VoteSitesCommand implements CommandExecutor {
    private final SimpleVote plugin;
    
    public VoteSitesCommand(SimpleVote plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<Map<String, String>> votingSites = plugin.getConfigManager().getVotingSites();
        
        if (votingSites.isEmpty()) {
            sender.sendMessage(Component.text("No voting sites have been configured.", NamedTextColor.RED));
            return true;
        }
        
        sender.sendMessage(Component.text("=== Voting Sites ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("Click on a site to open it in your browser:", NamedTextColor.YELLOW));
        
        for (Map<String, String> site : votingSites) {
            String name = site.get("name");
            String url = site.get("url");
            
            if (name != null && url != null) {
                Component linkComponent = Component.text("➤ ", NamedTextColor.GRAY)
                    .append(Component.text(name, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url)));
                
                sender.sendMessage(linkComponent);
            }
        }
        
        return true;
    }
} 