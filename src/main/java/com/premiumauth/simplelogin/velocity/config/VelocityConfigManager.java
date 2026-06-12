package com.premiumauth.simplelogin.velocity.config;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VelocityConfigManager {

    private final SimpleLoginVelocity plugin;
    private final Path configPath;
    private CommentedConfigurationNode root;
    private YamlConfigurationLoader loader;

    public VelocityConfigManager(SimpleLoginVelocity plugin) {
        this.plugin = plugin;
        this.configPath = plugin.getDataDirectory().resolve("config.yml");
        loadConfig();
    }

    private void loadConfig() {
        try {
            if (!Files.exists(configPath)) {
                Files.createDirectories(plugin.getDataDirectory());
                Files.copy(getClass().getResourceAsStream("/config.yml"), configPath);
            }
            this.loader = YamlConfigurationLoader.builder()
                    .path(configPath)
                    .build();
            root = loader.load();
        } catch (IOException e) {
            plugin.getLogger().error("Error cargando config.yml", e);
        }
    }

    public String getDatabaseType() {
        return root != null ? root.node("database", "type").getString("sqlite") : "sqlite";
    }

    public String getSqliteFile() {
        return root != null ? root.node("database", "sqlite", "file").getString("database.db") : "database.db";
    }

    public String getMysqlHost() { return root != null ? root.node("database", "mysql", "host").getString("localhost") : "localhost"; }
    public int getMysqlPort() { return root != null ? root.node("database", "mysql", "port").getInt(3306) : 3306; }
    public String getMysqlDatabase() { return root != null ? root.node("database", "mysql", "database").getString("simplelogin") : "simplelogin"; }
    public String getMysqlUsername() { return root != null ? root.node("database", "mysql", "username").getString("root") : "root"; }
    public String getMysqlPassword() { return root != null ? root.node("database", "mysql", "password").getString("") : ""; }
    public boolean getMysqlUseSSL() { return root != null ? root.node("database", "mysql", "useSSL").getBoolean(false) : false; }

    /* ================================================================
       LIMBO CONFIG (NanoLimbo / servidor limbo real)
       ================================================================ */

    public boolean isLimboEnabled() {
        return root != null ? root.node("limbo", "enabled").getBoolean(true) : true;
    }

    public String getLimboServerName() {
        return root != null ? root.node("limbo", "server_name").getString("limbo") : "limbo";
    }

    public String getLobbyServer() {
        return getMainSpawnServer();
    }

    public int getSessionDurationHours() {
        return getInt(24,
                new Object[]{"auth", "session-duration-hours"},
                new Object[]{"limbo", "session_duration_hours"});
    }

    /* ================================================================
       SPAWN CONFIG
       ================================================================ */

    public String getMainSpawnServer() {
        return getString("lobby",
                new Object[]{"servers", "main"},
                new Object[]{"spawn", "main_server"},
                new Object[]{"limbo", "lobby_server"});
    }

    public String getAuthSpawnServer() {
        return getString(getMainSpawnServer(),
                new Object[]{"servers", "auth"},
                new Object[]{"spawn", "auth_server"});
    }

    public void setMainSpawnServer(String server) {
        if (root != null) {
            try {
                root.node("servers", "main").set(server);
                root.node("spawn", "main_server").set(server);
                saveConfig();
            } catch (Exception e) {
                plugin.getLogger().error("Error guardando main spawn server", e);
            }
        }
    }

    public boolean isIpBindingEnabled() {
        return root != null ? root.node("security", "ip_binding").getBoolean(false) : false;
    }

    public int getDatabasePoolSize() {
        return root != null ? root.node("database", "pool", "max-size").getInt(10) : 10;
    }

    public boolean isRequireSsl() {
        return root != null ? root.node("database", "mysql", "requireSSL").getBoolean(false) : false;
    }

    public boolean isDebugLoggingEnabled() {
        return root != null && root.node("logging", "debug").getBoolean(false);
    }

    public String getLanguage() {
        return root != null ? root.node("language").getString("es").toLowerCase() : "es";
    }

    public int getMinPasswordLength() {
        return getInt(8, new Object[]{"auth", "min-password-length"});
    }

    public int getMaxLoginAttempts() {
        return getInt(5, new Object[]{"auth", "max-login-attempts"});
    }

    public int getLoginCooldownSeconds() {
        return getInt(60, new Object[]{"auth", "login-cooldown-seconds"});
    }

    public int getLoginTimeoutSeconds() {
        return getInt(60, new Object[]{"auth", "login-timeout-seconds"});
    }

    public int getMaxConnectionsPerIp() {
        return getInt(6, new Object[]{"security", "anti-bot", "max-connections-per-ip"});
    }

    public int getConnectionWindowSeconds() {
        return getInt(30, new Object[]{"security", "anti-bot", "connection-window-seconds"});
    }

    public int getConnectionCooldownSeconds() {
        return getInt(120, new Object[]{"security", "anti-bot", "cooldown-seconds"});
    }

    public int getMaxNameAttempts() {
        return getInt(4, new Object[]{"security", "anti-bot", "max-name-attempts"});
    }

    public void setAuthSpawnServer(String server) {
        if (root != null) {
            try {
                root.node("servers", "auth").set(server);
                root.node("spawn", "auth_server").set(server);
                saveConfig();
            } catch (Exception e) {
                plugin.getLogger().error("Error guardando auth spawn server", e);
            }
        }
    }

    private void saveConfig() {
        if (loader == null) {
            plugin.getLogger().error("No se pudo guardar config.yml: loader no inicializado.");
            return;
        }
        try {
            loader.save(root);
            plugin.getLogger().debug("Configuración guardada correctamente.");
        } catch (IOException e) {
            plugin.getLogger().error("Error guardando config.yml", e);
        }
    }

    public void reload() {
        loadConfig();
        plugin.getLogger().debug("Configuración recargada.");
    }

    private String getString(String fallback, Object[]... paths) {
        if (root == null) {
            return fallback;
        }
        for (Object[] path : paths) {
            String value = root.node(path).getString();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private int getInt(int fallback, Object[]... paths) {
        if (root == null) {
            return fallback;
        }
        for (Object[] path : paths) {
            if (!root.node(path).virtual()) {
                return root.node(path).getInt(fallback);
            }
        }
        return fallback;
    }
}
