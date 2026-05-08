# 🛡️ SimpleLogin

SimpleLogin is a high-performance, professional Minecraft authentication solution designed for both **Paper** and **Velocity**. It provides a seamless experience for networks supporting both Premium and Cracked players.

## ✨ Key Features

*   **💎 Premium Auto-Join**: Automatic recognition and instant login for official Mojang accounts—no password required!
*   **🔌 Universal Support**: One single JAR works on both Paper (Spigot/Bukkit) and Velocity proxy.
*   **☁️ Velocity Limbo Integration**: Full support for LimboAPI to keep players in a secure state before authentication.
*   **🗄️ Flexible Storage**: Supports both **SQLite** (local) and **MySQL** (external) for cross-server synchronization.
*   **⚡ High Performance**: Built with HikariCP for database connection pooling and Caffeine for lightning-fast caching.
*   **🔒 Secure Security**: Passwords are encrypted using modern hashing (jBCrypt).
*   **🌐 Fully Customizable**: Custom messages and configuration for every network's needs.

## 🛠️ Requirements

- **Java**: 17 or higher
- **Server**: Paper 1.20.4+ or Velocity 3.3.0+
- **[LimboAPI](https://modrinth.com/plugin/limboapi)** (Optional): Highly recommended for Velocity networks to provide a superior "Limbo" experience.

## 🚀 Building from Source

To compile the latest version of the plugin:

```powershell
.\gradlew.bat shadowJar
```

The output JAR will be located in `build/libs/`.

## ⚙️ Configuration

The plugin generates default configuration files on the first run:
- `config.yml`: Core settings (Database, Premium settings, etc.)
- `messages.yml`: Fully customizable localization.

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
