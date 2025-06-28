package com.jellypudding.simpleVote.commands;

import com.jellypudding.simpleVote.SimpleVote;
import com.jellypudding.simpleVote.TokenManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public class TokenCommand implements CommandExecutor {
    private final TokenManager tokenManager;

    public TokenCommand(SimpleVote plugin, TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            // Check own tokens
            if (!sender.hasPermission("simplevote.tokens")) {
                sender.sendMessage(Component.text("You don't have permission to check tokens.", NamedTextColor.RED));
                return true;
            }

            if (sender instanceof Player player) {
                int tokens = tokenManager.getTokens(player.getUniqueId());
                String tokenText = tokens == 1 ? " vote token." : " vote tokens.";
                player.sendMessage(Component.text("You have ")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text(tokens).color(NamedTextColor.GOLD))
                        .append(Component.text(tokenText).color(NamedTextColor.GREEN)));
                return true;
            } else {
                sender.sendMessage(Component.text("Only players can check their own tokens.", NamedTextColor.RED));
                return false;
            }
        } else if (args.length == 1) {
            // Check another player's tokens
            if (!sender.hasPermission("simplevote.tokens.others")) {
                sender.sendMessage(Component.text("You don't have permission to check other players' tokens.", NamedTextColor.RED));
                return false;
            }

            String targetName = args[0];
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            
            if (!targetPlayer.hasPlayedBefore()) {
                sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
                return false;
            }
            
            int tokens = tokenManager.getTokens(targetPlayer.getUniqueId());
            String tokenText = tokens == 1 ? " vote token." : " vote tokens.";
            sender.sendMessage(Component.text(Objects.requireNonNull(targetPlayer.getName()))
                    .color(NamedTextColor.GOLD)
                    .append(Component.text(" has ").color(NamedTextColor.GREEN))
                    .append(Component.text(tokens).color(NamedTextColor.GOLD))
                    .append(Component.text(tokenText).color(NamedTextColor.GREEN)));
            return true;
        } else if (args.length == 3) {
            // Admin commands: give, take, set
            if (!sender.hasPermission("simplevote.admin")) {
                sender.sendMessage(Component.text("You don't have permission to manage tokens.", NamedTextColor.RED));
                return false;
            }

            String action = args[0].toLowerCase();
            String targetName = args[1];
            int amount;
            
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 0) {
                    sender.sendMessage(Component.text("Amount must be positive.", NamedTextColor.RED));
                    return false;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[2], NamedTextColor.RED));
                return false;
            }
            
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            if (!targetPlayer.hasPlayedBefore()) {
                sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
                return false;
            }
            
            UUID targetUUID = targetPlayer.getUniqueId();
            
            switch (action) {
                case "give":
                    tokenManager.addTokens(targetUUID, amount);
                    sender.sendMessage(Component.text("Gave ")
                            .color(NamedTextColor.GREEN)
                            .append(Component.text(amount).color(NamedTextColor.GOLD))
                            .append(Component.text(" tokens to ").color(NamedTextColor.GREEN))
                            .append(Component.text(Objects.requireNonNull(targetPlayer.getName())).color(NamedTextColor.GOLD)));
                    break;
                case "take":
                    if (tokenManager.removeTokens(targetUUID, amount)) {
                        sender.sendMessage(Component.text("Took ")
                                .color(NamedTextColor.GREEN)
                                .append(Component.text(amount).color(NamedTextColor.GOLD))
                                .append(Component.text(" tokens from ").color(NamedTextColor.GREEN))
                                .append(Component.text(Objects.requireNonNull(targetPlayer.getName())).color(NamedTextColor.GOLD)));
                    } else {
                        sender.sendMessage(Component.text(targetPlayer.getName() + " doesn't have enough tokens.", NamedTextColor.RED));
                    }
                    break;
                case "set":
                    tokenManager.setTokens(targetUUID, amount);
                    sender.sendMessage(Component.text("Set ")
                            .color(NamedTextColor.GREEN)
                            .append(Component.text(Objects.requireNonNull(targetPlayer.getName())).color(NamedTextColor.GOLD))
                            .append(Component.text("'s tokens to ").color(NamedTextColor.GREEN))
                            .append(Component.text(amount).color(NamedTextColor.GOLD)));
                    break;
                default:
                    sender.sendMessage(Component.text("Unknown action: " + action, NamedTextColor.RED));
                    return false;
            }
            
            return true;
        }

        // Display help
        if (sender.hasPermission("simplevote.admin")) {
            sender.sendMessage(Component.text("=== SimpleVote Token Commands ===", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/" + label)
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(" - Check your tokens", NamedTextColor.WHITE)));
            
            sender.sendMessage(Component.text("/" + label + " <player>")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(" - Check another player's tokens", NamedTextColor.WHITE)));
            
            sender.sendMessage(Component.text("/" + label + " give <player> <amount>")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(" - Give tokens", NamedTextColor.WHITE)));
            
            sender.sendMessage(Component.text("/" + label + " take <player> <amount>")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(" - Take tokens", NamedTextColor.WHITE)));
            
            sender.sendMessage(Component.text("/" + label + " set <player> <amount>")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(" - Set tokens", NamedTextColor.WHITE)));
        } else {
            sender.sendMessage(Component.text("Usage: /" + label + " - Check your vote token balance", NamedTextColor.GREEN));
        }

        return true;
    }
}
