package com.premiumauth.simplelogin.listeners;

import com.premiumauth.simplelogin.SimpleLoginPlugin;
import com.premiumauth.simplelogin.database.DatabaseManager;
import com.premiumauth.simplelogin.models.Account;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;

/**
 * Listener que completa el Handshake de Verificacion Premium.
 * <p>Paso 3 del handshake:</p>
 * <ul>
 *   <li>Velocity ya forzo online-mode (Paso 2) si is_verification_pending=1.</li>
 *   <li>Si el jugador entra con el UUID premium real forwardado por Velocity,
 *       se confirma is_premium=1, premium_enabled=1 y se limpia pending.</li>
 *   <li>Si entra con UUID offline, la prueba falla y se limpia pending.</li>
 * </ul>
 */
public class PremiumVerificationListener implements Listener {

    private final SimpleLoginPlugin plugin;
    private final DatabaseManager databaseManager;

    public PremiumVerificationListener(SimpleLoginPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();

        plugin.getLogger().info("[simplelogin-Paper] Evaluando PlayerJoinEvent para: " + username);

        databaseManager.getAccount(username).thenAccept(optAccount -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (optAccount.isEmpty()) {
                    plugin.getLogger().info("[simplelogin-Paper] No hay cuenta en BD para: " + username);
                    return;
                }

                Account account = optAccount.get();
                if (!account.isVerificationPending()) {
                    plugin.getLogger().info("[simplelogin-Paper] " + username + " no tiene verificación pendiente. Ignorando.");
                    return;
                }

                plugin.getLogger().info("[simplelogin-Paper] ¡Verificación pendiente detectada para " + username + "!");

                if (account.getPremiumUuid() == null) {
                    plugin.getLogger().warning("[simplelogin-Paper] Estado inconsistente (Premium UUID null) para: " + username);
                    // Estado inconsistente: limpiar pending.
                    account.setVerificationPending(false);
                    databaseManager.updateAccount(account);
                    return;
                }

                if (player.getUniqueId().equals(account.getPremiumUuid())) {
                    plugin.getLogger().info("[simplelogin-Paper] UUID coincide (" + player.getUniqueId() + "). Completando verificación premium para: " + username);
                    // VERIFICACION EXITOSA: Velocity forwardo el UUID premium real.
                    account.setPremium(true);
                    account.setPremiumEnabled(true);
                    account.setVerificationPending(false);

                    databaseManager.updateAccount(account).thenRun(() ->
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    plugin.getSessionManager().createSession(username, player.getUniqueId(), true);
                                    player.sendMessage(plugin.getMessageManager().getMessage("premium.auto_login"));
                            })
                    ).exceptionally(ex -> {
                        plugin.getLogger().severe("Error confirmando verificacion premium de " + username + ": " + ex.getMessage());
                        return null;
                    });
                } else {
                    plugin.getLogger().info("[simplelogin-Paper] UUID NO coincide para " + username + " (Online: " + player.getUniqueId() + ", BD: " + account.getPremiumUuid() + "). Limpiando pending.");
                    // VERIFICACION FALLIDA: entro con UUID offline (launcher cracked).
                    account.setVerificationPending(false);
                    databaseManager.updateAccount(account).thenRun(() ->
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    plugin.getSessionManager().createSession(username, player.getUniqueId(), false);
                                    player.sendMessage(plugin.getMessageManager().getMessage("premium.failed"));
                                    player.sendMessage(plugin.getMessageManager().getMessage("auth.must_login"));
                            })
                    ).exceptionally(ex -> {
                        plugin.getLogger().severe("Error limpiando verificacion pendiente de " + username + ": " + ex.getMessage());
                        return null;
                    });
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[simplelogin-Paper] Error obteniendo datos de cuenta para " + username + ": " + ex.getMessage());
            return null;
        });
    }
}
