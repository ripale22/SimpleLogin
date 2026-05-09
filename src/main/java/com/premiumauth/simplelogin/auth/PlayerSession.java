package com.premiumauth.simplelogin.auth;

import java.util.UUID;

/**
 * Representa el estado de una sesion activa de jugador en memoria.
 */
public class PlayerSession {

    private final String username;
    private final UUID uniqueId;
    private final boolean premiumAuthenticated;
    private boolean locallyAuthenticated;
    private final long loginTime;

    public PlayerSession(String username, UUID uniqueId, boolean premiumAuthenticated) {
        this.username = username;
        this.uniqueId = uniqueId;
        this.premiumAuthenticated = premiumAuthenticated;
        this.locallyAuthenticated = premiumAuthenticated; // Premium ya esta autenticado por el proxy.
        this.loginTime = System.currentTimeMillis();
    }

    public String getUsername() {
        return username;
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public boolean isPremiumAuthenticated() {
        return premiumAuthenticated;
    }

    public boolean isLocallyAuthenticated() {
        return locallyAuthenticated;
    }

    public void setLocallyAuthenticated(boolean locallyAuthenticated) {
        this.locallyAuthenticated = locallyAuthenticated;
    }

    public long getLoginTime() {
        return loginTime;
    }
}
