package com.premiumauth.simplelogin.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility to extract account data directly from an external AuthMe database (SQLite or MySQL).
 */
public class AuthMeMigrator {

    /**
     * Extracts accounts from an AuthMe SQLite database file.
     */
    public static List<MigratedAccount> extractFromSQLite(File sqliteFile) throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        String url = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
        List<MigratedAccount> accounts = new ArrayList<>();
        
        // Default table name in AuthMe is 'authme'
        String sql = "SELECT username, realname, password, ip, regdate, lastlogin FROM authme;";
        
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
             
             while (rs.next()) {
                 String username = rs.getString("realname");
                 if (username == null || username.isEmpty()) {
                     username = rs.getString("username");
                 }
                 accounts.add(new MigratedAccount(
                     username,
                     rs.getString("password"),
                     rs.getString("ip"),
                     rs.getLong("regdate"),
                     rs.getLong("lastlogin")
                 ));
             }
        }
        return accounts;
    }

    /**
     * Extracts accounts from an AuthMe MySQL/MariaDB database server.
     */
    public static List<MigratedAccount> extractFromMySQL(String host, int port, String database, String tableName, String user, String password, boolean useSSL) throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String sslMode = useSSL ? "PREFERRED" : "DISABLED";
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL + "&sslMode=" + sslMode + "&allowPublicKeyRetrieval=true";
        List<MigratedAccount> accounts = new ArrayList<>();
        
        String table = (tableName == null || tableName.trim().isEmpty()) ? "authme" : tableName;
        String sql = "SELECT username, realname, password, ip, regdate, lastlogin FROM " + table + ";";
        
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
             
             while (rs.next()) {
                 String username = rs.getString("realname");
                 if (username == null || username.isEmpty()) {
                     username = rs.getString("username");
                 }
                 accounts.add(new MigratedAccount(
                     username,
                     rs.getString("password"),
                     rs.getString("ip"),
                     rs.getLong("regdate"),
                     rs.getLong("lastlogin")
                 ));
             }
        }
        return accounts;
    }
}
