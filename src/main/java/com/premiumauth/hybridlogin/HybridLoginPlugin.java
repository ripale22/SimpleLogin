package com.premiumauth.hybridlogin;

import com.premiumauth.hybridlogin.auth.SessionManager;
import com.premiumauth.hybridlogin.cache.AuthCache;
import com.premiumauth.hybridlogin.commands.AdminCommand;
import com.premiumauth.hybridlogin.commands.LoginCommand;
import com.premiumauth.hybridlogin.commands.PremiumCommand;
import com.premiumauth.hybridlogin.commands.RegisterCommand;
import com.premiumauth.hybridlogin.config.ConfigManager;
import com.premiumauth.hybridlogin.config.MessageManager;
import com.premiumauth.hybridlogin.database.DatabaseManager;
import com.premiumauth.hybridlogin.listeners.AuthListener;
import com.premiumauth.hybridlogin.listeners.PremiumVerificationListener;
import com.premiumauth.hybridlogin.services.MojangApiService;
import com.premiumauth.hybridlogin.utils.LoginRateLimiter;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import com.premiumauth.hybridlogin.models.Account;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Clase principal del plugin HybridLogin para Paper (Backend).
 *
 * <p>Arquitectura: Velocity Proxy -> Paper Backend.</p>
 * <ul>
 *   <li>Velocity se encarga de forzar online-mode para jugadores premium.</li>
 *   <li>Paper solo gestiona la base de datos compartida, el auto-login para premium
 *       y la autenticacion por contrasena para jugadores cracked.</li>
 * </ul>
 */
public class HybridLoginPlugin extends JavaPlugin {

    private static HybridLoginPlugin instance;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private SessionManager sessionManager;
    private AuthCache authCache;
    private MojangApiService mojangApiService;
    private LoginRateLimiter loginRateLimiter;
    private BukkitTask freezeTask;

    @Override
    public void onEnable() {
        instance = this;
        try {
            this.configManager = new ConfigManager(this);
            this.messageManager = new MessageManager(this);
            this.databaseManager = new DatabaseManager(this);
            this.authCache = new AuthCache();
            this.mojangApiService = new MojangApiService(this, this.authCache);
            this.sessionManager = new SessionManager();
            this.loginRateLimiter = new LoginRateLimiter();

            this.databaseManager.initializeSchema().thenRun(() -> {
                getLogger().info("SimpleLogin-Paper habilitado correctamente (BD lista).");
                Bukkit.getPluginManager().registerEvents(new AuthListener(this, sessionManager), this);
                Bukkit.getPluginManager().registerEvents(new PremiumVerificationListener(this), this);
                getCommand("premium").setExecutor(new PremiumCommand(this));
                getCommand("register").setExecutor(new RegisterCommand(this));
                getCommand("login").setExecutor(new LoginCommand(this));
                getCommand("simplelogin").setExecutor(new AdminCommand(this));

                getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

                this.freezeTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!sessionManager.isAuthenticated(p.getUniqueId())) {
                            p.setFreezeTicks(p.getMaxFreezeTicks());
                        }
                    }
                }, 20L, 20L);

                getLogger().info("Listeners y comandos registrados.");
            }).exceptionally(ex -> {
                getLogger().log(Level.SEVERE, "Error al inicializar el esquema de base de datos.", ex);
                Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
                return null;
            });
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error critico al habilitar HybridLogin-Paper", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (this.freezeTask != null) {
            this.freezeTask.cancel();
        }
        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
        getLogger().info("SimpleLogin-Paper deshabilitado.");
    }

    public static HybridLoginPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public AuthCache getAuthCache() {
        return authCache;
    }

    public MojangApiService getMojangApiService() {
        return mojangApiService;
    }

    public LoginRateLimiter getLoginRateLimiter() {
        return loginRateLimiter;
    }

    public void completeAuthentication(Player player, Account acc) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.clearTitle();
        player.setFreezeTicks(0);

        // Update session token and last IP
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";
        String token = UUID.randomUUID().toString().replace("-", "");
        long expiresAt = System.currentTimeMillis() + (configManager.getSessionExpirationHours() * 3600000L);

        acc.setLastIp(ip);
        acc.setSessionToken(token);
        acc.setSessionExpiresAt(expiresAt);

        databaseManager.updateAccount(acc).exceptionally(ex -> {
            getLogger().severe("Error actualizando sesion para " + player.getName());
            return null;
        });

        sessionManager.authenticate(player.getUniqueId());

        String targetServer = configManager.getTargetServer();
        if (targetServer != null && !targetServer.trim().isEmpty()) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(targetServer);
            player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
        } else {
            // Teleport to main spawn if no proxy server is configured
            Location mainSpawn = configManager.getSpawn("main");
            if (mainSpawn != null) {
                player.teleportAsync(mainSpawn);
            }
        }
    }
}
