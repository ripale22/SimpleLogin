package com.premiumauth.simplelogin.velocity.database;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.premiumauth.simplelogin.velocity.config.VelocityConfigManager;
import com.premiumauth.simplelogin.utils.IpUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class VelocityDatabaseManager {

    private final SimpleLoginVelocity plugin;
    private final VelocityConfigManager configManager;
    private HikariDataSource dataSource;
    private Executor dbExecutor;
    private File sqliteFile;

    public void setExecutor(Executor executor) {
        this.dbExecutor = executor;
    }

    public VelocityDatabaseManager(SimpleLoginVelocity plugin, VelocityConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        initializePool();
    }

    private void initializePool() {
        HikariConfig config = new HikariConfig();
        String dbType = configManager.getDatabaseType();
        int poolSize = configManager.getDatabasePoolSize();

        if ("sqlite".equalsIgnoreCase(dbType)) {
            String configuredPath = configManager.getSqliteFile();
            Path dbPath = Paths.get(configuredPath);
            if (!dbPath.isAbsolute()) {
                dbPath = plugin.getDataDirectory().resolve(configuredPath).normalize();
            }
            File dbFile = dbPath.toFile();
            this.sqliteFile = dbFile;

            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(Math.min(poolSize, 5));
            config.setConnectionTestQuery("SELECT 1");
            config.setConnectionTimeout(15000);
            config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA busy_timeout=15000;");
            plugin.getLogger().info("BD SQLite lista.");
        } else if ("mysql".equalsIgnoreCase(dbType) || "mariadb".equalsIgnoreCase(dbType)) {
            String host = configManager.getMysqlHost();
            int port = configManager.getMysqlPort();
            String dbName = configManager.getMysqlDatabase();
            String user = configManager.getMysqlUsername();
            String pass = configManager.getMysqlPassword();
            boolean useSSL = configManager.getMysqlUseSSL();
            boolean requireSSL = configManager.isRequireSsl();

            if (!useSSL && !requireSSL) {
                plugin.getLogger().warn("[SEGURIDAD] MySQL conectado sin SSL. Se recomienda habilitar requireSSL en config.yml para producción.");
            }

            String sslMode = requireSSL ? "REQUIRED" : (useSSL ? "PREFERRED" : "DISABLED");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=" + useSSL + "&sslMode=" + sslMode + "&autoReconnect=true&allowPublicKeyRetrieval=true");
            config.setUsername(user);
            config.setPassword(pass);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setMaximumPoolSize(poolSize);
            config.setConnectionTimeout(15000);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            plugin.getLogger().info("BD MySQL/MariaDB lista (SSL: {}).", sslMode);
        } else {
            throw new UnsupportedOperationException("Base de datos no soportada: " + dbType);
        }

        config.setPoolName("SimpleLoginVelocityPool");
        this.dataSource = new HikariDataSource(config);
    }

    public CompletableFuture<Void> initializeSchema() {
        return runAsync(() -> {
            boolean isMysql = configManager.getDatabaseType().toLowerCase().contains("mysql") ||
                              configManager.getDatabaseType().toLowerCase().contains("mariadb");

            String autoIncrement = isMysql ? "AUTO_INCREMENT" : "AUTOINCREMENT";
            String sql = "CREATE TABLE IF NOT EXISTS accounts (" +
                    "id INTEGER PRIMARY KEY " + autoIncrement + "," +
                    "username VARCHAR(32) NOT NULL UNIQUE," +
                    "premium_uuid VARCHAR(36)," +
                    "offline_uuid VARCHAR(36)," +
                    "is_premium INTEGER DEFAULT 0," +
                    "premium_enabled INTEGER DEFAULT 0," +
                    "is_verification_pending INTEGER DEFAULT 0," +
                    "password_hash VARCHAR(255)," +
                    "first_join BIGINT," +
                    "last_join BIGINT," +
                    "last_ip VARCHAR(45)," +
                    "session_token VARCHAR(255)," +
                    "session_expires_at BIGINT" +
                    ");";
            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                // Ensure unique index exists for SQLite fallback
                try { stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_username ON accounts(username);"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN password_hash VARCHAR(255);"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN is_verification_pending INTEGER DEFAULT 0;"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN last_ip VARCHAR(45);"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN session_token VARCHAR(255);"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN session_expires_at BIGINT;"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN registered_ip VARCHAR(45);"); } catch (SQLException ignored) {}
            } catch (SQLException e) {
                plugin.getLogger().error("Error creando tabla accounts", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Consulta si un usuario requiere online-mode (ya sea premium verificado
     * o en proceso de verificacion de handshake).
     *
     * @param username nombre del jugador.
     * @return CompletableFuture con true si debe forzarse online-mode.
     */
    public CompletableFuture<Boolean> isPremiumEnabled(String username) {
        return supplyAsync(() -> {
            String lowerName = username.toLowerCase();
            plugin.getLogger().debug("[simplelogin-Velocity] Consultando BD por nombre: {}", lowerName);
            String sql = "SELECT is_premium, is_verification_pending FROM accounts WHERE LOWER(username) = ? LIMIT 1;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, lowerName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int isPremium = rs.getInt("is_premium");
                        int isVerificationPending = rs.getInt("is_verification_pending");
                        plugin.getLogger().debug("[simplelogin-Velocity] Cuenta encontrada. is_premium={}, verification_pending={}", isPremium, isVerificationPending);
                        return isPremium == 1 || isVerificationPending == 1;
                    } else {
                        plugin.getLogger().debug("[simplelogin-Velocity] Cuenta no encontrada.");
                    }
                    return false;
                }
            } catch (SQLException e) {
                plugin.getLogger().error("Error consultando estado premium de: {}", username, e);
                return false;
            }
        });
    }

    /* ================================================================
       MÉTODOS DE AUTENTICACIÓN PARA LIMBO
       ================================================================ */

    public CompletableFuture<Optional<AccountData>> getAccountData(String username) {
        return supplyAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "SELECT id, username, premium_uuid, offline_uuid, is_premium, premium_enabled, " +
                         "is_verification_pending, password_hash, last_ip, registered_ip, session_expires_at " +
                         "FROM accounts WHERE LOWER(username) = ? LIMIT 1;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, lowerName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapAccountData(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().error("Error consultando account data de: {}", username, e);
            }
            return Optional.empty();
        });
    }

    public CompletableFuture<Void> registerAccount(String username, UUID offlineUuid, String passwordHash) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            long now = System.currentTimeMillis();

            boolean isSqlite = configManager.getDatabaseType().equalsIgnoreCase("sqlite");
            String actualSql;
            if (isSqlite) {
                actualSql = "INSERT OR REPLACE INTO accounts (username, offline_uuid, password_hash, first_join, last_join) VALUES (?, ?, ?, ?, ?);";
            } else {
                // MySQL / MariaDB
                actualSql = "INSERT INTO accounts (username, offline_uuid, password_hash, first_join, last_join) VALUES (?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), last_join = VALUES(last_join);";
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(actualSql)) {
                stmt.setString(1, lowerName);
                stmt.setString(2, offlineUuid != null ? offlineUuid.toString() : null);
                stmt.setString(3, passwordHash);
                stmt.setLong(4, now);
                stmt.setLong(5, now);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().error("Error registrando cuenta de: {}", username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> updateSession(String username, String ip, long expiresAt) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "UPDATE accounts SET last_ip = ?, session_expires_at = ? WHERE LOWER(username) = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, IpUtil.normalize(ip));
                stmt.setLong(2, expiresAt);
                stmt.setString(3, lowerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().error("Error actualizando sesión de: {}", username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> clearSession(String username) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "UPDATE accounts SET last_ip = NULL, session_expires_at = 0 WHERE LOWER(username) = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, lowerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().error("Error limpiando sesión de: {}", username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> updatePassword(String username, String passwordHash) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "UPDATE accounts SET password_hash = ? WHERE LOWER(username) = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, passwordHash);
                stmt.setString(2, lowerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().error("Error actualizando password de: {}", username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> setPremiumEnabled(String username, boolean enabled) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "UPDATE accounts SET is_premium = ?, premium_enabled = ? WHERE LOWER(username) = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, enabled ? 1 : 0);
                stmt.setInt(2, enabled ? 1 : 0);
                stmt.setString(3, lowerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().error("Error actualizando estado premium de: {}", username, e);
                throw new RuntimeException(e);
            }
        });
    }

    private AccountData mapAccountData(ResultSet rs) throws SQLException {
        String premiumUuidStr = rs.getString("premium_uuid");
        String offlineUuidStr = rs.getString("offline_uuid");
        return new AccountData(
                rs.getInt("id"),
                rs.getString("username"),
                premiumUuidStr != null ? UUID.fromString(premiumUuidStr) : null,
                offlineUuidStr != null ? UUID.fromString(offlineUuidStr) : null,
                rs.getInt("is_premium") == 1,
                rs.getInt("premium_enabled") == 1,
                rs.getInt("is_verification_pending") == 1,
                rs.getString("password_hash"),
                rs.getString("last_ip"),
                rs.getString("registered_ip"),
                rs.getLong("session_expires_at")
        );
    }

    public CompletableFuture<Void> unregisterAccount(String username) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "DELETE FROM accounts WHERE LOWER(username) = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, lowerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().error("Error eliminando cuenta de: {}", username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> forcePremium(String username, boolean enabled) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "UPDATE accounts SET is_premium = ?, premium_enabled = ? WHERE LOWER(username) = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, enabled ? 1 : 0);
                stmt.setInt(2, enabled ? 1 : 0);
                stmt.setString(3, lowerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().error("Error forzando estado premium de: {}", username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> updateRegisteredIp(String username, String ip) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "UPDATE accounts SET registered_ip = ? WHERE LOWER(username) = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, IpUtil.normalize(ip));
                stmt.setString(2, lowerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().error("Error actualizando IP registrada de: {}", username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> resetRegisteredIp(String username) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "UPDATE accounts SET registered_ip = NULL WHERE LOWER(username) = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, lowerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().error("Error reseteando IP registrada de: {}", username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public File getBackupFolder() {
        File folder = plugin.getDataDirectory().resolve("backups").toFile();
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    public List<String> listBackups() {
        File folder = getBackupFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".db") || name.endsWith(".sql"));
        if (files == null) return List.of();
        List<String> names = new ArrayList<>();
        for (File f : files) names.add(f.getName());
        names.sort(Comparator.reverseOrder());
        return names;
    }

    public CompletableFuture<String> backupDatabase() {
        return supplyAsync(() -> {
            String dbType = configManager.getDatabaseType();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
            String timestamp = sdf.format(new Date());
            File backupFolder = getBackupFolder();

            if ("sqlite".equalsIgnoreCase(dbType)) {
                String filename = "backup-" + timestamp + ".db";
                File dest = new File(backupFolder, filename);
                try {
                    Files.copy(sqliteFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy SQLite file", e);
                }
                plugin.getLogger().info("SQLite backup created: " + filename);
                return filename;
            } else {
                String dbName = configManager.getMysqlDatabase();
                String host = configManager.getMysqlHost();
                int port = configManager.getMysqlPort();
                String user = configManager.getMysqlUsername();
                String pass = configManager.getMysqlPassword();
                String filename = "backup-" + timestamp + ".sql";
                File dest = new File(backupFolder, filename);

                try {
                    ProcessBuilder pb = new ProcessBuilder(
                        "mysqldump",
                        "-h", host,
                        "-P", String.valueOf(port),
                        "-u", user,
                        "-p" + pass,
                        "--single-transaction",
                        "--routines",
                        "--triggers",
                        dbName
                    );
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    Files.copy(process.getInputStream(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        throw new RuntimeException("mysqldump exited with code " + exitCode);
                    }
                    plugin.getLogger().info("MySQL backup created: " + filename);
                    return filename;
                } catch (Exception e) {
                    plugin.getLogger().error("Backup failed", e);
                    throw new RuntimeException("Backup failed: " + e.getMessage(), e);
                }
            }
        });
    }

    public CompletableFuture<Void> restoreBackup(String filename) {
        return runAsync(() -> {
            String dbType = configManager.getDatabaseType();
            File backupFile = new File(getBackupFolder(), filename);
            if (!backupFile.exists()) {
                throw new RuntimeException("Backup file not found: " + filename);
            }

            if ("sqlite".equalsIgnoreCase(dbType)) {
                dataSource.close();
                try {
                    Files.copy(backupFile.toPath(), sqliteFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("SQLite restored from backup: " + filename);
                } catch (Exception e) {
                    plugin.getLogger().error("Restore failed", e);
                    throw new RuntimeException("Restore failed: " + e.getMessage(), e);
                }
                initializePool();
            } else {
                String dbName = configManager.getMysqlDatabase();
                String host = configManager.getMysqlHost();
                int port = configManager.getMysqlPort();
                String user = configManager.getMysqlUsername();
                String pass = configManager.getMysqlPassword();

                try {
                    ProcessBuilder pb = new ProcessBuilder(
                        "mysql",
                        "-h", host,
                        "-P", String.valueOf(port),
                        "-u", user,
                        "-p" + pass,
                        dbName
                    );
                    pb.redirectInput(backupFile);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        throw new RuntimeException("mysql restore exited with code " + exitCode);
                    }
                    plugin.getLogger().info("MySQL restored from backup: " + filename);
                } catch (Exception e) {
                    plugin.getLogger().error("Restore failed", e);
                    throw new RuntimeException("Restore failed: " + e.getMessage(), e);
                }
            }
        });
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    private CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, dbExecutor != null ? dbExecutor : CompletableFuture.delayedExecutor(0, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, dbExecutor != null ? dbExecutor : CompletableFuture.delayedExecutor(0, java.util.concurrent.TimeUnit.MILLISECONDS));
    }
}
