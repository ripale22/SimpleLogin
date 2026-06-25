package com.premiumauth.simplelogin.utils;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LoginRateLimiter {

    // Bukkit defaults
    private static final int MAX_ATTEMPTS = 5;
    private static final long COOLDOWN_MILLIS = 5 * 60 * 1000;

    private final Map<String, AttemptData> attempts = new ConcurrentHashMap<>();

    // ==========================================
    // Bukkit API
    // ==========================================

    public boolean isBlocked(String identifier) {
        AttemptData data = attempts.get(identifier);
        if (data == null) return false;
        if (System.currentTimeMillis() - data.windowStartedAt > COOLDOWN_MILLIS) {
            attempts.remove(identifier);
            return false;
        }
        return data.count >= MAX_ATTEMPTS;
    }

    public void recordFailure(String identifier) {
        long now = System.currentTimeMillis();
        attempts.compute(identifier, (key, data) -> {
            if (data == null || now - data.windowStartedAt > COOLDOWN_MILLIS) {
                return new AttemptData(1, now, false);
            }
            int count = data.count + 1;
            return new AttemptData(count, data.windowStartedAt, count >= MAX_ATTEMPTS);
        });
    }

    public void recordSuccess(String identifier) {
        attempts.remove(identifier);
    }

    public long getRemainingCooldown(String identifier) {
        AttemptData data = attempts.get(identifier);
        if (data == null) return 0;
        long elapsed = System.currentTimeMillis() - data.windowStartedAt;
        return Math.max(0, COOLDOWN_MILLIS - elapsed);
    }

    // ==========================================
    // Velocity API
    // ==========================================

    public boolean isBlocked(String key, int cooldownSeconds) {
        AttemptData data = attempts.get(key);
        if (data == null) {
            return false;
        }
        if (isExpired(data, cooldownSeconds)) {
            attempts.remove(key);
            return false;
        }
        return data.blocked;
    }

    public void recordFailure(String key, int maxAttempts, int cooldownSeconds) {
        long now = System.currentTimeMillis();
        attempts.compute(key, (ignored, data) -> {
            if (data == null || isExpired(data, cooldownSeconds)) {
                return new AttemptData(1, now, maxAttempts <= 1);
            }
            int count = data.count + 1;
            return new AttemptData(count, data.windowStartedAt, count >= maxAttempts);
        });
    }

    public long getRemainingCooldownMillis(String key, int cooldownSeconds) {
        AttemptData data = attempts.get(key);
        if (data == null) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - data.windowStartedAt;
        return Math.max(0L, (cooldownSeconds * 1000L) - elapsed);
    }

    public void cleanup(int cooldownSeconds) {
        Iterator<Map.Entry<String, AttemptData>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            if (isExpired(iterator.next().getValue(), cooldownSeconds)) {
                iterator.remove();
            }
        }
    }

    private boolean isExpired(AttemptData data, int cooldownSeconds) {
        return System.currentTimeMillis() - data.windowStartedAt > cooldownSeconds * 1000L;
    }

    private record AttemptData(int count, long windowStartedAt, boolean blocked) {
    }
}
