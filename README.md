# SimpleLogin

SimpleLogin is a Minecraft authentication plugin for Paper and Velocity. It supports premium/cracked account handling, password registration/login, Mojang profile checks, SQLite/MySQL storage, and optional Velocity limbo integration through LimboAPI.

## Requirements

- Java 17
- Gradle wrapper included
- Paper 1.20.4 or Velocity 3.3.0
- LimboAPI is optional for Velocity limbo support

## Build

On Windows:

```powershell
.\gradlew.bat build
```

The compiled plugin jar is generated in `build/libs/`.

## Configuration

Default configuration files are located in `src/main/resources/`:

- `config.yml`
- `messages.yml`
- `plugin.yml`
- `velocity-plugin.json`

Runtime databases, logs, build outputs, IDE files, and local Gradle cache are ignored by Git.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).

