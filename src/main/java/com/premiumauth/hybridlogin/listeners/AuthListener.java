package com.premiumauth.hybridlogin.listeners;

import com.premiumauth.hybridlogin.HybridLoginPlugin;
import com.premiumauth.hybridlogin.auth.SessionManager;
import com.premiumauth.hybridlogin.database.DatabaseManager;
import com.premiumauth.hybridlogin.models.Account;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Listener de autenticacion para Paper.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Crear cuenta en BD si es primera vez.</li>
 *   <li>Auto-login para jugadores premium (Velocity ya valido la sesion).</li>
 *   <li>Congelar a jugadores cracked hasta que usen /login o /register.</li>
 * </ul>
 */
public class AuthListener implements Listener {

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "/login", "/log", "/l",
            "/register", "/reg", "/r"
    );

    private final HybridLoginPlugin plugin;
    private final SessionManager sessionManager;
    private final DatabaseManager databaseManager;

    public AuthListener(HybridLoginPlugin plugin, SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.databaseManager = plugin.getDatabaseManager();
    }

    /* ================================================================
       PRE-LOGIN: Crear cuenta si no existe
       ================================================================ */

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String username = event.getName();
        UUID incomingUuid = event.getUniqueId();

        Optional<Account> optAccount;
        try {
            optAccount = databaseManager.getAccount(username).join();
        } catch (Exception e) {
            plugin.getLogger().severe("Error de BD al validar pre-login de " + username);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    plugin.getMessageManager().getMessage("general.error"));
            return;
        }

        if (optAccount.isEmpty()) {
            try {
                databaseManager.createAccount(username, incomingUuid).join();
                plugin.getLogger().info("Nueva cuenta creada para: " + username);
            } catch (Exception e) {
                plugin.getLogger().severe("Error creando cuenta para " + username);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        plugin.getMessageManager().getMessage("general.error"));
                return;
            }
        }

        // No creamos sesion aqui; lo hacemos en PlayerJoinEvent cuando ya tenemos
        // la certeza del estado premium desde la BD.
    }

    /* ================================================================
       JOIN: Auto-login premium o congelar cracked
       ================================================================ */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();
        UUID uuid = player.getUniqueId();

        String currentIp = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";

        databaseManager.getAccount(username).thenAccept(optAccount -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (optAccount.isEmpty()) {
                    player.kick(plugin.getMessageManager().getMessage("general.error"));
                    return;
                }

                Account account = optAccount.get();

                account.setLastJoin(System.currentTimeMillis());

                if (account.isVerificationPending()) {
                    databaseManager.updateAccount(account).exceptionally(ex -> {
                        plugin.getLogger().warning("No se pudo actualizar last_join de " + username);
                        return null;
                    });
                    return;
                }

                if (account.isPremiumEnabled()) {
                    sessionManager.createSession(username, uuid, true);
                    player.sendMessage(plugin.getMessageManager().getMessage("premium.auto_login"));
                    teleportToSpawn(player, "main");
                } else {
                    boolean ipMatches = currentIp.equals(account.getLastIp());
                    boolean sessionValid = account.getSessionExpiresAt() > System.currentTimeMillis();

                    if (ipMatches && sessionValid) {
                        sessionManager.createSession(username, uuid, true);
                        player.sendMessage(Component.text("Has iniciado sesion automaticamente.").color(NamedTextColor.GREEN));
                        teleportToSpawn(player, "main");
                    } else {
                        sessionManager.createSession(username, uuid, false);
                        player.sendMessage(plugin.getMessageManager().getMessage("auth.must_login"));
                        teleportToSpawn(player, "auth");
                        applyAuthEffects(player);
                    }
                }

                databaseManager.updateAccount(account).exceptionally(ex -> {
                    plugin.getLogger().warning("No se pudo actualizar last_join de " + username);
                    return null;
                });
            });
        });
    }

    private void teleportToSpawn(Player player, String type) {
        Location spawn = plugin.getConfigManager().getSpawn(type);
        if (spawn != null) {
            player.teleportAsync(spawn);
        }
    }

    private void applyAuthEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 1, false, false, false));
        player.setFreezeTicks(player.getMaxFreezeTicks());
        Title title = Title.title(
                Component.text("SimpleLogin").color(NamedTextColor.GOLD),
                Component.text("Usa /login o /register").color(NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(10), Duration.ofMillis(300))
        );
        player.showTitle(title);
    }

    /* ================================================================
       QUIT: Limpieza de sesion
       ================================================================ */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        sessionManager.removeSession(player.getUniqueId());
    }

    /* ================================================================
       BLOQUEO DE ACCIONES PARA NO-AUTENTICADOS (CRACKED SIN LOGIN)
       ================================================================ */

    private boolean isNotAuthenticated(Player player) {
        return !sessionManager.isAuthenticated(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (isNotAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("auth.must_login"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (sessionManager.isAuthenticated(player.getUniqueId())) return;

        String cmd = event.getMessage().split(" ")[0].toLowerCase(Locale.ROOT);
        if (!ALLOWED_COMMANDS.contains(cmd)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("auth.must_login"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isNotAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (isNotAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (isNotAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isNotAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player && isNotAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isNotAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isNotAuthenticated(player)) {
            event.setCancelled(true);
        }
    }
}
