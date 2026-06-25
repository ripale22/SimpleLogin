package com.premiumauth.simplelogin.database;

/**
 * Modelo temporal que representa una cuenta extraída de otra base de datos de login (como AuthMe).
 */
public class MigratedAccount {
    private final String username;
    private final String passwordHash;
    private final String ip;
    private final long regDate;
    private final long lastLogin;

    public MigratedAccount(String username, String passwordHash, String ip, long regDate, long lastLogin) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.ip = ip;
        this.regDate = regDate;
        this.lastLogin = lastLogin;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getIp() {
        return ip;
    }

    public long getRegDate() {
        return regDate;
    }

    public long getLastLogin() {
        return lastLogin;
    }
}
