package com.premiumauth.simplelogin.database;

import com.premiumauth.simplelogin.models.Account;
import com.premiumauth.simplelogin.utils.IpUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Unified data access layer using HikariCP.
 * All I/O operations are asynchronous using the provided executor.
 */
public class DatabaseManager {

    private final DatabaseConfig configManager;
    private final DatabaseLogger logger;
    private final File dataFolder;
    private final Executor dbExecutor;
    private HikariDataSource dataSource;
    private File sqliteFile;

    public DatabaseManager(DatabaseConfig configManager, DatabaseLogger logger, File dataFolder, Executor dbExecutor) {
        this.configManager = configManager;
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.dbExecutor = dbExecutor;
        initializePool();
    }

    private void initializePool() {
        HikariConfig config = new HikariConfig();
        String dbType = configManager.getDatabaseType();
        int poolSize = configManager.getDatabasePoolSize();

        if ("sqlite".equalsIgnoreCase(dbType)) {
            String dbFile = configManager.getSqliteFile();
            File file = new File(dataFolder, dbFile);
            this.sqliteFile = file;
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(Math.min(poolSize, 5));
            config.setConnectionTestQuery("SELECT 1");
            config.setConnectionTimeout(15000);
            config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA busy_timeout=15000;");
        } else if ("mysql".equalsIgnoreCase(dbType) || "mariadb".equalsIgnoreCase(dbType)) {
            String host = configManager.getMysqlHost();
            int port = configManager.getMysqlPort();
            String dbName = configManager.getMysqlDatabase();
            String user = configManager.getMysqlUsername();
            String pass = configManager.getMysqlPassword();
            boolean useSSL = configManager.getMysqlUseSSL();
            boolean requireSSL = configManager.getMysqlRequireSSL();

            if (!useSSL && !requireSSL) {
                logger.warning("MySQL connected without SSL. It is recommended to enable requireSSL in config.yml for production.");
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
            boolean isMysql = configManager.getDatabaseType().toLowerCase().contains("mysql") || 
                              configManager.getDatabaseType().toLowerCase().contains("mariadb");
            
            String autoIncrement = isMysql ? "AUTO_INCREMENT" : "AUTOINCREMENT";
            String createTable = "CREATE TABLE IF NOT EXISTS accounts (" +
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
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(createTable);
                // Ensure unique index exists for SQLite fallback
                try { stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_username ON accounts(username);"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN password_hash VARCHAR(255);"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN is_verification_pending INTEGER DEFAULT 0;"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN last_ip VARCHAR(45);"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN session_token VARCHAR(255);"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN session_expires_at BIGINT;"); } catch (SQLException ignored) {}
                try { stmt.execute("ALTER TABLE accounts ADD COLUMN registered_ip VARCHAR(45);"); } catch (SQLException ignored) {}
                try { stmt.execute("CREATE TABLE IF NOT EXISTS premium_bypass (username VARCHAR(32) PRIMARY KEY);"); } catch (SQLException ignored) {}
            } catch (SQLException e) {
                logger.severe("Error creando tabla accounts", e);
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
                logger.severe("Error obteniendo cuenta: " + username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Integer> getAccountsCountByIp(String ip) {
        return supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM accounts WHERE last_ip = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, IpUtil.normalize(ip));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }
            } catch (SQLException e) {
                logger.severe("Error contando cuentas para IP: " + ip, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> createAccount(String username, UUID offlineUuid) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "INSERT INTO accounts (username, offline_uuid, first_join, last_join) VALUES (?, ?, ?, ?);";
            long now = System.currentTimeMillis();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, lowerName);
                stmt.setString(2, offlineUuid != null ? offlineUuid.toString() : null);
                stmt.setLong(3, now);
                stmt.setLong(4, now);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Error creando cuenta: " + username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> updateAccount(Account account) {
        return runAsync(() -> {
            String sql = "UPDATE accounts SET premium_uuid = ?, offline_uuid = ?, is_premium = ?, " +
                         "premium_enabled = ?, is_verification_pending = ?, password_hash = ?, first_join = ?, last_join = ?, " +
                         "last_ip = ?, session_token = ?, session_expires_at = ?, registered_ip = ? WHERE id = ?;";
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
                stmt.setString(12, account.getRegisteredIp());
                stmt.setInt(13, account.getId());
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Error actualizando cuenta: " + account.getUsername(), e);
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
                logger.severe("Error actualizando password de: " + username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Boolean> isPremiumEnabled(String username) {
        return supplyAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "SELECT is_premium, is_verification_pending FROM accounts WHERE LOWER(username) = ? LIMIT 1;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, lowerName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int isPremium = rs.getInt("is_premium");
                        int isVerificationPending = rs.getInt("is_verification_pending");
                        return isPremium == 1 || isVerificationPending == 1;
                    }
                    return false;
                }
            } catch (SQLException e) {
                logger.severe("Error consultando estado premium de: " + username, e);
                return false;
            }
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
                logger.severe("Error registrando cuenta de: " + username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> updateSession(String username, String ip, long expiresAt) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "UPDATE accounts SET session_expires_at = ? WHERE LOWER(username) = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, expiresAt);
                stmt.setString(2, lowerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Error actualizando sesión de: " + username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> clearSession(String username) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "UPDATE accounts SET session_expires_at = 0 WHERE LOWER(username) = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, lowerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Error limpiando sesión de: " + username, e);
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
                logger.severe("Error actualizando estado premium de: " + username, e);
                throw new RuntimeException(e);
            }
        });
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
                logger.severe("Error eliminando cuenta de: " + username, e);
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> forcePremium(String username, boolean enabled) {
        return setPremiumEnabled(username, enabled);
    }

    public CompletableFuture<Void> setPremiumPending(String username, UUID premiumUuid) {
        return runAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "UPDATE accounts SET premium_uuid = ?, is_premium = 1, premium_enabled = 1, is_verification_pending = 1 WHERE LOWER(username) = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, premiumUuid != null ? premiumUuid.toString() : null);
                stmt.setString(2, lowerName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Error setting premium pending for: " + username, e);
                throw new RuntimeException(e);
            }
        });
    }
    public CompletableFuture<Boolean> isBypassed(String username) {
        return supplyAsync(() -> {
            String lowerName = username.toLowerCase();
            String sql = "SELECT 1 FROM premium_bypass WHERE LOWER(username) = ? LIMIT 1;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, lowerName);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                logger.severe("Error querying bypass for: " + username, e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> toggleBypass(String username) {
        return supplyAsync(() -> {
            String lowerName = username.toLowerCase();
            String checkSql = "SELECT 1 FROM premium_bypass WHERE LOWER(username) = ? LIMIT 1;";
            boolean exists = false;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setString(1, lowerName);
                try (ResultSet rs = stmt.executeQuery()) {
                    exists = rs.next();
                }
            } catch (SQLException e) {
                logger.severe("Error checking bypass status for: " + username, e);
                throw new RuntimeException(e);
            }

            if (exists) {
                String deleteSql = "DELETE FROM premium_bypass WHERE LOWER(username) = ?;";
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    stmt.setString(1, lowerName);
                    stmt.executeUpdate();
                    return false;
                } catch (SQLException e) {
                    logger.severe("Error removing bypass for: " + username, e);
                    throw new RuntimeException(e);
                }
            } else {
                String insertSql = "INSERT INTO premium_bypass (username) VALUES (?);";
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, lowerName);
                    stmt.executeUpdate();
                    return true;
                } catch (SQLException e) {
                    logger.severe("Error adding bypass for: " + username, e);
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private Account mapResultSet(ResultSet rs) throws SQLException {
        String premiumUuidStr = rs.getString("premium_uuid");
        String offlineUuidStr = rs.getString("offline_uuid");
        
        String registeredIp = null;
        try {
            registeredIp = rs.getString("registered_ip");
        } catch (SQLException ignored) {}

        return new Account(
                rs.getInt("id"),
                rs.getString("username"),
                premiumUuidStr != null ? UUID.fromString(premiumUuidStr) : null,
                offlineUuidStr != null ? UUID.fromString(offlineUuidStr) : null,
                rs.getInt("is_premium") == 1,
                rs.getInt("premium_enabled") == 1,
                rs.getInt("is_verification_pending") == 1,
                rs.getLong("first_join"),
                rs.getLong("last_join"),
                rs.getString("password_hash"),
                rs.getString("last_ip"),
                registeredIp,
                rs.getString("session_token"),
                rs.getLong("session_expires_at")
        );
    }

    public File getBackupFolder() {
        File folder = new File(dataFolder, "backups");
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
                logger.info("SQLite backup created: " + filename);
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
                    logger.info("MySQL backup created: " + filename);
                    return filename;
                } catch (Exception e) {
                    logger.severe("Backup failed", e);
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
                    logger.info("SQLite restored from backup: " + filename);
                } catch (Exception e) {
                    logger.severe("Restore failed", e);
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
                    logger.info("MySQL restored from backup: " + filename);
                } catch (Exception e) {
                    logger.severe("Restore failed", e);
                    throw new RuntimeException("Restore failed: " + e.getMessage(), e);
                }
            }
        });
    }

    public CompletableFuture<List<Account>> getAllAccounts() {
        return supplyAsync(() -> {
            String sql = "SELECT * FROM accounts;";
            List<Account> list = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            } catch (SQLException e) {
                logger.severe("Error getting all accounts", e);
                throw new RuntimeException(e);
            }
            return list;
        });
    }

    public CompletableFuture<Void> bulkInsertAccounts(List<MigratedAccount> accounts) {
        return runAsync(() -> {
            boolean isSqlite = configManager.getDatabaseType().equalsIgnoreCase("sqlite");
            String sql;
            if (isSqlite) {
                sql = "INSERT OR IGNORE INTO accounts (username, password_hash, last_ip, registered_ip, first_join, last_join, offline_uuid) VALUES (?, ?, ?, ?, ?, ?, ?);";
            } else {
                sql = "INSERT IGNORE INTO accounts (username, password_hash, last_ip, registered_ip, first_join, last_join, offline_uuid) VALUES (?, ?, ?, ?, ?, ?, ?);";
            }

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (MigratedAccount acc : accounts) {
                        String lowerName = acc.getUsername().toLowerCase();
                        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + acc.getUsername()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        
                        stmt.setString(1, lowerName);
                        stmt.setString(2, acc.getPasswordHash());
                        stmt.setString(3, acc.getIp());
                        stmt.setString(4, acc.getIp());
                        stmt.setLong(5, acc.getRegDate() > 0 ? acc.getRegDate() : System.currentTimeMillis());
                        stmt.setLong(6, acc.getLastLogin() > 0 ? acc.getLastLogin() : System.currentTimeMillis());
                        stmt.setString(7, offlineUuid.toString());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                logger.severe("Error during bulk insertion of accounts", e);
                throw new RuntimeException(e);
            }
        });
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, dbExecutor);
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, dbExecutor);
    }
}
