package com.premiumauth.simplelogin.velocity.listener;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.premiumauth.simplelogin.velocity.services.VelocityMojangService;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;

import java.util.concurrent.TimeUnit;

/**
 * Listener de Velocity que decide AUTOMATICAMENTE si una conexion es online o offline.
 *
 * Lógica:
 * 1. Si el jugador YA existe en la BD con estado definido -> respetar ese estado (UUID consistente).
 * 2. Si es NUEVO -> consultar Mojang API automaticamente.
 *    - Si Mojang responde con perfil -> es PREMIUM -> forceOnlineMode.
 *    - Si Mojang no responde -> es CRACKED -> forceOfflineMode.
 * 3. Guardar el resultado en BD para futuras conexiones.
 *
 * Esta lógica garantiza:
 * - UUIDs estables (nunca cambian después de la primera detección).
 * - Detección automática sin /premium manual.
 * - Compatibilidad con LuckPerms (UUID consistente).
 */
public class PreLoginListener {

    private final SimpleLoginVelocity plugin;
    private final VelocityMojangService mojangService;

    public PreLoginListener(SimpleLoginVelocity plugin, VelocityMojangService mojangService) {
        this.plugin = plugin;
        this.mojangService = mojangService;
    }

    @Subscribe(order = PostOrder.FIRST)
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> {
            String username = event.getUsername();
            String currentIp = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
            plugin.getLogger().debug("[simplelogin] PreLogin: validando identidad de '{}' desde IP '{}'...", username, currentIp);

            if (plugin.getConnectionRateLimiter().recordAndIsBlocked(
                    currentIp,
                    username,
                    plugin.getConfigManager().getMaxConnectionsPerIp(),
                    plugin.getConfigManager().getConnectionWindowSeconds(),
                    plugin.getConfigManager().getConnectionCooldownSeconds(),
                    plugin.getConfigManager().getMaxNameAttempts())) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        net.kyori.adventure.text.Component.text("Demasiadas conexiones. Espera e inténtalo de nuevo.")
                                .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                ));
                return;
            }

            try {
                // Paso 1: Consultar BD para ver si ya tiene estado definido
                var accountOpt = plugin.getDatabaseManager().getAccountData(username)
                        .orTimeout(5, TimeUnit.SECONDS).join();

                if (accountOpt.isPresent()) {
                    var account = accountOpt.get();
                    boolean isPremium = account.isPremium();

                    plugin.getLogger().debug("[simplelogin] '{}' ya existe en BD. Estado: {}",
                            username, isPremium ? "PREMIUM" : "CRACKED");

                    // IP-Binding: si la cuenta no tiene IP registrada, actualizar ahora
                    if (!account.hasRegisteredIp()) {
                        plugin.getDatabaseManager().updateRegisteredIp(username, currentIp)
                                .orTimeout(5, TimeUnit.SECONDS).join();
                        plugin.getLogger().debug("[IP-Binding] Cuenta '{}' sin IP previa. IP registrada: {}", username, currentIp);
                    }

                    plugin.setPremiumStatus(username, isPremium);

                    if (isPremium) {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                    } else {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                    }
                    return;
                }

                // Paso 2: Jugador NUEVO -> consultar Mojang API automaticamente
                plugin.getLogger().debug("[simplelogin] '{}' es nuevo. Consultando Mojang API...", username);

                boolean isPremium = mojangService.isPremium(username)
                        .orTimeout(8, TimeUnit.SECONDS).join();

                if (isPremium) {
                    plugin.getLogger().debug("[simplelogin] '{}' detectado como PREMIUM por Mojang API.", username);

                    // Primero INSERTAR la fila en BD (sin password, ya que es premium)
                    plugin.getDatabaseManager().registerAccount(username, null, "")
                            .orTimeout(5, TimeUnit.SECONDS).join();

                    // Luego marcar como premium
                    plugin.getDatabaseManager().setPremiumEnabled(username, true)
                            .orTimeout(5, TimeUnit.SECONDS).join();

                    // IP-Binding: registrar IP del primer ingreso
                    plugin.getDatabaseManager().updateRegisteredIp(username, currentIp)
                            .orTimeout(5, TimeUnit.SECONDS).join();
                    plugin.getLogger().debug("[IP-Binding] Nuevo jugador premium '{}'. IP registrada: {}", username, currentIp);

                    plugin.setPremiumStatus(username, true);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                } else {
                    plugin.getLogger().debug("[simplelogin] '{}' detectado como CRACKED por Mojang API.", username);

                    // Guardar en BD como no-premium (con password vacio hasta que se registre)
                    plugin.getDatabaseManager().registerAccount(
                                    username, null, ""
                            ).orTimeout(5, TimeUnit.SECONDS).join();

                    // IP-Binding: registrar IP del primer ingreso
                    plugin.getDatabaseManager().updateRegisteredIp(username, currentIp)
                            .orTimeout(5, TimeUnit.SECONDS).join();
                    plugin.getLogger().debug("[IP-Binding] Nuevo jugador cracked '{}'. IP registrada: {}", username, currentIp);

                    plugin.setPremiumStatus(username, false);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                }

            } catch (Exception e) {
                plugin.getLogger().error("[simplelogin] Error detectando estado de '{}'. Conexión rechazada por seguridad.", username, e);
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        net.kyori.adventure.text.Component.text("Error de autenticación. Inténtalo de nuevo más tarde.")
                                .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                ));
            }
        });
    }
}
