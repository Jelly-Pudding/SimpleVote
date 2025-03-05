package com.jellypudding.simpleVote.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TokenTabCompleter implements TabCompleter {
    private final List<String> adminSubcommands = Arrays.asList("give", "take", "set");

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - player name or admin command
            List<String> players = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList();
            
            if (sender.hasPermission("simplevote.admin")) {
                completions.addAll(adminSubcommands);
            }
            
            completions.addAll(players);
        } else if (args.length == 2) {
            // Second argument - player name for admin commands
            if (sender.hasPermission("simplevote.admin") && adminSubcommands.contains(args[0].toLowerCase())) {
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .toList());
            }
        } else if (args.length == 3) {
            // Third argument - token amount for admin commands
            if (sender.hasPermission("simplevote.admin") && adminSubcommands.contains(args[0].toLowerCase())) {
                completions.add("1");
                completions.add("5");
                completions.add("10");
                completions.add("50");
                completions.add("100");
            }
        }

        // Filter completions based on the current input
        String currentArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentArg))
                .collect(Collectors.toList());
    }
} 