package com.premiumauth.simplelogin.velocity;

import com.google.inject.Inject;
import com.premiumauth.simplelogin.velocity.auth.VelocityAuthManager;
import com.premiumauth.simplelogin.velocity.commands.AdminCommand;
import com.premiumauth.simplelogin.velocity.commands.ChangePasswordCommand;
import com.premiumauth.simplelogin.velocity.commands.LoginCommand;
import com.premiumauth.simplelogin.velocity.commands.LogoutCommand;
import com.premiumauth.simplelogin.velocity.commands.PremiumCommand;
import com.premiumauth.simplelogin.velocity.commands.RegisterCommand;
import com.premiumauth.simplelogin.velocity.config.VelocityConfigManager;
import com.premiumauth.simplelogin.velocity.config.VelocityMessageManager;
import com.premiumauth.simplelogin.velocity.database.VelocityDatabaseManager;
import com.premiumauth.simplelogin.velocity.limbo.AuthLimboManager;
import com.premiumauth.simplelogin.velocity.listener.LimboListener;
import com.premiumauth.simplelogin.velocity.listener.PreLoginListener;
import com.premiumauth.simplelogin.velocity.security.ConnectionRateLimiter;
import com.premiumauth.simplelogin.velocity.security.VelocityLoginRateLimiter;
import com.premiumauth.simplelogin.velocity.services.VelocityMojangService;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Plugin(
        id = "simplelogin",
        name = "simplelogin-Velocity",
        version = "1.2.1",
        description = "Proxy hibrido para forzar online-mode por jugador premium con limbo auth integrado",
        authors = {"ripale"},
        dependencies = {
                @Dependency(id = "limboapi", optional = true)
        }
)
public class SimpleLoginVelocity {

    public static final MinecraftChannelIdentifier ADMIN_CHANNEL = MinecraftChannelIdentifier.create("simplelogin", "admin");

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Map<String, Boolean> premiumStatusCache = new ConcurrentHashMap<>();

    private VelocityConfigManager configManager;
    private VelocityMessageManager messageManager;
    private VelocityDatabaseManager databaseManager;
    private VelocityAuthManager authManager;
    private AuthLimboManager authLimboManager;
    private VelocityMojangService mojangService;
    private VelocityLoginRateLimiter loginRateLimiter;
    private ConnectionRateLimiter connectionRateLimiter;
    private ExecutorService dbExecutor;

    @Inject
    public SimpleLoginVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            this.configManager = new VelocityConfigManager(this);
            this.messageManager = new VelocityMessageManager(this);
            this.databaseManager = new VelocityDatabaseManager(this, configManager);
            this.dbExecutor = Executors.newFixedThreadPool(
                    configManager.getDatabasePoolSize(),
                    r -> { Thread t = new Thread(r, "simplelogin-DB"); t.setDaemon(true); return t; }
            );
            this.databaseManager.setExecutor(dbExecutor);
            this.databaseManager.initializeSchema().join();
            this.authManager = new VelocityAuthManager();
            this.loginRateLimiter = new VelocityLoginRateLimiter();
            this.connectionRateLimiter = new ConnectionRateLimiter();
            this.mojangService = new VelocityMojangService(this);
            proxy.getChannelRegistrar().register(ADMIN_CHANNEL);
            proxy.getEventManager().register(this, new com.premiumauth.simplelogin.velocity.listener.AdminPluginMessageListener(this));

            proxy.getEventManager().register(this, new PreLoginListener(this, mojangService));
            validateConfiguredServers();

            if (configManager.isLimboEnabled()) {
                boolean limboApiPresent = proxy.getPluginManager().getPlugin("limboapi").isPresent();

                if (limboApiPresent) {
                    this.authLimboManager = new AuthLimboManager(this, authManager);
                    proxy.getEventManager().register(this, this.authLimboManager);
                    logger.info("LimboAPI enabled.");
                } else {
                    logger.warn("LimboAPI is not installed. Attempting automatic download...");
                    downloadLimboApi();
                }

                proxy.getEventManager().register(this, new LimboListener(this, authManager));
                // /login y /register se registran como Brigadier commands en AuthLimboManager (con tab completion)
            } else {
                logger.info("Limbo auth mode disabled.");
            }

            // Registrar comandos globales del proxy independientemente del Limbo
            proxy.getCommandManager().register("premium", new PremiumCommand(this));
            logger.debug("[Command] Registered: /premium");
            proxy.getCommandManager().register("changepassword", new ChangePasswordCommand(this), "changepass", "cp");
            proxy.getCommandManager().register("logout", new LogoutCommand(this));

            proxy.getCommandManager().register("simplelogin", new AdminCommand(this), "sl");
            logger.debug("[Command] Registered: /simplelogin (alias: /sl)");
            proxy.getScheduler().buildTask(this, () -> {
                loginRateLimiter.cleanup(configManager.getLoginCooldownSeconds());
                connectionRateLimiter.cleanup(configManager.getConnectionWindowSeconds());
            }).repeat(5, java.util.concurrent.TimeUnit.MINUTES).schedule();

