package com.premiumauth.simplelogin.listeners;

import com.premiumauth.simplelogin.SimpleLoginPlugin;
import com.premiumauth.simplelogin.auth.PlayerSession;
import com.premiumauth.simplelogin.auth.SessionManager;
import com.premiumauth.simplelogin.database.DatabaseManager;
import com.premiumauth.simplelogin.models.Account;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class AuthListener implements Listener {

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "/login", "/log", "/l",
            "/register", "/reg", "/r"
    );

    private final SimpleLoginPlugin plugin;
    private final SessionManager sessionManager;
    private final DatabaseManager databaseManager;

    public AuthListener(SimpleLoginPlugin plugin, SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String username = event.getName();
        UUID incomingUuid = event.getUniqueId();

        Optional<Account> optAccount;
        try {
            optAccount = databaseManager.getAccount(username).join();
        } catch (Exception e) {
            plugin.getLogger().severe("DB error validating pre-login of " + username);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    plugin.getMessageManager().getMessage("general.error"));
            return;
        }

        if (optAccount.isEmpty()) {
            try {
                databaseManager.createAccount(username, incomingUuid).join();
                plugin.getLogger().info("New account created for: " + username);
            } catch (Exception e) {
                plugin.getLogger().severe("Error creating account for " + username);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        plugin.getMessageManager().getMessage("general.error"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();

        databaseManager.getAccount(username).thenAccept(optAccount -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (optAccount.isEmpty()) {
                player.kick(plugin.getMessageManager().getMessage("general.error"));
                return;
            }

            Account account = optAccount.get();
            account.setLastJoin(System.currentTimeMillis());

            persistLastJoin(account, username);
            handleJoinSession(player, account);
        }));
    }

    private void handleJoinSession(Player player, Account account) {
        UUID uuid = player.getUniqueId();
        PlayerSession session = sessionManager.getSession(uuid);

        if (session != null) {
            applyJoinSession(player, account, session);
            return;
        }

        plugin.getLogger().info("Session not yet present for " + player.getName() + ", deferring join handling.");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PlayerSession resolved = sessionManager.getSession(uuid);
            if (resolved == null) {
                plugin.getLogger().warning("No session created after defer for " + player.getName() + ". Creating temporary non-premium session.");
                resolved = sessionManager.createSession(player.getName(), uuid, false);
            }
            applyJoinSession(player, account, resolved);
        }, 10L);
    }

    private void applyJoinSession(Player player, Account account, PlayerSession session) {
        if (isValidSession(session, account, player)) {
            if (session.isPremiumAuthenticated()) {
                player.sendMessage(plugin.getMessageManager().getMessage("premium.auto_login"));
                teleportToSpawn(player, "main");
            } else if (session.isLocallyAuthenticated()) {
                player.sendMessage(plugin.getMessageManager().getMessage("limbo.auto_login"));
                teleportToSpawn(player, "main");
            }
            return;
        }

        player.sendMessage(plugin.getMessageManager().getMessage("auth.must_login"));
        teleportToSpawn(player, "auth");
        applyAuthEffects(player);
    }

    private void persistLastJoin(Account account, String username) {
        databaseManager.updateAccount(account).exceptionally(ex -> {
            plugin.getLogger().warning("Could not update last_join for " + username);
            return null;
        });
    }

    private boolean isValidSession(PlayerSession session, Account account, Player player) {
        boolean sessionUuidMatches = player.getUniqueId().equals(session.getUniqueId());

        if (!sessionUuidMatches) return false;

        return session.isPremiumAuthenticated()
                ? account.hasValidPremiumSession(player.getUniqueId())
                : account.hasValidCrackedSession(player.getUniqueId());
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
                plugin.getMessageManager().getMessage("limbo.title_simplelogin"),
                plugin.getMessageManager().getMessage("limbo.subtitle_prompt"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(10), Duration.ofMillis(300))
        );
        player.showTitle(title);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessionManager.removeSession(event.getPlayer().getUniqueId());
    }

    private boolean isNotAuthenticated(Player player) {
        return !sessionManager.isAuthenticated(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (isNotAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
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
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (isNotAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isNotAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isNotAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isNotAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isNotAuthenticated(player)) {
            event.setCancelled(true);
            return;
        }

        if (event.getDamager() instanceof Projectile proj) {
            ProjectileSource shooter = proj.getShooter();
            if (shooter instanceof Player s && isNotAuthenticated(s)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isNotAuthenticated(player)) {
            event.setCancelled(true);
        }
    }
}
