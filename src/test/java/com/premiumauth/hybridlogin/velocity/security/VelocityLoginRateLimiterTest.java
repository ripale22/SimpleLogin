package com.premiumauth.hybridlogin.velocity.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityLoginRateLimiterTest {

    @Test
    void blocksAfterConfiguredFailuresAndClearsOnSuccess() {
        VelocityLoginRateLimiter limiter = new VelocityLoginRateLimiter();
        String key = "127.0.0.1:test";

        limiter.recordFailure(key, 3, 60);
        limiter.recordFailure(key, 3, 60);
        assertFalse(limiter.isBlocked(key, 60));

        limiter.recordFailure(key, 3, 60);
        assertTrue(limiter.isBlocked(key, 60));

        limiter.recordSuccess(key);
        assertFalse(limiter.isBlocked(key, 60));
    }
}
