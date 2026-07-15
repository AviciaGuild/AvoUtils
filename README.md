# AvoUtils

A Minecraft Fabric client mod providing various helper utilities for Avicia guild members, including Party Finder, Chat Bridge, and Emoji support.

[![GitHub Release](https://img.shields.io/github/v/release/AviciaGuild/AvoUtils?label=latest)](https://github.com/AviciaGuild/AvoUtils/releases/latest)
[![Modrinth](https://img.shields.io/badge/modrinth-AvoUtils-00AF5C?logo=modrinth)](https://modrinth.com/mod/avoutils)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

### Features

- **Party Finder:** Browse, create, and join parties in-game. Integrated with AvoBot's party finder system so parties created in-game sync with Discord and vice versa.
- **Chat Bridge:** Lets you view the in-game guild chat from Discord, and relays Discord messages back to in-game chat.
- **Emoji Support:** Use standard Discord emojis and custom emojis from Avicia's Discord server directly in Minecraft chat.

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
| Fabric Loader | `>=0.16.14` |
| Fabric API | `>=0.141.4` |
| Java | `>=21` |

---

### Configuration

Most settings can be changed in-game via `/avo config` (or just `/avo`). The config file lives at `config/avoutils.json`.

If you're running the backend locally, change the `apiBaseUrl` field to point at your local server.

---

### Building from Source

```bash
./gradlew build
```

The compiled jar will be at `build/libs/avoutils-<version>.jar`.

---

### Release Workflow

This project uses GitHub Actions to automate builds and publishing:

| Trigger | Version Format | Published To |
|---|---|---|
| `git tag v1.0.0` and push | `1.0.0` | GitHub Releases + Modrinth |
| Push to `main` | `1.0.0-beta.N` | GitHub Releases (pre-release) |
| Push to other branches | `1.0.0-dev.N` | Build only (artifact) |

To create a new release:

```bash
git tag v1.0.0
git push --tags
```

---

### License

[MIT](LICENSE)
