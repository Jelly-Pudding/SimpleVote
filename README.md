# SimpleVote

A simple, completely self-contained vote plugin for Paper 1.21.4 that rewards players with `tokens` when they vote for your server.

## Features

- **100% standalone** with built-in vote receiver - no external plugins required!
- Built-in implementation of the Votifier protocol to receive votes directly from voting websites
- Rewards players with tokens when they vote for your server
- Configurable token amounts per vote
- Works with any voting site that supports the Votifier protocol
- Token management commands for admins
- Token balance checking for players
- Works with offline players (they'll receive tokens when they vote, even if offline)

## How It Works

SimpleVote implements the Votifier protocol directly within the plugin, opening a socket on port 8192 (configurable) to receive vote notifications from voting websites. When a player votes on a website, the website sends an encrypted message to your server, which SimpleVote receives, validates, and processes to award tokens to the player.

## Requirements

- Paper 1.21.4
- A server with port forwarding capability (to receive votes from websites)

## Installation

1. Download SimpleVote.jar from the releases section
2. Place the .jar file in your server's `plugins` folder
3. Start/restart your server
4. Configure SimpleVote as needed (config.yml)
5. Use the `/votekey` command to get your public key for voting websites
6. Register your server on voting websites with your server IP, port, and public key

## Configuration

The default configuration (`config.yml`) has the following options:

```yaml
# Tokens given per vote
tokens-per-vote: 1

# Whether to broadcast votes to the server
broadcast-votes: true

# Message to broadcast when a player votes
# Available placeholders: {player}, {service}, {tokens}
vote-message: "&6{player} &avoted for the server on &e{service} &aand received &e{tokens} &atokens!"

# Debug mode - simulates a vote on player join if true
# Only for testing purposes - set to false for production
debug-mode: false

# Votifier settings (for receiving votes from voting websites)
votifier:
  # Enable or disable the vote listener server
  enabled: true
  # Port to listen for vote connections (default is 8192)
  port: 8192
  # Whether to show detailed debug messages about vote connections
  debug: false
```

## Commands

- `/tokens` - Check your token balance
- `/tokens <player>` - Check another player's token balance
- `/tokens give <player> <amount>` - Give tokens to a player
- `/tokens take <player> <amount>` - Take tokens from a player
- `/tokens set <player> <amount>` - Set a player's token balance
- `/votekey` - Display the public key for registering on voting sites

## Permissions

- `simplevote.check` - Allow checking own token balance (default: true)
- `simplevote.check.others` - Allow checking others' token balances (default: op)
- `simplevote.admin` - Allow managing tokens for all players (default: op)

## Setting Up Voting

1. Start your server with SimpleVote installed
2. Use the `/votekey` command to get your public key
3. Register your server on voting websites (like Minecraft-Server-List.com, TopG, etc.)
4. When registering, provide:
   - Your server IP address
   - The Votifier port (default: 8192) 
   - Your public key
5. Make sure the port is open and forwarded to your server
6. Test by voting for your server
7. Players will receive tokens automatically when they vote

## Important Notes

- You must have port forwarding enabled for the Votifier port (8192 by default)
- If your server is behind a NAT/firewall, you must forward this port
- The port can be changed in the config.yml if needed
- When changing servers/IPs, you'll need to update your voting site registrations
