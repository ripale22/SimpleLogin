# SimpleLogin

**Universal authentication plugin for Paper & Velocity** — one JAR, both platforms, zero hassle.

SimpleLogin automatically detects official Mojang accounts via the Mojang API and logs them in instantly, while offline players register with a secure BCrypt-hashed password. Sessions are validated by UUID to prevent impersonation, and an integrated limbo system keeps unauthenticated players contained.

---

## Features

- **Automatic Premium Detection** — Mojang accounts are recognized and logged in instantly. No `/register` or `/login` required.
- **Cracked Account Protection** — Offline players must register with a secure password. Sessions validated by UUID + IP.
- **Universal JAR** — Deploy the same file on Paper and Velocity. No separate downloads.
- **Velocity Limbo Support** — Full LimboAPI integration. Unauthenticated players stay in a secure limbo state.
- **Auto-Install LimboAPI** — Detects missing LimboAPI on startup and downloads the latest compatible version.
- **Multi-Database** — SQLite (zero config) and MySQL/MariaDB (cross-server sync) via HikariCP.
- **Database Backups** — `/sl backup`, `/sl backup list`, `/sl backup restore` with tab completion.
- **Full i18n** — All player-facing messages are per-language (English/Spanish included). Console logs in English.
- **Anti-Bot Protection** — Per-IP rate limiting, max accounts per IP, IP binding, login cooldowns.
- **Session Validation** — UUID-based checks prevent cracked players from stealing premium sessions.
- **Permissions System** — Granular permissions for every command.

---

## Requirements

| Component | Version |
|-----------|---------|
| Java      | 17+     |
| Paper     | 1.20.4+ |
| Velocity  | 3.3.0+  |
| LimboAPI  | Optional (auto-downloaded) |

---

## Installation

1. Drop `SimpleLogin.jar` into your Velocity `plugins/` folder
2. Drop the same JAR into your Paper backend `plugins/` folder
3. Start both servers — `config.yml` is generated automatically
4. LimboAPI will be auto-downloaded on first startup if limbo mode is enabled
5. Configure database, language, and other settings in `config.yml`
6. Run `/velocity reload` or restart

### Velocity Setup

```yaml
# velocity.toml
online-mode = false
```

```properties
# server.properties (backend)
online-mode = false
```

SimpleLogin uses `forceOnlineMode()` / `forceOfflineMode()` per connection. No multi-proxy setup required.

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/register <pass> <confirm>` | Create a cracked account | `simplelogin.register` |
| `/login <pass>` | Authenticate with password | `simplelogin.login` |
| `/premium [confirm]` | Enable premium auto-login | `simplelogin.premium` |
| `/sl` | Admin help menu | `simplelogin.admin` |
| `/sl unregister <player>` | Reset a player's account | `simplelogin.admin` |
| `/sl forcepremium <player>` | Toggle premium mode | `simplelogin.admin` |
| `/sl setspawn <main\|auth>` | Set spawn locations | `simplelogin.admin` |
| `/sl resetip <player>` | Release bound IP | `simplelogin.admin` |
| `/sl setip <player>` | Update bound IP | `simplelogin.admin` |
| `/sl status <player>` | Show account status | `simplelogin.admin` |
| `/sl resetpassword <player>` | Generate temp password | `simplelogin.admin` |
| `/sl backup` | Create database backup | `simplelogin.admin` |
| `/sl backup list` | List available backups | `simplelogin.admin` |
| `/sl backup restore <file>` | Restore from backup | `simplelogin.admin` |
| `/sl info` | Show plugin diagnostics | `simplelogin.admin` |
| `/sl reload` | Reload configuration | `simplelogin.admin` |
| `/changepassword <old> <new>` | Change your password | *(all players)* |
| `/logout` | End your session | *(all players)* |

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `simplelogin.admin` | OP | Access to all `/sl` admin commands |
| `simplelogin.premium` | `true` | Use `/premium` |
| `simplelogin.register` | `true` | Use `/register` |
| `simplelogin.login` | `true` | Use `/login` |

---

## Configuration

All settings live in `plugins/simplelogin/config.yml`:

```yaml
database:
  type: sqlite              # sqlite, mysql, mariadb
  sqlite:
    file: database.db
  mysql:
    host: localhost
    port: 3306
    database: simplelogin
    username: root
    password: ""
  pool:
    max-size: 10

servers:
  main: lobby               # Server for authenticated players
  auth: lobby                # Fallback if LimboAPI is disabled

auth:
  min-password-length: 8
  max-accounts-per-ip: 3
  session-duration-hours: 24
  login-timeout-seconds: 60
  max-login-attempts: 5
  login-cooldown-seconds: 60

security:
  ip_binding: true
  anti-bot:
    max-connections-per-ip: 6
    connection-window-seconds: 30
    cooldown-seconds: 120
    max-name-attempts: 4

limbo:
  enabled: true

language: en                 # en, es, or custom filename
```

### Custom Languages

Copy `messages/en.yml` from the plugin resources, translate it, and set `language: <yourfile>` in `config.yml`.

---

## How It Works

1. Player connects to Velocity proxy
2. `PreLoginListener` queries Mojang API to detect premium status
3. Premium accounts → `forceOnlineMode()` with real Mojang UUID → auto-login on backend
4. Cracked accounts → `forceOfflineMode()` → enter limbo → `/register` → `/login`
5. Sessions persist for 24 hours (configurable) with UUID-based validation
6. Reconnections auto-authenticate if session is still valid

---

## Building from Source

```bash
./gradlew shadowJar
```

Output: `build/libs/SimpleLogin-<version>.jar`

---

## License

MIT — see [LICENSE](LICENSE).