            logger.info("SimpleLogin-Velocity enabled successfully.");
        } catch (Exception e) {
            logger.error("Critical error enabling SimpleLogin-Velocity", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Shutting down SimpleLogin-Velocity resources...");
        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
        if (this.dbExecutor != null && !this.dbExecutor.isShutdown()) {
            this.dbExecutor.shutdown();
        }
        logger.info("SimpleLogin-Velocity disabled successfully.");
    }

    public void setPremiumStatus(String username, boolean premium) {
        premiumStatusCache.put(username.toLowerCase(), premium);
    }

    public Boolean getPremiumStatus(String username) {
        return premiumStatusCache.get(username.toLowerCase());
    }

    public void clearPremiumStatus(String username) {
        premiumStatusCache.remove(username.toLowerCase());
    }

    private void downloadLimboApi() {
        CompletableFuture.runAsync(() -> {
            OkHttpClient client = new OkHttpClient();
            try {
                logger.info("Fetching LimboAPI releases...");
                Request apiRequest = new Request.Builder()
                        .url("https://api.github.com/repos/Elytrium/LimboAPI/releases?per_page=5")
                        .header("Accept", "application/vnd.github+json")
                        .build();
                String json;
                try (Response resp = client.newCall(apiRequest).execute()) {
                    json = resp.body() != null ? resp.body().string() : "";
                }
                if (json.isEmpty()) {
                    logger.error("Failed to get LimboAPI release info.");
                    return;
                }

                String tag = extractJsonString(json, "\"tag_name\":\"", "\"");
                String downloadUrl = extractJsonString(json, "\"browser_download_url\":\"", "\"");
                if (tag.isEmpty() || downloadUrl.isEmpty()) {
                    logger.error("Failed to parse LimboAPI release info.");
                    return;
                }

                File pluginsDir = dataDirectory.getParent().toFile();
                File destFile = new File(pluginsDir, "LimboAPI-" + tag + ".jar");

                if (destFile.exists()) {
                    if (destFile.length() > 0) {
                        logger.info("LimboAPI {} already exists in plugins folder.", tag);
                        logger.info("Run /velocity reload or restart to enable limbo mode.");
                        return;
                    }
                    destFile.delete();
                }

                logger.info("Downloading LimboAPI {} from {}...", tag, downloadUrl);
                Request downloadReq = new Request.Builder().url(downloadUrl).build();
                try (Response downloadResp = client.newCall(downloadReq).execute()) {
                    if (!downloadResp.isSuccessful() || downloadResp.body() == null) {
                        logger.error("Failed to download LimboAPI: HTTP {}", downloadResp.code());
                        return;
                    }
                    try (BufferedSink sink = Okio.buffer(Okio.sink(destFile))) {
                        sink.writeAll(downloadResp.body().source());
                    }
                }

                logger.info("LimboAPI {} downloaded successfully to plugins folder.", tag);
                logger.info("Run /velocity reload or restart the proxy to enable limbo mode.");
            } catch (Exception e) {
                logger.error("Failed to download LimboAPI automatically.", e);
                logger.error("Please download manually from: https://github.com/Elytrium/LimboAPI/releases");
            }
        });
    }

    private static String extractJsonString(String json, String key, String endDelim) {
        int start = json.indexOf(key);
        if (start == -1) return "";
        start += key.length();
        int end = json.indexOf(endDelim, start);
        if (end == -1) return "";
        return json.substring(start, end);
    }

    private void validateConfiguredServers() {
        String mainServer = configManager.getMainSpawnServer();
        if (proxy.getServer(mainServer).isEmpty()) {
            logger.error("Main server '{}' does not exist in velocity.toml. Check servers.main or spawn.main_server config.", mainServer);
        }

        String authServer = configManager.getAuthSpawnServer();
        if (proxy.getServer(authServer).isEmpty()) {
            logger.warn("Auth server '{}' does not exist in velocity.toml. Only needed if LimboAPI is disabled.", authServer);
        }
    }

    /**
     * Verifica si un CommandSource tiene privilegios de administrador.
     * Se basa UNICAMENTE en el permiso 'simplelogin.admin'.
     * Consola y usuarios con OP/permiso pueden usar los comandos admin.
     */
    public boolean isAdmin(CommandSource source) {
        return source.hasPermission("simplelogin.admin");
    }

    public ProxyServer getProxy() { return proxy; }
    public Logger getLogger() { return logger; }
    public Path getDataDirectory() { return dataDirectory; }
    public VelocityDatabaseManager getDatabaseManager() { return databaseManager; }
    public VelocityConfigManager getConfigManager() { return configManager; }
    public VelocityMessageManager getMessageManager() { return messageManager; }
    public VelocityAuthManager getAuthManager() { return authManager; }
    public AuthLimboManager getAuthLimboManager() { return authLimboManager; }
    public VelocityLoginRateLimiter getLoginRateLimiter() { return loginRateLimiter; }
    public ConnectionRateLimiter getConnectionRateLimiter() { return connectionRateLimiter; }
}
