package com.premiumauth.hybridlogin.velocity.commands;

import com.premiumauth.hybridlogin.velocity.HybridLoginVelocity;
import com.premiumauth.hybridlogin.velocity.auth.VelocityAuthManager;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RegisterCommand implements RawCommand {

    private final HybridLoginVelocity plugin;
    private final VelocityAuthManager authManager;

    public RegisterCommand(HybridLoginVelocity plugin, VelocityAuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
            return;
        }

        if (!player.hasPermission("simplelogin.register")) {
            player.sendMessage(plugin.getMessageManager().getMessage("errors.no_permission"));
            return;
        }

        String[] args = invocation.arguments().isEmpty() ? new String[0] : invocation.arguments().split(" ");
        if (args.length < 2) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.register_usage"));
            return;
        }

        String pass = args[0];
        String confirm = args[1];

        if (!pass.equals(confirm)) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.passwords_no_match"));
            return;
        }
        if (pass.length() < 8) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.password_too_short"));
            return;
        }

        plugin.getDatabaseManager().getAccountData(player.getUsername()).orTimeout(10, TimeUnit.SECONDS).whenComplete((opt, ex) -> {
            if (ex != null) {
                plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                    player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
                }).schedule();
                return;
            }
            if (opt.isPresent() && opt.get().isRegistered()) {
                plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                    player.sendMessage(plugin.getMessageManager().getMessage("limbo.already_registered"));
                }).schedule();
                return;
            }

            String hash = BCrypt.hashpw(pass, BCrypt.gensalt(12));

            plugin.getDatabaseManager().registerAccount(player.getUsername(), player.getUniqueId(), hash)
                    .whenComplete((v, err) -> {
                        plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                            if (err != null) {
                                player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
                                return;
                            }
                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.register_success"));
                            authManager.authenticate(player.getUniqueId());
                            String ip = player.getRemoteAddress().getAddress().getHostAddress();
                            long expires = System.currentTimeMillis() + (plugin.getConfigManager().getSessionDurationHours() * 3600_000L);
                            plugin.getDatabaseManager().updateSession(player.getUsername(), ip, expires).exceptionally(sessionErr -> {
                                plugin.getLogger().warn("No se pudo actualizar sesion de {}", player.getUsername(), sessionErr);
                                return null;
                            });
                            plugin.getDatabaseManager().updateRegisteredIp(player.getUsername(), ip).exceptionally(ipErr -> {
                                plugin.getLogger().warn("No se pudo guardar IP registrada de {}", player.getUsername(), ipErr);
                                return null;
                            });
                            redirectToLobby(player);
                        }).schedule();
                    });
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) return true;
        return player.hasPermission("simplelogin.register");
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
