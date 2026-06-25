package com.premiumauth.simplelogin.velocity.commands;

import com.premiumauth.simplelogin.models.Account;
import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class PremiumCommand implements RawCommand {

    private final SimpleLoginVelocity plugin;
    private static final List<String> CONFIRM_SUGGESTION = List.of("confirm");

    public PremiumCommand(SimpleLoginVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(plugin.getMessageManager().getMessage("errors.only_players"));
            return;
        }

        if (!player.hasPermission("simplelogin.premium")) {
            player.sendMessage(plugin.getMessageManager().getMessage("errors.no_permission"));
            return;
        }

        String[] args = invocation.arguments().isEmpty() ? new String[0] : invocation.arguments().split(" ");

        if (args.length == 0) {
            player.sendMessage(plugin.getMessageManager().getMessage("premium.warning"));
            return;
        }

        if (args.length != 1 || !"confirm".equalsIgnoreCase(args[0])) {
            player.sendMessage(plugin.getMessageManager().getMessage("premium.usage"));
            return;
        }

        plugin.getDatabaseManager().getAccount(player.getUsername()).orTimeout(5, TimeUnit.SECONDS).whenComplete((opt, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null || opt.isEmpty()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("general.error"));
                    return;
                }

                var account = opt.get();
                if (!account.isRegistered()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("premium.not_registered"));
                    return;
                }

                boolean currentStatus = account.isPremiumEnabled();
                if (currentStatus) {
                    player.sendMessage(plugin.getMessageManager().getMessage("premium.already_verified"));
                    return;
                }

                plugin.getDatabaseManager().setPremiumEnabled(player.getUsername(), true).whenComplete((v, err) -> {
                    plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                        if (err != null) {
                            player.sendMessage(plugin.getMessageManager().getMessage("general.error"));
                            return;
                        }

                        plugin.setPremiumStatus(player.getUsername(), true);
                        player.disconnect(plugin.getMessageManager().getMessage("premium.kick_success"));
                    }).schedule();
                });
            }).schedule();
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments().isEmpty() ? new String[0] : invocation.arguments().split(" ");
        if (args.length == 0 || (args.length == 1 && args[0].isEmpty())) {
            return CONFIRM_SUGGESTION;
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) return true;
        return player.hasPermission("simplelogin.premium");
    }
}
