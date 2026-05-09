package com.premiumauth.simplelogin.velocity.commands;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ChangePasswordCommand implements RawCommand {

    private final SimpleLoginVelocity plugin;

    public ChangePasswordCommand(SimpleLoginVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(plugin.getMessageManager().getMessage("errors.only_players"));
            return;
        }

        String[] args = invocation.arguments().isEmpty() ? new String[0] : invocation.arguments().split(" ");
        if (args.length < 3) {
            player.sendMessage(plugin.getMessageManager().getMessage("auth.change_password_usage"));
            return;
        }

        String current = args[0];
        String next = args[1];
        String confirm = args[2];
        if (!next.equals(confirm)) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.passwords_no_match"));
            return;
        }
        if (next.length() < plugin.getConfigManager().getMinPasswordLength()) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.password_too_short"));
            return;
        }

        plugin.getDatabaseManager().getAccountData(player.getUsername()).orTimeout(10, TimeUnit.SECONDS).whenComplete((opt, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null || opt.isEmpty() || !opt.get().isRegistered()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("limbo.not_registered"));
                    return;
                }
                if (!BCrypt.checkpw(current, opt.get().getPasswordHash())) {
                    player.sendMessage(plugin.getMessageManager().getMessage("limbo.wrong_password"));
                    return;
                }
                String hash = BCrypt.hashpw(next, BCrypt.gensalt(12));
                plugin.getDatabaseManager().updatePassword(player.getUsername(), hash).whenComplete((v, err) -> {
                    plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                        if (err != null) {
                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
                            return;
                        }
                        player.sendMessage(plugin.getMessageManager().getMessage("auth.change_password_success"));
                    }).schedule();
                });
            }).schedule();
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("simplelogin.login");
    }
}
