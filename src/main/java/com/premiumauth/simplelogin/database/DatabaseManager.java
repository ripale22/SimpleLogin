package com.premiumauth.simplelogin.database;

import com.premiumauth.simplelogin.SimpleLoginPlugin;
import com.premiumauth.simplelogin.models.Account;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Capa de acceso a datos utilizando HikariCP.
 * Todas las operaciones de I/O son asincronas para no bloquear el Main Thread.
 */
public class DatabaseManager {

    private final SimpleLoginPlugin plugin;
    private HikariDataSource dataSource;
    private File sqliteFile;

    public DatabaseManager(SimpleLoginPlugin plugin) {
        this.plugin = plugin;
        initializePool();
    }

    private void initializePool() {
        HikariConfig config = new HikariConfig();
        String dbType = plugin.getConfigManager().getDatabaseType();
        int poolSize = plugin.getConfigManager().getDatabasePoolSize();

        if ("sqlite".equalsIgnoreCase(dbType)) {
            String dbFile = plugin.getConfigManager().getSqliteFile();
            File file = new File(plugin.getDataFolder(), dbFile);
            this.sqliteFile = file;
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(Math.min(poolSize, 5));
            config.setConnectionTestQuery("SELECT 1");
            config.setConnectionTimeout(15000);
            config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA busy_timeout=15000;");
        } else if ("mysql".equalsIgnoreCase(dbType) || "mariadb".equalsIgnoreCase(dbType)) {
            String host = plugin.getConfigManager().getMysqlHost();
            int port = plugin.getConfigManager().getMysqlPort();
            String dbName = plugin.getConfigManager().getMysqlDatabase();
            String user = plugin.getConfigManager().getMysqlUsername();
            String pass = plugin.getConfigManager().getMysqlPassword();
            boolean useSSL = plugin.getConfigManager().getMysqlUseSSL();
            boolean requireSSL = plugin.getConfigManager().getMysqlRequireSSL();

            if (!useSSL && !requireSSL) {
                plugin.getLogger().warning("[SEGURIDAD] MySQL conectado sin SSL. Se recomienda habilitar requireSSL en config.yml para producción.");
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
        } else {
            throw new UnsupportedOperationException("Base de datos no soportada: " + dbType);
        }

        config.setPoolName("simpleloginPool");
        this.dataSource = new HikariDataSource(config);
    }

    public CompletableFuture<Void> initializeSchema() {
        return runAsync(() -> {
            boolean isMysql = plugin.getConfigManager().getDatabaseType().toLowerCase().contains("mysql") || 
                              plugin.getConfigManager().getDatabaseType().toLowerCase().contains("mariadb");
            
            String autoIncrement = isMysql ? "AUTO_INCREMENT" : "AUTOINCREMENT";
            String createTable = "CREATE TABLE IF NOT EXISTS accounts (" +
                         "id INTEGER PRIMARY KEY " + autoIncrement + "," +
                         "username VARCHAR(32) NOT NULL," +
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
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(createTable);
                // Migraciones para instalaciones previas
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN password_hash VARCHAR(255);"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN is_verification_pending INTEGER DEFAULT 0;"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN last_ip VARCHAR(45);"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN session_token VARCHAR(255);"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN session_expires_at BIGINT;"); } catch (SQLException ignored) {}
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error creando tabla accounts", e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Optional<Account>> getAccount(String username) {
        return supplyAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "SELECT * FROM accounts WHERE LOWER(username) = ? LIMIT 1;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, lowerName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error obteniendo cuenta: " + username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Integer> getAccountsCountByIp(String ip) {
        return supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM accounts WHERE last_ip = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ip);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error contando cuentas para IP: " + ip, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> createAccount(String username, UUID offlineUuid) {
        return runAsync(() -> {
            String sql = "INSERT INTO accounts (username, offline_uuid, first_join, last_join) VALUES (?, ?, ?, ?);";
            long now = System.currentTimeMillis();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, offlineUuid != null ? offlineUuid.toString() : null);
                stmt.setLong(3, now);
                stmt.setLong(4, now);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error creando cuenta: " + username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> updateAccount(Account account) {
        return runAsync(() -> {
            String sql = "UPDATE accounts SET premium_uuid = ?, offline_uuid = ?, is_premium = ?, " +
                         "premium_enabled = ?, is_verification_pending = ?, password_hash = ?, first_join = ?, last_join = ?, " +
                         "last_ip = ?, session_token = ?, session_expires_at = ? WHERE id = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, account.getPremiumUuid() != null ? account.getPremiumUuid().toString() : null);
                stmt.setString(2, account.getOfflineUuid() != null ? account.getOfflineUuid().toString() : null);
                stmt.setInt(3, account.isPremium() ? 1 : 0);
                stmt.setInt(4, account.isPremiumEnabled() ? 1 : 0);
                stmt.setInt(5, account.isVerificationPending() ? 1 : 0);
                stmt.setString(6, account.getPasswordHash());
                stmt.setLong(7, account.getFirstJoin());
                stmt.setLong(8, account.getLastJoin());
                stmt.setString(9, account.getLastIp());
                stmt.setString(10, account.getSessionToken());
                stmt.setLong(11, account.getSessionExpiresAt());
                stmt.setInt(12, account.getId());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error actualizando cuenta: " + account.getUsername(), e);
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
                plugin.getLogger().log(Level.SEVERE, "Error actualizando password de: " + username, e);
                throw new RuntimeException(e);
            }
        });
    }

    private Account mapResultSet(ResultSet rs) throws SQLException {
        return new Account(
                rs.getInt("id"),
                rs.getString("username"),
                uuidFromString(rs.getString("premium_uuid")),
                uuidFromString(rs.getString("offline_uuid")),
                rs.getInt("is_premium") == 1,
                rs.getInt("premium_enabled") == 1,
                rs.getInt("is_verification_pending") == 1,
                rs.getLong("first_join"),
                rs.getLong("last_join"),
                rs.getString("password_hash"),
                rs.getString("last_ip"),
                rs.getString("session_token"),
                rs.getLong("session_expires_at")
        );
    }

    private UUID uuidFromString(String str) {
        return str != null ? UUID.fromString(str) : null;
    }

    public File getBackupFolder() {
        File folder = new File(plugin.getDataFolder(), "backups");
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
            String dbType = plugin.getConfigManager().getDatabaseType();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
            String timestamp = sdf.format(new Date());
            File backupFolder = getBackupFolder();

            if ("sqlite".equalsIgnoreCase(dbType)) {
                String filename = "backup-" + timestamp + ".db";
                File dest = new File(backupFolder, filename);
                Files.copy(sqliteFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("SQLite backup created: " + filename);
                return filename;
            } else {
                String dbName = plugin.getConfigManager().getMysqlDatabase();
                String host = plugin.getConfigManager().getMysqlHost();
                int port = plugin.getConfigManager().getMysqlPort();
                String user = plugin.getConfigManager().getMysqlUsername();
                String pass = plugin.getConfigManager().getMysqlPassword();
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
                    plugin.getLogger().log(Level.SEVERE, "Backup failed", e);
                    throw new RuntimeException("Backup failed: " + e.getMessage(), e);
                }
            }
        });
    }

    public CompletableFuture<Void> restoreBackup(String filename) {
        return runAsync(() -> {
            String dbType = plugin.getConfigManager().getDatabaseType();
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
                    plugin.getLogger().log(Level.SEVERE, "Restore failed", e);
                    throw new RuntimeException("Restore failed: " + e.getMessage(), e);
                }
                initializePool();
            } else {
                String dbName = plugin.getConfigManager().getMysqlDatabase();
                String host = plugin.getConfigManager().getMysqlHost();
                int port = plugin.getConfigManager().getMysqlPort();
                String user = plugin.getConfigManager().getMysqlUsername();
                String pass = plugin.getConfigManager().getMysqlPassword();

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
                    plugin.getLogger().log(Level.SEVERE, "Restore failed", e);
                    throw new RuntimeException("Restore failed: " + e.getMessage(), e);
                }
            }
        });
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, command ->
                Bukkit.getScheduler().runTaskAsynchronously(plugin, command));
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, command ->
                Bukkit.getScheduler().runTaskAsynchronously(plugin, command));
    }
}
