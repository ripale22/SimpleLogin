package com.premiumauth.simplelogin.velocity.auth;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona el estado de autenticación de jugadores en memoria dentro del proxy.
 */
public class VelocityAuthManager {

    private final Set<UUID> pendingAuth = ConcurrentHashMap.newKeySet();
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();

    public void setPending(UUID uuid) {
        pendingAuth.add(uuid);
        authenticated.remove(uuid);
    }

    public void remove(UUID uuid) {
        pendingAuth.remove(uuid);
        authenticated.remove(uuid);
    }

    public void clearPending(UUID uuid) {
        pendingAuth.remove(uuid);
    }

    public void authenticate(UUID uuid) {
        pendingAuth.remove(uuid);
        authenticated.add(uuid);
    }

    public boolean isPending(UUID uuid) {
        return pendingAuth.contains(uuid);
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.contains(uuid);
    }
}
