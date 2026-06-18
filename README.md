# Avicia Party Finder Mod

A Minecraft Fabric client mod for integrating in-game Wynncraft party sessions with the AvoBot Party Finder backend. It enables leaders to automatically post, edit, invite, and reserve party slots on Discord directly from Minecraft.

## Requirements

- **Minecraft Version**: `1.21.11` (or compatible 1.21.x releases)
- **Fabric Loader**: `0.16.14` or newer
- **Fabric API**: `0.141.4` or newer
- **Java**: Java 21 (to run and compile)

## Features

- **Matchmaking UI**: Open the Party Finder screen with `/apf` or `/avo pf` to browse, create, join, leave, or edit active party listings.
- **Auto-Reservation**: Syncs your Minecraft party to the Discord lobby. When a new player joins the in-game party, the mod communicates with the AvoBot backend to auto-reserve slots on Discord.
- **Leader Controls**: Edit party details, manage members, or disband the party finder listing from the UI.

## Configuration

On first run, the mod generates a configuration file in your client's config folder:
`config/avicia-pfinder.json`

```json
{
  "apiBaseUrl": "https://auth.avicia.info:8443"
}
```

## Building

To build the mod from source:

```bash
cd AvoBot-PartyFinder-Mod
./gradlew build
```

The compiled mod jar will be located under `build/libs/`.

## License

MIT: [LICENSE](file:///C:/Projects/AvoBot-PartyFinder-Mod/LICENSE)
