package com.premiumauth.simplelogin.velocity.commands;

import com.premiumauth.simplelogin.models.Account;
import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.mindrot.jbcrypt.BCrypt;

public class AdminCommand implements RawCommand {

    private final SimpleLoginVelocity plugin;
    private static final List<String> SUBCOMMANDS = List.of("unregister", "forcepremium", "bypass", "reload", "setspawn", "status", "resetpassword", "info", "backup");
    private static final List<String> SPAWN_TYPES = List.of("main", "auth");
    private static final List<String> BACKUP_ACTIONS = List.of("list", "restore");
    private static final String SET_SPAWN = "SET_SPAWN";
    private static final String INFO_REQUEST = "INFO_REQUEST";

    public AdminCommand(SimpleLoginVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments().isEmpty() ? new String[0] : invocation.arguments().split(" ");

        if (!plugin.isAdmin(source)) {
            source.sendMessage(plugin.getMessageManager().getMessage("admin.no_permission"));
            return;
        }

        if (args.length == 0) {
            sendUsage(source);
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "unregister" -> handleUnregister(source, args);
            case "forcepremium" -> handleForcePremium(source, args);
            case "bypass" -> handleBypass(source, args);
            case "reload" -> handleReload(source);
            case "setspawn" -> handleSetSpawn(source, args);
            case "status" -> handleStatus(source, args);
            case "resetpassword" -> handleResetPassword(source, args);
            case "info" -> handleInfo(source);
            case "backup" -> handleBackup(source, args);
            default -> {
                source.sendMessage(plugin.getMessageManager().getMessage("admin.unknown_subcommand"));
                sendUsage(source);
            }
        }
    }

