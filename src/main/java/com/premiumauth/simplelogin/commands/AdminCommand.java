package com.premiumauth.simplelogin.commands;

import com.premiumauth.simplelogin.SimpleLoginPlugin;
import com.premiumauth.simplelogin.models.Account;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final SimpleLoginPlugin plugin;

    public AdminCommand(SimpleLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("simplelogin.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("errors.no_permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "setspawn":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("errors.only_players"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.getMessageManager().getMessage("admin.setspawn_usage"));
                    return true;
                }
                String type = args[1].toLowerCase(Locale.ROOT);
                if (!type.equals("auth") && !type.equals("main")) {
                    player.sendMessage(plugin.getMessageManager().getMessage("admin.setspawn_invalid_type"));
                    return true;
                }
                plugin.getConfigManager().setSpawn(type, player.getLocation());
                player.sendMessage(plugin.getMessageManager().getMessage("admin.setspawn_success", Map.of("type", type)));
                break;

            case "unregister":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("admin.unregister_usage"));
                    return true;
                }
                String targetUnreg = args[1];
                plugin.getDatabaseManager().getAccount(targetUnreg).thenAccept(optAcc -> {
                    if (optAcc.isEmpty()) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin.unregister_not_found"));
                        return;
                    }
                    Account acc = optAcc.get();
                    acc.setPasswordHash(null);
                    acc.setSessionToken(null);
                    acc.setSessionExpiresAt(0);
                    acc.setPremium(false);
                    acc.setPremiumEnabled(false);
                    acc.setVerificationPending(false);
                    acc.setPremiumUuid(null);
                    plugin.getDatabaseManager().updateAccount(acc).thenRun(() -> {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin.unregister_success", Map.of("player", targetUnreg)));
                    });
                });
                break;

            case "forcepremium":
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("admin.forcepremium_usage"));
                    return true;
                }
                String targetPrem = args[1];
                plugin.getDatabaseManager().getAccount(targetPrem).thenAccept(optAcc -> {
                    if (optAcc.isEmpty()) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin.forcepremium_not_found"));
                        return;
                    }
                    Account acc = optAcc.get();
                    boolean newStatus = !acc.isPremiumEnabled();
                    acc.setPremiumEnabled(newStatus);
                    acc.setVerificationPending(false);
                    plugin.getDatabaseManager().updateAccount(acc).thenRun(() -> {
                        sender.sendMessage(plugin.getMessageManager().getMessage(
                                newStatus ? "admin.forcepremium_enabled" : "admin.forcepremium_disabled",
                                Map.of("player", targetPrem)));
                    });
                });
                break;

            case "reload":
                plugin.getConfigManager().reloadConfig();
                plugin.getMessageManager().loadMessages();
                sender.sendMessage(plugin.getMessageManager().getMessage("admin.reload_success"));
                break;

            case "backup":
                if (args.length >= 2 && "restore".equalsIgnoreCase(args[1])) {
                    if (args.length < 3) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin.backup_restore_usage"));
                        return true;
                    }
                    String filename = args[2];
                    if (!filename.endsWith(".db") && !filename.endsWith(".sql")) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin.backup_invalid_file"));
                        return true;
                    }
                    sender.sendMessage(plugin.getMessageManager().getMessage("admin.backup_restoring"));
                    plugin.getDatabaseManager().restoreBackup(filename).thenRun(() -> {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin.backup_restore_success", Map.of("file", filename)));
                    }).exceptionally(ex -> {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin.backup_restore_error", Map.of("error", ex.getMessage())));
                        return null;
                    });
                } else if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
                    List<String> backups = plugin.getDatabaseManager().listBackups();
                    sender.sendMessage(plugin.getMessageManager().getMessage("admin.backup_list_header"));
                    if (backups.isEmpty()) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin.backup_list_empty"));
                    } else {
                        for (String b : backups) {
                            sender.sendMessage(plugin.getMessageManager().getMessage("admin.backup_list_item", Map.of("file", b)));
                        }
                    }
                } else {
                    sender.sendMessage(plugin.getMessageManager().getMessage("admin.backup_creating"));
                    plugin.getDatabaseManager().backupDatabase().thenAccept(fname -> {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin.backup_success", Map.of("file", fname)));
                    }).exceptionally(ex -> {
                        sender.sendMessage(plugin.getMessageManager().getMessage("admin.backup_error", Map.of("error", ex.getMessage())));
                        return null;
                    });
                }
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_header"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_setspawn"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_unregister"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_forcepremium"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_reload"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_backup"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("simplelogin.admin")) return List.of();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> subs = new ArrayList<>(List.of("setspawn", "unregister", "forcepremium", "reload", "backup"));
            return subs.stream().filter(s -> s.startsWith(partial)).toList();
        }
        if (args.length == 2 && "backup".equalsIgnoreCase(args[0])) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            List<String> opts = new ArrayList<>(List.of("list", "restore"));
            return opts.stream().filter(s -> s.startsWith(partial)).toList();
        }
        if (args.length == 3 && "backup".equalsIgnoreCase(args[0]) && "restore".equalsIgnoreCase(args[1])) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            return plugin.getDatabaseManager().listBackups().stream()
                    .filter(f -> f.toLowerCase().startsWith(partial))
                    .toList();
        }
        if (args.length == 2 && "setspawn".equalsIgnoreCase(args[0])) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return List.of("main", "auth").stream().filter(s -> s.startsWith(partial)).toList();
        }
        return List.of();
    }
}
