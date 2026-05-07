package com.premiumauth.hybridlogin.velocity;

import com.google.inject.Inject;
import com.premiumauth.hybridlogin.velocity.auth.VelocityAuthManager;
import com.premiumauth.hybridlogin.velocity.commands.AdminCommand;
import com.premiumauth.hybridlogin.velocity.commands.LoginCommand;
import com.premiumauth.hybridlogin.velocity.commands.PremiumCommand;
import com.premiumauth.hybridlogin.velocity.commands.RegisterCommand;
import com.premiumauth.hybridlogin.velocity.config.VelocityConfigManager;
import com.premiumauth.hybridlogin.velocity.config.VelocityMessageManager;
import com.premiumauth.hybridlogin.velocity.database.VelocityDatabaseManager;
import com.premiumauth.hybridlogin.velocity.limbo.AuthLimboManager;
import com.premiumauth.hybridlogin.velocity.listener.LimboListener;
import com.premiumauth.hybridlogin.velocity.listener.PreLoginListener;
import com.premiumauth.hybridlogin.velocity.services.VelocityMojangService;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Plugin(
        id = "simplelogin",
        name = "HybridLogin-Velocity",
        version = "1.0.0-SNAPSHOT",
        description = "Proxy hibrido para forzar online-mode por jugador premium con limbo auth integrado",
        authors = {"SeniorDev"},
        dependencies = {
                @Dependency(id = "limboapi", optional = true)
        }
)
public class HybridLoginVelocity {

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
    private ExecutorService dbExecutor;

    @Inject
    public HybridLoginVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
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
                    r -> { Thread t = new Thread(r, "HybridLogin-DB"); t.setDaemon(true); return t; }
            );
            this.databaseManager.setExecutor(dbExecutor);
            this.databaseManager.initializeSchema().join();
            this.authManager = new VelocityAuthManager();
            this.mojangService = new VelocityMojangService(this);

            proxy.getEventManager().register(this, new PreLoginListener(this, mojangService));

            if (configManager.isLimboEnabled()) {
                boolean limboApiPresent = proxy.getPluginManager().getPlugin("limboapi").isPresent();

                if (limboApiPresent) {
                    this.authLimboManager = new AuthLimboManager(this, authManager);
                    proxy.getEventManager().register(this, this.authLimboManager);
                    logger.info("Modo limbo nativo (LimboAPI) habilitado.");
                } else {
                    logger.error("LimboAPI no está instalado en Velocity. El modo limbo nativo no puede activarse.");
                    logger.error("Descarga LimboAPI desde: https://github.com/Elytrium/LimboAPI/releases");
                }

                proxy.getEventManager().register(this, new LimboListener(this, authManager));
                // /login y /register se registran como Brigadier commands en AuthLimboManager (con tab completion)
            } else {
                logger.info("Modo limbo de autenticación deshabilitado.");
            }

            // Registrar comandos globales del proxy independientemente del Limbo
            proxy.getCommandManager().register("premium", new PremiumCommand(this));
            logger.info("[Command] Registrado: /premium");

            proxy.getCommandManager().register("simplelogin", new AdminCommand(this), "sl");
            logger.info("[Command] Registrado: /simplelogin (alias: /sl)");

            logger.info("SimpleLogin-Velocity habilitado correctamente.");
        } catch (Exception e) {
            logger.error("Error critico al habilitar SimpleLogin-Velocity", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Cerrando recursos de SimpleLogin-Velocity...");
        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
        if (this.dbExecutor != null && !this.dbExecutor.isShutdown()) {
            this.dbExecutor.shutdown();
        }
        logger.info("SimpleLogin-Velocity deshabilitado correctamente.");
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
}
