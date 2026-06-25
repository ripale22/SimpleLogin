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

public class RegisterCommand implements CommandExecutor, TabCompleter {

    private final SimpleLoginPlugin plugin;

    public RegisterCommand(SimpleLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("errors.only_players"));
            return true;
        }

        if (!player.hasPermission("simplelogin.register")) {
            player.sendMessage(plugin.getMessageManager().getMessage("errors.no_permission"));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(plugin.getMessageManager().getMessage("auth.register_usage"));
            return true;
        }

        String password = args[0];
        String confirm = args[1];

        if (!password.equals(confirm)) {
            player.sendMessage(plugin.getMessageManager().getMessage("auth.passwords_dont_match"));
            return true;
        }

        if (password.length() < plugin.getConfigManager().getMinPasswordLength()) {
            player.sendMessage(plugin.getMessageManager().getMessage("auth.password_too_short"));
            return true;
        }

        String currentIp = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";
        int maxAccounts = plugin.getConfigManager().getMaxAccountsPerIp();
        LoginRateLimiter rateLimiter = plugin.getLoginRateLimiter();

        if (rateLimiter.isBlocked(currentIp)) {
            long remaining = rateLimiter.getRemainingCooldown(currentIp) / 1000;
            player.sendMessage(plugin.getMessageManager().getMessage("auth.too_many_attempts", Map.of("seconds", String.valueOf(remaining))));
            return true;
        }

        plugin.getDatabaseManager().getAccountsCountByIp(currentIp).thenAccept(count -> {
            if (count >= maxAccounts) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(plugin.getMessageManager().getMessage("auth.max_accounts_per_ip", Map.of("max", String.valueOf(maxAccounts))));
                });
                return;
            }

            plugin.getDatabaseManager().getAccount(player.getName()).thenAccept(optAccount -> {
                if (optAccount.isPresent() && optAccount.get().isRegistered()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(plugin.getMessageManager().getMessage("auth.already_logged_in"));
                    });
                    return;
                }

                String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (optAccount.isPresent()) {
                        Account acc = optAccount.get();
                        acc.setPasswordHash(hash);
                        plugin.getDatabaseManager().updateAccount(acc).thenRun(() ->
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    rateLimiter.recordSuccess(currentIp);
                                    plugin.completeAuthentication(player, acc);
                                    player.sendMessage(plugin.getMessageManager().getMessage("auth.register_success"));
                                })
                        ).exceptionally(ex -> {
                            plugin.getLogger().severe("Error guardando registro de " + player.getName());
                            return null;
                        });
                    } else {
                        plugin.getDatabaseManager().createAccount(player.getName(), player.getUniqueId()).thenRun(() ->
                                plugin.getDatabaseManager().updatePassword(player.getName(), hash).thenRun(() ->
                                        plugin.getDatabaseManager().getAccount(player.getName()).thenAccept(newOpt -> {
                                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                newOpt.ifPresent(acc -> {
                                                    rateLimiter.recordSuccess(currentIp);
                                                    plugin.completeAuthentication(player, acc);
                                                });
                                                player.sendMessage(plugin.getMessageManager().getMessage("auth.register_success"));
                                            });
                                        })
                                )
                        ).exceptionally(ex -> {
                            plugin.getLogger().severe("Error creando cuenta para registro de " + player.getName());
                            return null;
                        });
                    }
                });
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
