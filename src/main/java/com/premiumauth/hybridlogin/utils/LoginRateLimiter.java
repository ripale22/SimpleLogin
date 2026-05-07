package com.premiumauth.hybridlogin.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long COOLDOWN_MILLIS = 5 * 60 * 1000;

    private final Map<String, AttemptData> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String identifier) {
        AttemptData data = attempts.get(identifier);
        if (data == null) return false;
        if (System.currentTimeMillis() - data.blockedSince > COOLDOWN_MILLIS) {
            attempts.remove(identifier);
            return false;
        }
        return data.count >= MAX_ATTEMPTS;
    }

    public void recordFailure(String identifier) {
        attempts.compute(identifier, (key, data) -> {
            if (data == null || System.currentTimeMillis() - data.blockedSince > COOLDOWN_MILLIS) {
                return new AttemptData(1, System.currentTimeMillis());
            }
            return new AttemptData(data.count + 1, data.blockedSince);
        });
    }

    public void recordSuccess(String identifier) {
        attempts.remove(identifier);
    }

    public long getRemainingCooldown(String identifier) {
        AttemptData data = attempts.get(identifier);
        if (data == null) return 0;
        long elapsed = System.currentTimeMillis() - data.blockedSince;
        return Math.max(0, COOLDOWN_MILLIS - elapsed);
    }

    private static class AttemptData {
        final int count;
        final long blockedSince;

        AttemptData(int count, long blockedSince) {
            this.count = count;
            this.blockedSince = blockedSince;
        }
    }
}
