# AvoUtils

A quality-of-life mod for [Avicia](https://discord.com/invite/avicia) guild members on Wynncraft.

This is not a general-purpose mod; you would need to link your Minecraft account in Avicia's Discord server to access most features.

### Features

- **Party Finder:** Browse, create, and join parties in-game. Syncs with AvoBot's party finder system, so parties created in Minecraft or Discord appear on both.
- **Chat Bridge:** View the in-game guild chat from Discord, and send Discord messages to the in-game guild chat.
- **Emojis:** Use standard Discord emojis and custom emojis from Avicia's Discord server.

### Setup

Before most of the mod's features will work, you need to link your Minecraft account with Discord.

1. Join Avicia's [Discord server](https://discord.com/invite/avicia)
2. Use `/link` through AviciaBot
3. Join Avicia on Wynncraft (required for some features)
4. Install the mod (see below)

### Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the latest version from this page
4. Place the jar in your `mods` folder

### Requirements
- Minecraft `1.21.11`
- Fabric Loader `>=0.16.14`
- Fabric API `>=0.141.4`
- Java `>=21`
- Wynntils (optional) `>=4.1.10`

### Commands

| Command | Description |
|---|---|
| `/avo` or `/avo config` | Opens the AvoUtils config screen |
| `/apf` or `/avo pf` | Opens the party finder GUI |
| `/apf join <name>` | Opens the detail modal for `<name>`'s party |
| `/apf togglenotifs` | Toggles new party notifications on/off |
| `/apf togglesounds` | Toggles notification sounds on/off |
| `/avo bridge` | Toggles the chat bridge on/off (requires guild membership) |
| `/avo storage` | Toggles storage threshold notifications on/off (requires guild membership) |
| `/avo emojis` | Toggles emoji rendering on/off |
