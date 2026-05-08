package com.premiumauth.hybridlogin.velocity.limbo;

import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.player.LimboPlayer;
import com.premiumauth.hybridlogin.velocity.HybridLoginVelocity;
import com.premiumauth.hybridlogin.velocity.auth.VelocityAuthManager;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class AuthLimboSessionHandler implements LimboSessionHandler {

    private final HybridLoginVelocity plugin;
    private final AuthLimboManager limboManager;
    private final VelocityAuthManager authManager;
    private LimboPlayer limboPlayer;

    public AuthLimboSessionHandler(HybridLoginVelocity plugin, AuthLimboManager limboManager, VelocityAuthManager authManager) {
        this.plugin = plugin;
        this.limboManager = limboManager;
        this.authManager = authManager;
    }

    @Override
    public void onSpawn(Limbo server, LimboPlayer limboPlayer) {
        this.limboPlayer = limboPlayer;
        Player player = limboPlayer.getProxyPlayer();

        limboManager.registerLimboPlayer(player.getUniqueId(), limboPlayer);
        authManager.setPending(player.getUniqueId());
        plugin.getLogger().debug("[Limbo] {} spawneado en limbo nativo.", player.getUsername());
        scheduleAuthTimeout(player);

        plugin.getDatabaseManager().getAccountData(player.getUsername())
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((opt, ex) -> {
                    plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                        if (ex != null) {
                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
                            return;
                        }

                        var account = opt.orElse(null);
                        String currentIp = player.getRemoteAddress().getAddress().getHostAddress();

                        if (account != null && account.hasValidSession(currentIp)) {
                            authManager.authenticate(player.getUniqueId());
                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.session_restored"));
                            limboManager.sendToLobby(player);
                            return;
                        }

                        boolean needsRegister = account == null || !account.isRegistered();
                        sendAuthPrompt(player, needsRegister);
                    }).schedule();
                });
    }

    @Override
    public void onChat(String message) {
        Player player = limboPlayer.getProxyPlayer();
        if (message.startsWith("/")) {
            plugin.getProxy().getCommandManager().executeImmediatelyAsync(player, message.substring(1));
            return;
        }
        player.sendMessage(plugin.getMessageManager().getMessage("limbo.unknown_command"));
    }

    @Override
    public void onDisconnect() {
        if (limboPlayer != null) {
            Player player = limboPlayer.getProxyPlayer();
            limboManager.unregisterLimboPlayer(player.getUniqueId());
            if (authManager.isAuthenticated(player.getUniqueId())) {
                authManager.clearPending(player.getUniqueId());
            } else {
                authManager.remove(player.getUniqueId());
            }
            plugin.clearPremiumStatus(player.getUsername());
            plugin.getLogger().debug("[Limbo] {} desconectado del limbo nativo.", player.getUsername());
        }
    }

    private void sendAuthPrompt(Player player, boolean needsRegister) {
        String msgKey = needsRegister ? "limbo.register_prompt" : "limbo.login_prompt";
        Component prompt = plugin.getMessageManager().getMessage(msgKey);

        Title title = Title.title(
                Component.text("SimpleLogin").color(net.kyori.adventure.text.format.NamedTextColor.GOLD),
                prompt.color(net.kyori.adventure.text.format.NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(10), Duration.ofMillis(300))
        );
        player.showTitle(title);
        player.sendMessage(prompt);
    }

    private void scheduleAuthTimeout(Player player) {
        int timeout = plugin.getConfigManager().getLoginTimeoutSeconds();
        if (timeout <= 0) {
            return;
        }
        plugin.getProxy().getScheduler().buildTask(plugin, () -> {
            if (limboManager.isInLimbo(player.getUniqueId()) && authManager.isPending(player.getUniqueId())) {
                player.disconnect(plugin.getMessageManager().getMessage("limbo.login_timeout"));
            }
        }).delay(timeout, TimeUnit.SECONDS).schedule();
    }
}
