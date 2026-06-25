package com.premiumauth.simplelogin.velocity.listener;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.premiumauth.simplelogin.velocity.services.VelocityMojangService;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;

import java.util.UUID;
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
                boolean isBypassed = plugin.getDatabaseManager().isBypassed(username)
                        .orTimeout(5, TimeUnit.SECONDS).join();
                if (isBypassed) {
                    plugin.getLogger().info("[simplelogin] '{}' is in premium bypass list. Forcing offline mode.", username);
                    var accountOpt = plugin.getDatabaseManager().getAccount(username)
                            .orTimeout(5, TimeUnit.SECONDS).join();
                    if (accountOpt.isEmpty()) {
                        plugin.getLogger().debug("[simplelogin] '{}' is bypassed but new. Registering as offline.", username);
                        plugin.getDatabaseManager().registerAccount(username, null, "")
                                .orTimeout(5, TimeUnit.SECONDS).join();
                    }
                    plugin.setPremiumStatus(username, false);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                    return;
                }

                var accountOpt = plugin.getDatabaseManager().getAccount(username)
                        .orTimeout(5, TimeUnit.SECONDS).join();

                if (accountOpt.isPresent()) {
                    var account = accountOpt.get();
                    boolean isPremium = account.isPremium();

                    plugin.getLogger().debug("[simplelogin] '{}' already exists in DB. Status: {}",
                            username, isPremium ? "PREMIUM" : "OFFLINE");

                    plugin.setPremiumStatus(username, isPremium);

                    if (isPremium) {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                    } else {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                    }
                    return;
                }

                plugin.getLogger().debug("[simplelogin] '{}' is new. Querying Mojang API...", username);

                var profileOpt = mojangService.fetchProfile(username)
                        .orTimeout(8, TimeUnit.SECONDS).join();

                if (profileOpt.isPresent()) {
                    var profile = profileOpt.get();
                    UUID premiumUuid = profile.getUniqueId();
                    plugin.getLogger().debug("[simplelogin] '{}' detected as PREMIUM by Mojang API (UUID: {}).", username, premiumUuid);

                    plugin.getDatabaseManager().registerAccount(username, null, "")
                            .orTimeout(5, TimeUnit.SECONDS).join();

                    plugin.getDatabaseManager().setPremiumPending(username, premiumUuid)
                            .orTimeout(5, TimeUnit.SECONDS).join();

                    plugin.getLogger().debug("[simplelogin] New premium player '{}' registered with verification pending.", username);

                    plugin.setPremiumStatus(username, true);
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
                } else {
                    plugin.getLogger().debug("[simplelogin] '{}' detected as OFFLINE by Mojang API.", username);

                    plugin.getDatabaseManager().registerAccount(
                                    username, null, ""
                            ).orTimeout(5, TimeUnit.SECONDS).join();

                    plugin.getLogger().debug("[simplelogin] New offline player '{}' registered.", username);

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
