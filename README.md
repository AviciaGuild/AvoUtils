# AvoUtils

A Minecraft Fabric client mod providing various helper utilities for Avicia guild members, including a guild raid party finder, Annihilation parties, chat bridge, and emoji support.

[![GitHub Release](https://img.shields.io/github/v/release/AviciaGuild/AvoUtils?label=latest)](https://github.com/AviciaGuild/AvoUtils/releases/latest)
[![Modrinth](https://img.shields.io/badge/modrinth-AvoUtils-00AF5C?logo=modrinth)](https://modrinth.com/mod/avoutils)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

### Features

- **Party Finder:** Browse, create, and join parties in-game. Syncs with AvoBot's party finder system, so parties created in Minecraft or Discord appear on both.
- **Annihilation Parties:** See the live Anni party roster while the event is active; integrates with AvoBot's Annihilation system.
- **Chat Bridge:** View the in-game guild chat from Discord, and send Discord messages to the in-game guild chat.
- **Emojis:** Use standard Discord emojis and custom emojis from Avicia's Discord server.

---

### Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft `1.21.11`
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the latest `avoutils-*.jar` from [Modrinth](https://modrinth.com/mod/avoutils) or [GitHub Releases](https://github.com/AviciaGuild/AvoUtils/releases)
4. Place the jar in your `mods/` folder

---

### Requirements

| Dependency | Version |
|---|---|
| Minecraft | `1.21.11` |
| Fabric Loader | `>=0.17.3` |
| Fabric API | `>=0.141.4` |
| Java | `>=21` |
| Wynntils  (optional) | `>=4.1.10` |

---

### Configuration

Most settings can be changed in-game via `/avo config` (or just `/avo`). The config file lives at `config/avoutils.json`.

The backend address is built into the mod. For local backend development, you can override it in the config file with a localhost address, e.g. `"apiBaseUrl": "http://localhost:8080"`; any other address is ignored. To build a test jar against a different backend, pass `-Papi_base_url=https://...` to Gradle (or set `api_base_url` in `local.properties`).

---

### Building from Source

```bash
./gradlew build
```

The compiled jar will be at `build/libs/avoutils-<version>.jar`.

To test without installing the jar, run `./gradlew runClient`, or in VS Code run `./gradlew vscode` once and start the "Minecraft Client" launch configuration (debugging this way lets you hot-swap code changes). [DevAuth](https://github.com/DJtheRedstoner/DevAuth) signs the dev client into your Microsoft account so it can join Wynncraft. To turn DevAuth off, set `devauth=false` in `local.properties`.

---

### Release Workflow

Publishing is manually triggered via GitHub Actions:

| Release Type | Options / Behavior | Version Format |
|---|---|---|
| **Beta Release** | Monotonically increments build number; resets to `1` when the base version changes | `<base>-beta.N`<br>*(e.g. `1.0.0-beta.1`)* |
| **Main Release** | Select bump type: `patch`, `minor`, or `major` | `<major>.<minor>.<patch>`<br> |
| **Tag Push** | Push tag `v<version>` directly | `<version>` |

---

### License

[MIT](LICENSE)
