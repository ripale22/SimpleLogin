package com.premiumauth.simplelogin.velocity.database;

import java.util.UUID;

/**
 * Datos mínimos de cuenta para uso en el proxy (Velocity).
 */
public class AccountData {

    private final int id;
    private final String username;
    private final UUID premiumUuid;
    private final UUID offlineUuid;
    private final boolean premium;
    private final boolean premiumEnabled;
    private final boolean verificationPending;
    private final String passwordHash;
    private final String lastIp;
    private final String registeredIp;
    private final long sessionExpiresAt;

    public AccountData(int id, String username, UUID premiumUuid, UUID offlineUuid,
                       boolean premium, boolean premiumEnabled, boolean verificationPending,
                       String passwordHash, String lastIp, String registeredIp, long sessionExpiresAt) {
        this.id = id;
        this.username = username;
        this.premiumUuid = premiumUuid;
        this.offlineUuid = offlineUuid;
        this.premium = premium;
        this.premiumEnabled = premiumEnabled;
        this.verificationPending = verificationPending;
        this.passwordHash = passwordHash;
        this.lastIp = lastIp;
        this.registeredIp = registeredIp;
        this.sessionExpiresAt = sessionExpiresAt;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public UUID getPremiumUuid() { return premiumUuid; }
    public UUID getOfflineUuid() { return offlineUuid; }
    public boolean isPremium() { return premium; }
    public boolean isPremiumEnabled() { return premiumEnabled; }
    public boolean isVerificationPending() { return verificationPending; }
    public String getPasswordHash() { return passwordHash; }
    public String getLastIp() { return lastIp; }
    public String getRegisteredIp() { return registeredIp; }
    public long getSessionExpiresAt() { return sessionExpiresAt; }

    public boolean isRegistered() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    public boolean hasValidSession(String currentIp) {
        if (lastIp == null || sessionExpiresAt <= 0) return false;
        return lastIp.equals(currentIp) && System.currentTimeMillis() < sessionExpiresAt;
    }

    public boolean hasRegisteredIp() {
        return registeredIp != null && !registeredIp.isEmpty();
    }

    public boolean ipMatches(String currentIp) {
        return registeredIp != null && registeredIp.equals(currentIp);
    }
}
