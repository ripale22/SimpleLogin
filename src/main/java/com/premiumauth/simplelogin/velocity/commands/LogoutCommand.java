package com.premiumauth.simplelogin.velocity.commands;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class LogoutCommand implements RawCommand {

    private final SimpleLoginVelocity plugin;

    public LogoutCommand(SimpleLoginVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(plugin.getMessageManager().getMessage("errors.only_players"));
            return;
        }

        plugin.getAuthManager().remove(player.getUniqueId());
        plugin.getDatabaseManager().clearSession(player.getUsername()).orTimeout(5, TimeUnit.SECONDS).whenComplete((v, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null) {
                    player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
                    return;
                }
                player.disconnect(plugin.getMessageManager().getMessage("auth.logout_success"));
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
