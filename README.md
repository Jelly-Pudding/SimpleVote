# SimpleVote Plugin

**SimpleVote** is a lightweight Minecraft Paper 1.21.6 plugin that rewards players with tokens when they vote for your server on voting websites. It includes built-in Votifier support to receive votes directly from voting sites.

## Features
- Rewards players with tokens for voting
- Clickable list of voting sites for players
- Automatically handles online and offline player voting
- Admin commands to manage tokens

## Installation
1. Download the latest release from [GitHub](https://github.com/Jelly-Pudding/SimpleVote/releases/latest).
2. Place the `.jar` file in your server's `plugins` folder.
3. Restart your server to generate the default configuration.
4. Set up your server on voting sites using the public key displayed on startup or with the `/votekey` command.

## Setting Up Votifier

### Port Forwarding
For voting websites to send votes to your server, you must ensure that the Votifier port (default: 8192) is open and forwarded to your server. This typically involves:

1. Setting up port forwarding in your router's configuration
2. Ensuring your firewall allows incoming connections on the specified port
3. If using a hosting provider, check their documentation for port configuration

### Registering on Voting Sites
1. Start your server with SimpleVote installed
2. The plugin will display its Votifier public key in the console during startup
3. You can also get the key via the server console or in-game with the `/votekey` command
4. When registering your server on voting sites:
   - Enter your server's public IP address or domain
   - Enter the Votifier port (default: 8192)
   - Paste the public key when prompted

## Troubleshooting
- If votes aren't being received, check that:
  - The Votifier port is correctly forwarded.
  - The correct public key is being used.
  - Your firewall permits access.

## In-game Commands
- `/tokens`: Check your current tokens
- `/tokens [player]`: Check another player's tokens (requires permission)
- `/tokens give [player] [amount]`: Give tokens to a player (admin only)
- `/tokens take [player] [amount]`: Take tokens from a player (admin only)
- `/tokens set [player] [amount]`: Set a player's tokens (admin only)
- `/votesites`: Display a list of clickable voting site links
- `/votekey`: Display the public key for registration on voting sites (admin only)

## Permissions
- `simplevote.tokens`: Allows checking own token balance (default: true)
- `simplevote.tokens.others`: Allows checking others' token balances (default: op)
- `simplevote.votesites`: Allows viewing voting sites (default: true)
- `simplevote.admin`: Allows managing tokens and accessing admin commands (default: op)

## Support Me
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)