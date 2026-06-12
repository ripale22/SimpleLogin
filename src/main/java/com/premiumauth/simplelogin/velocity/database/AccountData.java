package com.premiumauth.simplelogin.velocity.database;

import java.util.UUID;

public class AccountData {

    private final int id;
    private final String username;
    private final UUID premiumUuid;
    private final UUID offlineUuid;
    private final boolean premium;
    private final boolean premiumEnabled;
    private final boolean verificationPending;
    private final String passwordHash;
    private final long sessionExpiresAt;

    public AccountData(int id, String username, UUID premiumUuid, UUID offlineUuid,
                       boolean premium, boolean premiumEnabled, boolean verificationPending,
                       String passwordHash, long sessionExpiresAt) {
        this.id = id;
        this.username = username;
        this.premiumUuid = premiumUuid;
        this.offlineUuid = offlineUuid;
        this.premium = premium;
        this.premiumEnabled = premiumEnabled;
        this.verificationPending = verificationPending;
        this.passwordHash = passwordHash;
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
    public long getSessionExpiresAt() { return sessionExpiresAt; }

    public boolean isRegistered() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    public boolean hasValidSession() {
        return System.currentTimeMillis() < sessionExpiresAt;
    }

    public boolean hasValidPremiumSession(UUID playerUuid) {
        return premiumEnabled
                && premiumUuid != null
                && premiumUuid.equals(playerUuid)
                && System.currentTimeMillis() < sessionExpiresAt;
    }

    public boolean hasValidCrackedSession(UUID playerUuid) {
        return !premiumEnabled
                && offlineUuid != null
                && offlineUuid.equals(playerUuid)
                && System.currentTimeMillis() < sessionExpiresAt;
    }
}
