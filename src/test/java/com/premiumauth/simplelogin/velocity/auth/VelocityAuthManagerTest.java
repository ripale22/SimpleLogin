package com.premiumauth.simplelogin.velocity.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityAuthManagerTest {

    @Test
    void clearingPendingStateDoesNotRemoveAuthenticatedSession() {
        VelocityAuthManager authManager = new VelocityAuthManager();
        UUID uuid = UUID.randomUUID();

        authManager.setPending(uuid);
        authManager.authenticate(uuid);
        authManager.clearPending(uuid);

        assertFalse(authManager.isPending(uuid));
        assertTrue(authManager.isAuthenticated(uuid));
    }
}
