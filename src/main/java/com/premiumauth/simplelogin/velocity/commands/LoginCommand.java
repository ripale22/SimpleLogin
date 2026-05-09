package com.premiumauth.simplelogin.velocity.commands;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.premiumauth.simplelogin.velocity.auth.VelocityAuthManager;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class LoginCommand implements RawCommand {

    private final SimpleLoginVelocity plugin;
    private final VelocityAuthManager authManager;

    public LoginCommand(SimpleLoginVelocity plugin, VelocityAuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
            return;
        }

        if (!player.hasPermission("simplelogin.login")) {
            player.sendMessage(plugin.getMessageManager().getMessage("errors.no_permission"));
            return;
        }

        String[] args = invocation.arguments().isEmpty() ? new String[0] : invocation.arguments().split(" ");
        if (args.length < 1 || args[0].isEmpty()) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.login_usage"));
            return;
        }

        String password = args[0];

        plugin.getDatabaseManager().getAccountData(player.getUsername()).orTimeout(10, TimeUnit.SECONDS).whenComplete((opt, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null || opt.isEmpty()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
                    return;
                }
                var account = opt.get();
                if (!account.isRegistered()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("limbo.not_registered"));
                    return;
                }

                // IP-Binding Security: verificar IP registrada
                String currentIp = player.getRemoteAddress().getAddress().getHostAddress();
                if (plugin.getConfigManager().isIpBindingEnabled() && account.hasRegisteredIp() && !account.ipMatches(currentIp)) {
                    player.sendMessage(plugin.getMessageManager().getMessage("limbo.ip_mismatch"));
                    plugin.getLogger().warn("[IP-Binding] {} intentó login desde IP distinta. Registrada: {}, Actual: {}",
                            player.getUsername(), account.getRegisteredIp(), currentIp);
                    return;
                }

                if (BCrypt.checkpw(password, account.getPasswordHash())) {
                    player.sendMessage(plugin.getMessageManager().getMessage("limbo.login_success"));
                    authManager.authenticate(player.getUniqueId());
                    long expires = System.currentTimeMillis() + (plugin.getConfigManager().getSessionDurationHours() * 3600_000L);
                    plugin.getDatabaseManager().updateSession(player.getUsername(), currentIp, expires).exceptionally(err -> {
                        plugin.getLogger().warn("No se pudo actualizar sesion de {}", player.getUsername(), err);
                        return null;
                    });
                    redirectToLobby(player);
                } else {
                    player.sendMessage(plugin.getMessageManager().getMessage("limbo.wrong_password"));
                }
            }).schedule();
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) return true;
        return player.hasPermission("simplelogin.login");
    }

    private void redirectToLobby(Player player) {
        String targetServer = plugin.getConfigManager().getMainSpawnServer();
        Optional<RegisteredServer> lobby = plugin.getProxy().getServer(targetServer);
        if (lobby.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
            plugin.getLogger().error("Servidor lobby '{}' no encontrado.", targetServer);
            return;
        }

        if (plugin.getAuthLimboManager() != null && plugin.getAuthLimboManager().isInLimbo(player.getUniqueId())) {
            plugin.getAuthLimboManager().redirectFromLimbo(player);
        } else {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                player.createConnectionRequest(lobby.get()).connect();
            }).delay(500, TimeUnit.MILLISECONDS).schedule();
        }
    }
}
