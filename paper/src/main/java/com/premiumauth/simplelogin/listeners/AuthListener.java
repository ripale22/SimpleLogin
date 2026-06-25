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
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthListener implements Listener {

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "/login", "/log", "/l",
            "/register", "/reg", "/r"
    );

    private final SimpleLoginPlugin plugin;
    private final SessionManager sessionManager;
    private final DatabaseManager databaseManager;

    private final Map<String, Account> pendingAccountsCache = new ConcurrentHashMap<>();
    private final Set<String> failedVerifications = ConcurrentHashMap.newKeySet();
    private final Set<String> successfulVerifications = ConcurrentHashMap.newKeySet();

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
            plugin.getLogger().severe("DB error validating pre-login of " + username + ": " + e.getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    plugin.getMessageManager().getMessage("general.error"));
            return;
        }

        if (optAccount.isEmpty()) {
            try {
                databaseManager.createAccount(username, incomingUuid).join();
                plugin.getLogger().info("New account created for: " + username);
                optAccount = databaseManager.getAccount(username).join();
            } catch (Exception e) {
                plugin.getLogger().severe("Error creating account for " + username + ": " + e.getMessage());
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        plugin.getMessageManager().getMessage("general.error"));
                return;
            }
        }

        if (optAccount.isPresent()) {
            Account account = optAccount.get();
            if (account.isVerificationPending()) {
                plugin.getLogger().info("[simplelogin-Paper] Verification pending detected for " + username + " during pre-login");
                if (account.getPremiumUuid() != null) {
                    if (incomingUuid.equals(account.getPremiumUuid()) || !plugin.getServer().getOnlineMode()) {
                        plugin.getLogger().info("[simplelogin-Paper] Completing premium verification for: " + username);
                        account.setPremium(true);
                        account.setPremiumEnabled(true);
                        account.setVerificationPending(false);
                        try {
                            databaseManager.updateAccount(account).join();
                            successfulVerifications.add(username.toLowerCase());
                        } catch (Exception e) {
                            plugin.getLogger().severe("Error updating verified premium account for " + username + ": " + e.getMessage());
                        }
                    } else {
                        plugin.getLogger().info("[simplelogin-Paper] UUID mismatch for " + username + " (Online: " + incomingUuid + ", DB: " + account.getPremiumUuid() + "). Canceling pending status.");
                        account.setVerificationPending(false);
                        try {
                            databaseManager.updateAccount(account).join();
                            failedVerifications.add(username.toLowerCase());
                        } catch (Exception e) {
                            plugin.getLogger().severe("Error updating failed premium verification for " + username + ": " + e.getMessage());
                        }
                    }
                } else {
                    plugin.getLogger().warning("[simplelogin-Paper] Inconsistent state (Premium UUID is null) for: " + username);
                    account.setVerificationPending(false);
                    try {
                        databaseManager.updateAccount(account).join();
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error clearing pending verification for " + username + ": " + e.getMessage());
                    }
                }
            }
            pendingAccountsCache.put(username.toLowerCase(), account);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();

        Account account = pendingAccountsCache.remove(username.toLowerCase());
        if (account == null) {
            try {
                account = databaseManager.getAccount(username).join().orElse(null);
            } catch (Exception e) {
                plugin.getLogger().severe("Emergency DB load failed for " + username + ": " + e.getMessage());
            }
        }

        if (account == null) {
            player.kick(plugin.getMessageManager().getMessage("general.error"));
            return;
        }

        account.setLastJoin(System.currentTimeMillis());
        persistLastJoin(account, username);

        resolveJoinSession(player, account);
    }

    private void resolveJoinSession(Player player, Account account) {
        UUID uuid = player.getUniqueId();
        String username = player.getName();
        boolean wasSuccess = successfulVerifications.remove(username.toLowerCase());
        boolean wasFailed = failedVerifications.remove(username.toLowerCase());

        PlayerSession session = sessionManager.getSession(uuid);

        if (session == null) {
            boolean isPremiumPlayer = false;
            if (account.isPremiumEnabled() && account.getPremiumUuid() != null && uuid.equals(account.getPremiumUuid())) {
                isPremiumPlayer = true;
            }

            if (isPremiumPlayer) {
                plugin.getLogger().info("Creating premium session for verified player " + username);
                session = sessionManager.createSession(username, uuid, true);
            } else {
                plugin.getLogger().info("Creating non-premium session for " + username);
                session = sessionManager.createSession(username, uuid, false);
            }
        }

        applyJoinSession(player, account, session, wasSuccess, wasFailed);
    }

    private void applyJoinSession(Player player, Account account, PlayerSession session, boolean wasSuccess, boolean wasFailed) {
        if (isValidSession(session, account, player)) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.clearTitle();
            player.setFreezeTicks(0);
            session.setLocallyAuthenticated(true);

            if (session.isPremiumAuthenticated()) {
                player.sendMessage(plugin.getMessageManager().getMessage("premium.auto_login"));
            } else {
                player.sendMessage(plugin.getMessageManager().getMessage("limbo.session_restored"));
            }
            teleportToSpawn(player, "main");
            return;
        }

        if (wasFailed) {
            player.sendMessage(plugin.getMessageManager().getMessage("premium.failed"));
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
        plugin.getLogger().info("[Debug-Session] Checking session for: " + player.getName());
        plugin.getLogger().info("[Debug-Session] Player UUID: " + player.getUniqueId() + ", Session UUID: " + session.getUniqueId());
        plugin.getLogger().info("[Debug-Session] Session Premium Auth: " + session.isPremiumAuthenticated());
        plugin.getLogger().info("[Debug-Session] Account Premium Enabled: " + account.isPremiumEnabled());
        plugin.getLogger().info("[Debug-Session] Account Premium Uuid: " + account.getPremiumUuid());
        plugin.getLogger().info("[Debug-Session] Account Session Expires At: " + account.getSessionExpiresAt() + " (Current Time: " + System.currentTimeMillis() + ")");

        if (!player.getUniqueId().equals(session.getUniqueId())) {
            plugin.getLogger().info("[Debug-Session] Session Invalid: UUID mismatch");
            return false;
        }

        if (session.isPremiumAuthenticated()) {
            boolean valid = account.isPremiumEnabled()
                    && account.getPremiumUuid() != null
                    && player.getUniqueId().equals(account.getPremiumUuid());
            plugin.getLogger().info("[Debug-Session] Session Premium Valid: " + valid);
            return valid;
        }

        boolean valid = !account.isPremiumEnabled()
                && account.getSessionExpiresAt() > 0
                && System.currentTimeMillis() < account.getSessionExpiresAt();
        plugin.getLogger().info("[Debug-Session] Session Offline Valid: " + valid);
        return valid;
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
        UUID uuid = event.getPlayer().getUniqueId();
        String username = event.getPlayer().getName();
        sessionManager.removeSession(uuid);
        pendingAccountsCache.remove(username.toLowerCase());
        successfulVerifications.remove(username.toLowerCase());
        failedVerifications.remove(username.toLowerCase());
    }

    private boolean isNotAuthenticated(Player player) {
        return !sessionManager.isAuthenticated(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (isNotAuthenticated(event.getPlayer())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                Location authSpawn = plugin.getConfigManager().getSpawn("auth");
                if (authSpawn != null) {
                    event.setTo(authSpawn);
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && isNotAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHealthRegen(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player && isNotAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (isNotAuthenticated(event.getPlayer())) {
            event.getCommands().removeIf(cmd -> {
                String baseCmd = "/" + cmd.split(" ")[0].toLowerCase(Locale.ROOT);
                return !ALLOWED_COMMANDS.contains(baseCmd);
            });
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

