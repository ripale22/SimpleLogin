package com.premiumauth.simplelogin.config;

import com.premiumauth.simplelogin.SimpleLoginPlugin;
import com.premiumauth.simplelogin.database.DatabaseConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Gestiona la carga, recarga y persistencia del archivo config.yml.
 */
public class ConfigManager implements DatabaseConfig {

    private final SimpleLoginPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    public ConfigManager(SimpleLoginPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                } else {
                    configFile.createNewFile();
                }
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear config.yml: " + e.getMessage());
            }
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        addDefaults();
    }

    private void addDefaults() {
        config.addDefault("database.type", "sqlite"); // sqlite o mysql
        config.addDefault("database.sqlite.file", "database.db");
        config.addDefault("database.mysql.host", "localhost");
        config.addDefault("database.mysql.port", 3306);
        config.addDefault("database.mysql.database", "simplelogin");
        config.addDefault("database.mysql.username", "root");
        config.addDefault("database.mysql.password", "");
        config.addDefault("database.mysql.useSSL", false);
        config.addDefault("database.mysql.requireSSL", false);
        config.addDefault("database.pool.max-size", 10);
        config.addDefault("servers.main", "lobby");
        config.addDefault("servers.auth", "lobby");
        config.addDefault("auth.min-password-length", 8);
        config.addDefault("auth.max-accounts-per-ip", 3);
        config.addDefault("auth.session-duration-hours", 24);
        config.addDefault("auth.login-timeout-seconds", 60);
        config.addDefault("auth.max-login-attempts", 5);
        config.addDefault("auth.login-cooldown-seconds", 60);
        config.addDefault("security.ip_binding", false);
        config.addDefault("logging.debug", false);
        config.addDefault("language", "es");
        config.addDefault("settings.max-accounts-per-ip", 3);
        config.addDefault("settings.session-expiration-hours", 72);
        config.addDefault("settings.send-to-server-after-login", "");
        
        config.options().copyDefaults(true);
        saveConfig();
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar config.yml: " + e.getMessage());
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getSqliteFile() {
        return config.getString("database.sqlite.file", "database.db");
    }

    public String getMysqlHost() { return config.getString("database.mysql.host", "localhost"); }
    public int getMysqlPort() { return config.getInt("database.mysql.port", 3306); }
    public String getMysqlDatabase() { return config.getString("database.mysql.database", "simplelogin"); }
    public String getMysqlUsername() { return config.getString("database.mysql.username", "root"); }
    public String getMysqlPassword() { return config.getString("database.mysql.password", ""); }
    public boolean getMysqlUseSSL() { return config.getBoolean("database.mysql.useSSL", false); }
    public boolean getMysqlRequireSSL() { return config.getBoolean("database.mysql.requireSSL", false); }
    public int getDatabasePoolSize() { return config.getInt("database.pool.max-size", 10); }

    public int getMaxAccountsPerIp() { return getInt("auth.max-accounts-per-ip", "settings.max-accounts-per-ip", 3); }
    public int getSessionExpirationHours() { return getInt("auth.session-duration-hours", "settings.session-expiration-hours", 24); }
    public String getTargetServer() { return config.getString("settings.send-to-server-after-login", ""); }
    public int getMinPasswordLength() { return config.getInt("auth.min-password-length", 8); }
    public int getMaxLoginAttempts() { return config.getInt("auth.max-login-attempts", 5); }
    public int getLoginCooldownSeconds() { return config.getInt("auth.login-cooldown-seconds", 60); }
    public boolean isDebugLoggingEnabled() { return config.getBoolean("logging.debug", false); }

    public String getLanguage() {
        return config.getString("language", "es").toLowerCase();
    }

    public Location getSpawn(String type) {
        Location spawn = getSpawnAtPath("spawns." + type);
        if (spawn != null) {
            return spawn;
        }
        return getSpawnAtPath("locations." + type);
    }

    private Location getSpawnAtPath(String path) {
        if (!config.contains(path)) return null;

        String worldName = config.getString(path + ".world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        float yaw = (float) config.getDouble(path + ".yaw");
        float pitch = (float) config.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    public void setSpawn(String type, Location loc) {
        setSpawnAtPath("spawns." + type, loc);
        setSpawnAtPath("locations." + type, loc);
        saveConfig();
    }

    private void setSpawnAtPath(String path, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            config.set(path, null);
        } else {
            config.set(path + ".world", loc.getWorld().getName());
            config.set(path + ".x", loc.getX());
            config.set(path + ".y", loc.getY());
            config.set(path + ".z", loc.getZ());
            config.set(path + ".yaw", loc.getYaw());
            config.set(path + ".pitch", loc.getPitch());
        }
    }

    private int getInt(String primary, String legacy, int fallback) {
        if (config.contains(primary)) {
            return config.getInt(primary, fallback);
        }
        return config.getInt(legacy, fallback);
    }
}
