package com.premiumauth.simplelogin;

import com.premiumauth.simplelogin.auth.SessionManager;
import com.premiumauth.simplelogin.cache.AuthCache;
import com.premiumauth.simplelogin.commands.AdminCommand;
import com.premiumauth.simplelogin.commands.LoginCommand;
import com.premiumauth.simplelogin.commands.PremiumCommand;
import com.premiumauth.simplelogin.commands.RegisterCommand;
import com.premiumauth.simplelogin.config.ConfigManager;
import com.premiumauth.simplelogin.config.MessageManager;
import com.premiumauth.simplelogin.database.DatabaseManager;
import com.premiumauth.simplelogin.listeners.AuthListener;
import com.premiumauth.simplelogin.listeners.AdminPluginMessageListener;
import com.premiumauth.simplelogin.listeners.PremiumVerificationListener;
import com.premiumauth.simplelogin.services.MojangApiService;
import com.premiumauth.simplelogin.utils.LoginRateLimiter;
import com.premiumauth.simplelogin.utils.UpdateChecker;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import com.premiumauth.simplelogin.models.Account;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Clase principal del plugin simplelogin para Paper (Backend).
 *
 * <p>Arquitectura: Velocity Proxy -> Paper Backend.</p>
 * <ul>
 *   <li>Velocity se encarga de forzar online-mode para jugadores premium.</li>
 *   <li>Paper solo gestiona la base de datos compartida, el auto-login para premium
 *       y la autenticacion por contrasena para jugadores cracked.</li>
 * </ul>
 */
public class SimpleLoginPlugin extends JavaPlugin {

    private static SimpleLoginPlugin instance;
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
                getLogger().info("SimpleLogin-Paper enabled successfully (DB ready).");
                Bukkit.getPluginManager().registerEvents(new AuthListener(this, sessionManager), this);
                Bukkit.getPluginManager().registerEvents(new PremiumVerificationListener(this), this);
                getCommand("premium").setExecutor(new PremiumCommand(this));
                RegisterCommand registerCommand = new RegisterCommand(this);
                LoginCommand loginCommand = new LoginCommand(this);
                getCommand("register").setExecutor(registerCommand);
                getCommand("register").setTabCompleter(registerCommand);
                getCommand("login").setExecutor(loginCommand);
                getCommand("login").setTabCompleter(loginCommand);
                getCommand("simplelogin").setExecutor(new AdminCommand(this));

                getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
                getServer().getMessenger().registerOutgoingPluginChannel(this, "simplelogin:admin");
                getServer().getMessenger().registerIncomingPluginChannel(this, "simplelogin:admin", new AdminPluginMessageListener(this));

                this.freezeTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!sessionManager.isAuthenticated(p.getUniqueId())) {
                            p.setFreezeTicks(p.getMaxFreezeTicks());
                        }
                    }
                }, 20L, 20L);

                getLogger().info("Listeners and commands registered.");

                UpdateChecker.check().thenAccept(latest -> {
                    String current = getDescription().getVersion();
                    String latestVer = UpdateChecker.stripV(latest);
                    if (latest != null && !current.equals(latestVer)) {
                        getLogger().warning("==============================================");
                        getLogger().warning("SimpleLogin " + latestVer + " is available! (you are on " + current + ")");
                        getLogger().warning("Download: https://github.com/ripale22/SimpleLogin/releases/tag/" + latest);
                        getLogger().warning("==============================================");
                        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
                            @org.bukkit.event.EventHandler
                            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                                if (e.getPlayer().hasPermission("simplelogin.admin")) {
                                    e.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(
                                        "SimpleLogin " + latestVer + " is available! (you are on " + current + ")"
                                    ).color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                                }
                            }
                        }, SimpleLoginPlugin.this);
                    }
                });
            }).exceptionally(ex -> {
                getLogger().log(Level.SEVERE, "Error initializing database schema.", ex);
                Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
                return null;
            });
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Critical error enabling simplelogin-Paper", e);
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
        getLogger().info("SimpleLogin-Paper disabled.");
    }

    public static SimpleLoginPlugin getInstance() {
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
            getLogger().severe("Error updating session for " + player.getName());
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
