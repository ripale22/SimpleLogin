package com.premiumauth.simplelogin.velocity.listener;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.premiumauth.simplelogin.velocity.services.VelocityMojangService;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;

import java.util.concurrent.TimeUnit;

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
            plugin.getLogger().debug("[simplelogin] PreLogin: validating identity of '{}' from IP '{}'...", username, currentIp);

            if (plugin.getConnectionRateLimiter().recordAndIsBlocked(
                    currentIp,
                    username,
                    plugin.getConfigManager().getMaxConnectionsPerIp(),
                    plugin.getConfigManager().getConnectionWindowSeconds(),
                    plugin.getConfigManager().getConnectionCooldownSeconds(),
                    plugin.getConfigManager().getMaxNameAttempts())) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        plugin.getMessageManager().getMessage("velocity.too_many_connections")
                ));
                return;
            }

            try {
                var accountOpt = plugin.getDatabaseManager().getAccountData(username)
                        .orTimeout(5, TimeUnit.SECONDS).join();

                if (accountOpt.isPresent()) {
                    var account = accountOpt.get();
                    boolean isPremium = account.isPremium();

                    plugin.getLogger().debug("[simplelogin] '{}' already exists in DB. Status: {}",
                            username, isPremium ? "PREMIUM" : "CRACKED");

                    if (!account.hasRegisteredIp()) {
                        plugin.getDatabaseManager().updateRegisteredIp(username, currentIp)
                                .orTimeout(5, TimeUnit.SECONDS).join();
                        plugin.getLogger().debug("[IP-Binding] Account '{}' without prior IP. IP registered: {}", username, currentIp);
                    }

                    plugin.setPremiumStatus(username, isPremium);

                    if (isPremium) {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                    } else {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                    }
                    return;
                }

                plugin.getLogger().debug("[simplelogin] '{}' is new. Querying Mojang API...", username);

                boolean isPremium = mojangService.isPremium(username)
                        .orTimeout(8, TimeUnit.SECONDS).join();

                if (isPremium) {
                    plugin.getLogger().debug("[simplelogin] '{}' detected as PREMIUM by Mojang API.", username);

                    plugin.getDatabaseManager().registerAccount(username, null, "")
                            .orTimeout(5, TimeUnit.SECONDS).join();

                    plugin.getDatabaseManager().setPremiumEnabled(username, true)
                            .orTimeout(5, TimeUnit.SECONDS).join();

                    plugin.getDatabaseManager().updateRegisteredIp(username, currentIp)
                            .orTimeout(5, TimeUnit.SECONDS).join();
                    plugin.getLogger().debug("[IP-Binding] New premium player '{}'. IP registered: {}", username, currentIp);

                    plugin.setPremiumStatus(username, true);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                } else {
                    plugin.getLogger().debug("[simplelogin] '{}' detected as CRACKED by Mojang API.", username);

                    plugin.getDatabaseManager().registerAccount(
                                    username, null, ""
                            ).orTimeout(5, TimeUnit.SECONDS).join();

                    plugin.getDatabaseManager().updateRegisteredIp(username, currentIp)
                            .orTimeout(5, TimeUnit.SECONDS).join();
                    plugin.getLogger().debug("[IP-Binding] New cracked player '{}'. IP registered: {}", username, currentIp);

                    plugin.setPremiumStatus(username, false);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                }

            } catch (Exception e) {
                plugin.getLogger().error("[simplelogin] Error detecting status of '{}'. Connection refused for security.", username, e);
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        plugin.getMessageManager().getMessage("velocity.auth_error")
                ));
            }
        });
    }
}
