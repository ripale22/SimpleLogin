package com.premiumauth.hybridlogin.velocity.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.premiumauth.hybridlogin.models.MojangProfile;
import com.premiumauth.hybridlogin.velocity.HybridLoginVelocity;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VelocityMojangService {

    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/%s";

    private final HybridLoginVelocity plugin;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final Cache<String, MojangProfile> profileCache;
    private final Cache<String, Boolean> negativeCache;

    public VelocityMojangService(HybridLoginVelocity plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .readTimeout(java.time.Duration.ofSeconds(5))
                .connectionPool(new ConnectionPool(5, 60, TimeUnit.SECONDS))
                .build();
        this.gson = new Gson();
        this.profileCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(5_000)
                .build();
        this.negativeCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(5_000)
                .build();
    }

    public CompletableFuture<Boolean> isPremium(String username) {
        return fetchProfile(username).thenApply(Optional::isPresent);
    }

    public CompletableFuture<Optional<MojangProfile>> fetchProfile(String username) {
        String lowerName = username.toLowerCase();
        MojangProfile cached = profileCache.getIfPresent(lowerName);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        Boolean negativeResult = negativeCache.getIfPresent(lowerName);
        if (negativeResult != null && !negativeResult) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String url = String.format(PROFILE_URL, username);
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "HybridLogin-Velocity/1.0")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == 204 || response.code() == 404) {
                    plugin.getLogger().debug("[Mojang] {} no es premium ({})", username, response.code());
                    negativeCache.put(lowerName, false);
                    return Optional.<MojangProfile>empty();
                }
                if (!response.isSuccessful() || response.body() == null) {
                    plugin.getLogger().warn("[Mojang] Respuesta inesperada para {}: {}", username, response.code());
                    return Optional.<MojangProfile>empty();
                }

                String body = response.body().string();
                MojangProfile profile = gson.fromJson(body, MojangProfile.class);
                if (profile != null && profile.getId() != null) {
                    plugin.getLogger().info("[Mojang] {} es premium (UUID: {})", username, profile.getId());
                    profileCache.put(lowerName, profile);
                    return Optional.of(profile);
                }
                return Optional.<MojangProfile>empty();
            } catch (IOException e) {
                plugin.getLogger().error("[Mojang] Error consultando perfil de: {}", username, e);
                return Optional.<MojangProfile>empty();
            }
        });
    }

    public void invalidateCache(String username) {
        String lowerName = username.toLowerCase();
        profileCache.invalidate(lowerName);
        negativeCache.invalidate(lowerName);
    }
}
