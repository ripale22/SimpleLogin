package com.premiumauth.simplelogin.commands;

import com.premiumauth.simplelogin.SimpleLoginPlugin;
import com.premiumauth.simplelogin.models.Account;
import com.premiumauth.simplelogin.utils.LoginRateLimiter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.Map;

public class LoginCommand implements CommandExecutor, TabCompleter {

    private final SimpleLoginPlugin plugin;

    public LoginCommand(SimpleLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("errors.only_players"));
            return true;
        }

        if (!player.hasPermission("simplelogin.login")) {
            player.sendMessage(plugin.getMessageManager().getMessage("errors.no_permission"));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(plugin.getMessageManager().getMessage("auth.login_usage"));
            return true;
        }

        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        LoginRateLimiter rateLimiter = plugin.getLoginRateLimiter();

        if (rateLimiter.isBlocked(ip)) {
            long remaining = rateLimiter.getRemainingCooldown(ip) / 1000;
            player.sendMessage(plugin.getMessageManager().getMessage("auth.too_many_attempts", Map.of("seconds", String.valueOf(remaining))));
            return true;
        }

        String password = args[0];

        plugin.getDatabaseManager().getAccount(player.getName()).thenAccept(optAccount -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (optAccount.isEmpty()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("general.error"));
                    return;
                }

                Account acc = optAccount.get();
                if (!acc.isRegistered()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("auth.not_registered"));
                    return;
                }

                if (BCrypt.checkpw(password, acc.getPasswordHash())) {
                    rateLimiter.recordSuccess(ip);
                    plugin.completeAuthentication(player, acc);
                    player.sendMessage(plugin.getMessageManager().getMessage("auth.login_success"));
                } else {
                    rateLimiter.recordFailure(ip);
                    int remaining = 5 - 1;
                    if (remaining > 0) {
                        player.sendMessage(plugin.getMessageManager().getMessage("auth.wrong_password_remaining", Map.of("remaining", String.valueOf(remaining))));
                    } else {
                        player.sendMessage(plugin.getMessageManager().getMessage("auth.wrong_password"));
                    }
                }
            });
        });

        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
