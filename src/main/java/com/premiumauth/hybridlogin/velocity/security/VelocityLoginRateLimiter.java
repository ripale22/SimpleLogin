package com.premiumauth.hybridlogin.velocity.security;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityLoginRateLimiter {

    private final Map<String, AttemptData> attempts = new ConcurrentHashMap<>();

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

    public void recordSuccess(String key) {
        attempts.remove(key);
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
