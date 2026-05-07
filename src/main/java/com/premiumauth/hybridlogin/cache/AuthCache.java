package com.premiumauth.hybridlogin.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.premiumauth.hybridlogin.models.MojangProfile;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AuthCache {

    private final Cache<String, MojangProfile> profileCache;
    private final Cache<String, MojangProfile> sessionCache;
    private final Map<String, Set<String>> sessionIndex = new ConcurrentHashMap<>();

    public AuthCache() {
        this.profileCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(10_000)
                .build();

        this.sessionCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(10_000)
                .build();
    }

    public Optional<MojangProfile> getProfile(String username) {
        return Optional.ofNullable(profileCache.getIfPresent(username.toLowerCase()));
    }

    public void putProfile(String username, MojangProfile profile) {
        profileCache.put(username.toLowerCase(), profile);
    }

    public void invalidateProfile(String username) {
        profileCache.invalidate(username.toLowerCase());
    }

    public Optional<MojangProfile> getSession(String username, String serverHash) {
        String key = buildSessionKey(username, serverHash);
        return Optional.ofNullable(sessionCache.getIfPresent(key));
    }

    public void putSession(String username, String serverHash, MojangProfile profile) {
        String key = buildSessionKey(username, serverHash);
        sessionCache.put(key, profile);
        sessionIndex.computeIfAbsent(username.toLowerCase(), k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    public void invalidateSession(String username) {
        String lowerName = username.toLowerCase();
        Set<String> keys = sessionIndex.remove(lowerName);
        if (keys != null) {
            sessionCache.invalidateAll(keys);
        }
    }

    private String buildSessionKey(String username, String serverHash) {
        return username.toLowerCase() + ":" + serverHash;
    }
}
