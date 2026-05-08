package com.premiumauth.hybridlogin.commands;

import com.premiumauth.hybridlogin.HybridLoginPlugin;
import com.premiumauth.hybridlogin.models.Account;
import com.premiumauth.hybridlogin.utils.LoginRateLimiter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;

public class LoginCommand implements CommandExecutor, TabCompleter {

    private final HybridLoginPlugin plugin;

    public LoginCommand(HybridLoginPlugin plugin) {
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
            player.sendMessage(Component.text("Uso: /login <contrasena>").color(NamedTextColor.RED));
            return true;
        }

        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        LoginRateLimiter rateLimiter = plugin.getLoginRateLimiter();

        if (rateLimiter.isBlocked(ip)) {
            long remaining = rateLimiter.getRemainingCooldown(ip) / 1000;
            player.sendMessage(Component.text("Demasiados intentos fallidos. Espera " + remaining + " segundos antes de intentar de nuevo.").color(NamedTextColor.RED));
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
                        player.sendMessage(Component.text("Contraseña incorrecta. Intentos restantes: " + remaining).color(NamedTextColor.RED));
                    } else {
                        player.sendMessage(Component.text("Contraseña incorrecta. Has sido bloqueado temporalmente.").color(NamedTextColor.RED));
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
