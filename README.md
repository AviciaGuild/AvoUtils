# AvoUtils

A Minecraft Fabric client mod providing various helper utilities for Avicia guild members.

## Requirements

- **Minecraft Version**: `1.21.11` (or compatible 1.21.x releases)
- **Fabric Loader**: `0.16.14` or newer
- **Fabric API**: `0.141.4` or newer
- **Java**: Java 21 (to run and compile)

## Configuration

On first run, the mod generates a configuration file in your client's config folder:
`config/avoutils.json`

```json
{
  "apiBaseUrl": "https://auth.avicia.info:8443"
}
```

## Building

To build the mod from source:

```bash
./gradlew build
```

The compiled mod jar will be located under `build/libs/`.

## License

MIT: `LICENSE`
