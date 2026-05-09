package com.premiumauth.simplelogin.models;

import java.util.UUID;

/**
 * Modelo de datos que representa una cuenta de usuario en la base de datos.
 */
public class Account {

    private int id;
    private String username;
    private UUID premiumUuid;
    private UUID offlineUuid;
    private boolean premium;
    private boolean premiumEnabled;
    private boolean verificationPending;
    private long firstJoin;
    private long lastJoin;
    private String passwordHash;
    private String lastIp;
    private String sessionToken;
    private long sessionExpiresAt;

    public Account(int id, String username, UUID premiumUuid, UUID offlineUuid,
                   boolean premium, boolean premiumEnabled, boolean verificationPending,
                   long firstJoin, long lastJoin, String passwordHash,
                   String lastIp, String sessionToken, long sessionExpiresAt) {
        this.id = id;
        this.username = username;
        this.premiumUuid = premiumUuid;
        this.offlineUuid = offlineUuid;
        this.premium = premium;
        this.premiumEnabled = premiumEnabled;
        this.verificationPending = verificationPending;
        this.firstJoin = firstJoin;
        this.lastJoin = lastJoin;
        this.passwordHash = passwordHash;
        this.lastIp = lastIp;
        this.sessionToken = sessionToken;
        this.sessionExpiresAt = sessionExpiresAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public UUID getPremiumUuid() { return premiumUuid; }
    public void setPremiumUuid(UUID premiumUuid) { this.premiumUuid = premiumUuid; }

    public UUID getOfflineUuid() { return offlineUuid; }
    public void setOfflineUuid(UUID offlineUuid) { this.offlineUuid = offlineUuid; }

    public boolean isPremium() { return premium; }
    public void setPremium(boolean premium) { this.premium = premium; }

    public boolean isPremiumEnabled() { return premiumEnabled; }
    public void setPremiumEnabled(boolean premiumEnabled) { this.premiumEnabled = premiumEnabled; }

    public boolean isVerificationPending() { return verificationPending; }
    public void setVerificationPending(boolean verificationPending) { this.verificationPending = verificationPending; }

    public long getFirstJoin() { return firstJoin; }
    public void setFirstJoin(long firstJoin) { this.firstJoin = firstJoin; }

    public long getLastJoin() { return lastJoin; }
    public void setLastJoin(long lastJoin) { this.lastJoin = lastJoin; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getLastIp() { return lastIp; }
    public void setLastIp(String lastIp) { this.lastIp = lastIp; }

    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    public long getSessionExpiresAt() { return sessionExpiresAt; }
    public void setSessionExpiresAt(long sessionExpiresAt) { this.sessionExpiresAt = sessionExpiresAt; }

    public boolean isRegistered() {
        return passwordHash != null && !passwordHash.isEmpty();
    }
}