    private void sendUsage(CommandSource source) {
        source.sendMessage(plugin.getMessageManager().getMessage("admin.help_header"));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.help_unregister"));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.help_forcepremium"));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.help_bypass"));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.help_reload"));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.help_setspawn"));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.help_status"));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.help_resetpassword"));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.help_info"));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.help_backup"));
    }

    private void handleBypass(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(plugin.getMessageManager().getMessage("admin.bypass_usage"));
            return;
        }
        String targetName = args[1];
        plugin.getLogger().info("[Admin] {} toggling bypass for '{}'", getSenderName(source), targetName);

        plugin.getDatabaseManager().toggleBypass(targetName).orTimeout(5, TimeUnit.SECONDS).whenComplete((enabled, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null) {
                    source.sendMessage(plugin.getMessageManager().getMessage("admin.bypass_error", Map.of("player", targetName)));
                    plugin.getLogger().error("[Admin] Error toggling bypass for '{}'", targetName, ex);
                    return;
                }
                if (enabled) {
                    source.sendMessage(plugin.getMessageManager().getMessage("admin.bypass_enabled", Map.of("player", targetName)));
                } else {
                    source.sendMessage(plugin.getMessageManager().getMessage("admin.bypass_disabled", Map.of("player", targetName)));
                }
            }).schedule();
        });
    }

    private void handleUnregister(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(plugin.getMessageManager().getMessage("admin.unregister_usage"));
            return;
        }
        String targetName = args[1];
        plugin.getLogger().info("[Admin] {} unregistering '{}'", getSenderName(source), targetName);

        plugin.getDatabaseManager().unregisterAccount(targetName).orTimeout(5, TimeUnit.SECONDS).whenComplete((v, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null) {
                    source.sendMessage(plugin.getMessageManager().getMessage("admin.unregister_error", Map.of("player", targetName)));
                    plugin.getLogger().error("[Admin] Error unregister '{}'", targetName, ex);
                    return;
                }
                source.sendMessage(plugin.getMessageManager().getMessage("admin.unregister_success", Map.of("player", targetName)));
                Optional<Player> online = plugin.getProxy().getPlayer(targetName);
                online.ifPresent(p -> p.disconnect(plugin.getMessageManager().getMessage("admin.unregister_kick")));
            }).schedule();
        });
    }

    private void handleForcePremium(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(plugin.getMessageManager().getMessage("admin.forcepremium_usage"));
            return;
        }
        String targetName = args[1];
        plugin.getLogger().info("[Admin] {} force-premium '{}'", getSenderName(source), targetName);

        plugin.getDatabaseManager().forcePremium(targetName, true).orTimeout(5, TimeUnit.SECONDS).whenComplete((v, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null) {
                    source.sendMessage(plugin.getMessageManager().getMessage("admin.forcepremium_error", Map.of("player", targetName)));
                    plugin.getLogger().error("[Admin] Error forcepremium '{}'", targetName, ex);
                    return;
                }
                plugin.setPremiumStatus(targetName, true);
                source.sendMessage(plugin.getMessageManager().getMessage("admin.forcepremium_enabled", Map.of("player", targetName)));
                Optional<Player> online = plugin.getProxy().getPlayer(targetName);
                online.ifPresent(p -> p.disconnect(plugin.getMessageManager().getMessage("admin.forcepremium_kick")));
            }).schedule();
        });
    }

    private void handleReload(CommandSource source) {
        plugin.getLogger().info("[Admin] {} reloading config", getSenderName(source));
        plugin.getConfigManager().reload();
        plugin.getMessageManager().reload();
        source.sendMessage(plugin.getMessageManager().getMessage("admin.reload_success"));
    }

    private void handleSetSpawn(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(plugin.getMessageManager().getMessage("admin.setspawn_usage"));
            return;
        }
        String type = args[1].toLowerCase();
        if (!type.equals("main") && !type.equals("auth")) {
            source.sendMessage(plugin.getMessageManager().getMessage("admin.setspawn_invalid_type"));
            return;
        }

        if (!(source instanceof Player player) || player.getCurrentServer().isEmpty()) {
            source.sendMessage(plugin.getMessageManager().getMessage("admin.setspawn_not_connected"));
            return;
        }

        String serverName = player.getCurrentServer().get().getServerInfo().getName();
        if ("main".equals(type)) {
            plugin.getConfigManager().setMainSpawnServer(serverName);
        } else {
            plugin.getConfigManager().setAuthSpawnServer(serverName);
        }

        if (sendSetSpawnMessage(player, type)) {
            plugin.getLogger().info("[Admin] {} updated spawn {} at '{}'", getSenderName(source), type, serverName);
            source.sendMessage(plugin.getMessageManager().getMessage("admin.setspawn_backend_ok", Map.of("type", type)));
            return;
        }

        source.sendMessage(plugin.getMessageManager().getMessage("admin.setspawn_send_error"));
    }

    private boolean sendSetSpawnMessage(Player player, String type) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF(SET_SPAWN);
                output.writeUTF(type);
            }
            return player.getCurrentServer()
                    .map(server -> server.sendPluginMessage(SimpleLoginVelocity.ADMIN_CHANNEL, bytes.toByteArray()))
                    .orElse(false);
        } catch (IOException e) {
            player.sendMessage(plugin.getMessageManager().getMessage("admin.setspawn_send_error"));
            return false;
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!plugin.isAdmin(source)) {
            return List.of();
        }

        String[] args = invocation.arguments().isEmpty() ? new String[0] : invocation.arguments().split(" ");
        if (args.length == 0 || (args.length == 1 && args[0].isEmpty())) {
            return SUBCOMMANDS;
        }
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if ("unregister".equals(sub) || "forcepremium".equals(sub) || "bypass".equals(sub)
                        || "status".equals(sub) || "resetpassword".equals(sub)) {
                String partial = args[1].toLowerCase();
                return plugin.getProxy().getAllPlayers().stream()
                        .map(Player::getUsername)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
            if ("setspawn".equals(sub)) {
                String partial = args[1].toLowerCase();
                return SPAWN_TYPES.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
            }
            if ("backup".equals(sub)) {
                String partial = args[1].toLowerCase();
                return BACKUP_ACTIONS.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
            }
        }
        if (args.length == 3 && "backup".equalsIgnoreCase(args[0]) && "restore".equalsIgnoreCase(args[1])) {
            String partial = args[2].toLowerCase();
            return plugin.getDatabaseManager().listBackups().stream()
                    .filter(f -> f.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    private String getSenderName(CommandSource source) {
        return source instanceof Player p ? p.getUsername() : "CONSOLE";
    }

    private void handleStatus(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(plugin.getMessageManager().getMessage("admin.status_not_found", Map.of("player", "?")));
            return;
        }
        String targetName = args[1];
        plugin.getDatabaseManager().getAccount(targetName).orTimeout(5, TimeUnit.SECONDS).whenComplete((opt, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null || opt.isEmpty()) {
                    source.sendMessage(plugin.getMessageManager().getMessage("admin.status_not_found", Map.of("player", targetName)));
                    return;
                }
                var account = opt.get();
                boolean online = plugin.getProxy().getPlayer(targetName).isPresent();
                boolean sessionActive = account.hasValidSession();
                source.sendMessage(plugin.getMessageManager().getMessage("admin.status_header", Map.of("player", account.getUsername())));
                source.sendMessage(plugin.getMessageManager().getMessage("admin.status_registered", Map.of("value", yesNo(account.isRegistered()))));
                source.sendMessage(plugin.getMessageManager().getMessage("admin.status_type", Map.of("value", account.isPremiumEnabled() ? "PREMIUM" : "OFFLINE")));
                source.sendMessage(plugin.getMessageManager().getMessage("admin.status_online", Map.of("value", yesNo(online))));
                source.sendMessage(plugin.getMessageManager().getMessage("admin.status_ip", Map.of("value", valueOrNone(account.getRegisteredIp()))));
                source.sendMessage(plugin.getMessageManager().getMessage("admin.status_session", Map.of("value", yesNo(sessionActive))));
            }).schedule();
        });
    }

    private void handleResetPassword(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(plugin.getMessageManager().getMessage("admin.resetpassword_usage"));
            return;
        }
        String targetName = args[1];
        String temporaryPassword = "Temp" + Long.toString(System.currentTimeMillis(), 36);
        String hash = BCrypt.hashpw(temporaryPassword, BCrypt.gensalt(12));
        plugin.getDatabaseManager().updatePassword(targetName, hash).orTimeout(5, TimeUnit.SECONDS).whenComplete((v, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null) {
                    source.sendMessage(plugin.getMessageManager().getMessage("admin.resetpassword_error", Map.of("player", targetName)));
                    return;
                }
                plugin.getDatabaseManager().clearSession(targetName);
                source.sendMessage(plugin.getMessageManager().getMessage("admin.resetpassword_success", Map.of("player", targetName, "password", temporaryPassword)));
                plugin.getProxy().getPlayer(targetName).ifPresent(player ->
                        player.disconnect(plugin.getMessageManager().getMessage("admin.resetpassword_kick")));
            }).schedule();
        });
    }

    private void handleInfo(CommandSource source) {
        String main = plugin.getConfigManager().getMainSpawnServer();
        String auth = plugin.getConfigManager().getAuthSpawnServer();
        boolean limbo = plugin.getProxy().getPluginManager().getPlugin("limboapi").isPresent();
        source.sendMessage(plugin.getMessageManager().getMessage("admin.info_header", Map.of("version", "1.2.1")));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.info_db", Map.of("type", plugin.getConfigManager().getDatabaseType())));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.info_limbo", Map.of("status", yesNo(limbo))));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.info_main_server", Map.of("server", main + " (" + yesNo(plugin.getProxy().getServer(main).isPresent()) + ")")));
        source.sendMessage(plugin.getMessageManager().getMessage("admin.info_auth_server", Map.of("server", auth + " (" + yesNo(plugin.getProxy().getServer(auth).isPresent()) + ")")));
        if (source instanceof Player player && player.getCurrentServer().isPresent()) {
            if (!sendInfoRequest(player)) {
                player.sendMessage(plugin.getMessageManager().getMessage("admin.info_backend_error"));
            }
        } else {
            source.sendMessage(plugin.getMessageManager().getMessage("admin.info_run_inside"));
        }
    }

    private boolean sendInfoRequest(Player player) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF(INFO_REQUEST);
                output.writeUTF(player.getUsername());
            }
            return player.getCurrentServer()
                    .map(server -> server.sendPluginMessage(SimpleLoginVelocity.ADMIN_CHANNEL, bytes.toByteArray()))
                    .orElse(false);
        } catch (IOException e) {
            return false;
        }
    }

    private void handleBackup(CommandSource source, String[] args) {
        if (args.length >= 2 && "restore".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                source.sendMessage(plugin.getMessageManager().getMessage("admin.backup_restore_usage"));
                return;
            }
            String filename = args[2];
            if (!filename.endsWith(".db") && !filename.endsWith(".sql")) {
                source.sendMessage(plugin.getMessageManager().getMessage("admin.backup_invalid_file"));
                return;
            }
            source.sendMessage(plugin.getMessageManager().getMessage("admin.backup_restoring"));
            plugin.getLogger().info("[Admin] {} restoring backup: {}", getSenderName(source), filename);
            plugin.getDatabaseManager().restoreBackup(filename).thenRun(() -> {
                source.sendMessage(plugin.getMessageManager().getMessage("admin.backup_restore_success", Map.of("file", filename)));
            }).exceptionally(ex -> {
                source.sendMessage(plugin.getMessageManager().getMessage("admin.backup_restore_error", Map.of("error", ex.getMessage())));
                return null;
            });
        } else if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
            var backups = plugin.getDatabaseManager().listBackups();
            source.sendMessage(plugin.getMessageManager().getMessage("admin.backup_list_header"));
            if (backups.isEmpty()) {
                source.sendMessage(plugin.getMessageManager().getMessage("admin.backup_list_empty"));
            } else {
                for (String b : backups) {
                    source.sendMessage(plugin.getMessageManager().getMessage("admin.backup_list_item", Map.of("file", b)));
                }
            }
        } else {
            source.sendMessage(plugin.getMessageManager().getMessage("admin.backup_creating"));
            plugin.getLogger().info("[Admin] {} creating backup", getSenderName(source));
            plugin.getDatabaseManager().backupDatabase().thenAccept(fname -> {
                source.sendMessage(plugin.getMessageManager().getMessage("admin.backup_success", Map.of("file", fname)));
            }).exceptionally(ex -> {
                source.sendMessage(plugin.getMessageManager().getMessage("admin.backup_error", Map.of("error", ex.getMessage())));
                return null;
            });
        }
    }

    private String yesNo(boolean value) {
        return plugin.getMessageManager().getString(value ? "admin.yes" : "admin.no");
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? plugin.getMessageManager().getString("admin.none") : value;
    }
}
