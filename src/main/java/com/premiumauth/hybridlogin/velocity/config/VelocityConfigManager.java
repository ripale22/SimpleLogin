package com.premiumauth.hybridlogin.velocity.config;

import com.premiumauth.hybridlogin.velocity.HybridLoginVelocity;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VelocityConfigManager {

    private final HybridLoginVelocity plugin;
    private final Path configPath;
    private CommentedConfigurationNode root;
    private YamlConfigurationLoader loader;

    public VelocityConfigManager(HybridLoginVelocity plugin) {
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
    public String getMysqlDatabase() { return root != null ? root.node("database", "mysql", "database").getString("hybridlogin") : "hybridlogin"; }
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
        return root != null ? root.node("limbo", "lobby_server").getString("lobby") : "lobby";
    }

    public int getSessionDurationHours() {
        return root != null ? root.node("limbo", "session_duration_hours").getInt(24) : 24;
    }

    /* ================================================================
       SPAWN CONFIG
       ================================================================ */

    public String getMainSpawnServer() {
        String value = root != null ? root.node("spawn", "main_server").getString(getLobbyServer()) : getLobbyServer();
        plugin.getLogger().info("[Config] Leyendo main_spawn: '{}'", value);
        return value;
    }

    public String getAuthSpawnServer() {
        return root != null ? root.node("spawn", "auth_server").getString(getLobbyServer()) : getLobbyServer();
    }

    public void setMainSpawnServer(String server) {
        if (root != null) {
            try {
                root.node("spawn", "main_server").set(server);
                plugin.getLogger().info("[Config] Guardando main_spawn: '{}'", server);
                saveConfig();
            } catch (Exception e) {
                plugin.getLogger().error("Error guardando main spawn server", e);
            }
        }
    }

    public boolean isIpBindingEnabled() {
        return root != null ? root.node("security", "ip_binding").getBoolean(true) : true;
    }

    public int getDatabasePoolSize() {
        return root != null ? root.node("database", "pool", "max-size").getInt(10) : 10;
    }

    public boolean isRequireSsl() {
        return root != null ? root.node("database", "mysql", "requireSSL").getBoolean(false) : false;
    }

    public void setAuthSpawnServer(String server) {
        if (root != null) {
            try {
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
            plugin.getLogger().info("Configuración guardada correctamente.");
        } catch (IOException e) {
            plugin.getLogger().error("Error guardando config.yml", e);
        }
    }

    public void reload() {
        loadConfig();
        plugin.getLogger().info("Configuración recargada.");
    }
}
