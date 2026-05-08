package com.premiumauth.hybridlogin.velocity.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionRateLimiterTest {

    @Test
    void blocksIpWhenConnectionWindowExceedsLimit() {
        ConnectionRateLimiter limiter = new ConnectionRateLimiter();
        String ip = "127.0.0.1";

        assertFalse(limiter.recordAndIsBlocked(ip, "first", 2, 60, 60, 5));
        assertFalse(limiter.recordAndIsBlocked(ip, "second", 2, 60, 60, 5));
        assertTrue(limiter.recordAndIsBlocked(ip, "third", 2, 60, 60, 5));
    }
}
