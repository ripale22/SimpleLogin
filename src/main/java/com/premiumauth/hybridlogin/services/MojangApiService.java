package com.premiumauth.hybridlogin.services;

import com.google.gson.Gson;
import com.premiumauth.hybridlogin.HybridLoginPlugin;
import com.premiumauth.hybridlogin.cache.AuthCache;
import com.premiumauth.hybridlogin.models.MojangProfile;
import com.premiumauth.hybridlogin.utils.RateLimiter;
import okhttp3.HttpUrl;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Servicio encargado de interactuar con las APIs publicas de Mojang/Microsoft.
 * Todas las operaciones son asincronas y respetan un rate-limit conservador.
 */
public class MojangApiService {

    private static final String PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/%s";
    private static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/hasJoined";

    private final HybridLoginPlugin plugin;
    private final AuthCache authCache;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final RateLimiter rateLimiter;

    public MojangApiService(HybridLoginPlugin plugin, AuthCache authCache) {
        this.plugin = plugin;
        this.authCache = authCache;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .readTimeout(java.time.Duration.ofSeconds(5))
                .connectionPool(new ConnectionPool(5, 60, TimeUnit.SECONDS))
                .build();
        this.gson = new Gson();
        // Mojang recomienda no exceder ~600 req/10min. 1 por segundo es conservador y seguro.
        this.rateLimiter = new RateLimiter(1.0);
    }

    /**
     * Obtiene el perfil premium de un jugador desde la API de Mojang.
     * Primero consulta la cache; si no existe, realiza la peticion HTTP.
     *
     * @param username nombre de usuario a consultar.
     * @return CompletableFuture con el perfil, vacio si no es premium o la API falla.
     */
    public CompletableFuture<Optional<MojangProfile>> fetchPremiumProfile(String username) {
        Optional<MojangProfile> cached = authCache.getProfile(username);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }

        return supplyAsync(() -> {
            try {
                rateLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.<MojangProfile>empty();
            }

            String url = String.format(PROFILE_URL, username);
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "HybridLogin/1.0")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == 204 || response.code() == 404) {
                    // No existe perfil premium con ese nombre.
                    return Optional.<MojangProfile>empty();
                }
                if (!response.isSuccessful() || response.body() == null) {
                    plugin.getLogger().warning("Respuesta inesperada de Mojang Profile API: " + response.code());
                    return Optional.<MojangProfile>empty();
                }

                String body = response.body().string();
                MojangProfile profile = gson.fromJson(body, MojangProfile.class);
                if (profile != null && profile.getId() != null) {
                    authCache.putProfile(username, profile);
                    return Optional.of(profile);
                }
                return Optional.<MojangProfile>empty();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Error de I/O consultando perfil de Mojang para: " + username, e);
                return Optional.<MojangProfile>empty();
            }
        });
    }

    /**
     * Valida una sesion premium contra el servidor de sesiones de Mojang (hasJoined).
     * Este endpoint verifica que el cliente haya autenticado realmente con Microsoft/Mojang.
     *
     * @param username   nombre del jugador.
     * @param serverHash hash generado durante el handshake (serverId).
     * @return CompletableFuture con el perfil validado, vacio si la sesion no es valida.
     */
    public CompletableFuture<Optional<MojangProfile>> validateSession(String username, String serverHash) {
        Optional<MojangProfile> cached = authCache.getSession(username, serverHash);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }

        return supplyAsync(() -> {
            try {
                rateLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.<MojangProfile>empty();
            }

            HttpUrl url = HttpUrl.parse(SESSION_URL).newBuilder()
                    .addQueryParameter("username", username)
                    .addQueryParameter("serverId", serverHash)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "HybridLogin/1.0")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == 204 || response.code() == 400 || response.code() == 403) {
                    // Sesion invalida o no autenticada.
                    return Optional.<MojangProfile>empty();
                }
                if (!response.isSuccessful() || response.body() == null) {
                    plugin.getLogger().warning("Respuesta inesperada de Mojang Session API: " + response.code());
                    return Optional.<MojangProfile>empty();
                }

                String body = response.body().string();
                MojangProfile profile = gson.fromJson(body, MojangProfile.class);
                if (profile != null && profile.getId() != null) {
                    authCache.putSession(username, serverHash, profile);
                    return Optional.of(profile);
                }
                return Optional.<MojangProfile>empty();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Error de I/O validando sesion de Mojang para: " + username, e);
                return Optional.<MojangProfile>empty();
            }
        });
    }

    /**
     * Invalida las entradas de cache de un usuario especifico.
     * Util cuando un jugador cambia su estado premium o sale del servidor.
     *
     * @param username nombre de usuario.
     */
    public void invalidateCache(String username) {
        authCache.invalidateProfile(username);
        authCache.invalidateSession(username);
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, command ->
                Bukkit.getScheduler().runTaskAsynchronously(plugin, command));
    }
}
