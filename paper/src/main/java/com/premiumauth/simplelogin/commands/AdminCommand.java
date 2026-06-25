package com.premiumauth.simplelogin.commands;

import com.premiumauth.simplelogin.SimpleLoginPlugin;
import com.premiumauth.simplelogin.models.Account;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

            case "fixinv":
                String dataPath = args.length >= 2 ? args[1] : null;
                handleFixInventories(sender, dataPath);
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

            case "migrate":
                if (args.length < 3) {
                    sender.sendMessage("Uso: /sl migrate <authme> <sqlite|mysql> [argumentos...]");
                    sender.sendMessage("SQLite: /sl migrate authme sqlite <nombre_archivo_db>");
                    sender.sendMessage("MySQL: /sl migrate authme mysql <host> <port> <database> <user> <password> [useSSL] [tableName]");
                    return true;
                }
                
                String pluginType = args[1].toLowerCase(Locale.ROOT);
                if (!pluginType.equals("authme")) {
                    sender.sendMessage("§cPlugin no soportado para migración: " + pluginType + ". Actualmente solo soportamos 'authme'.");
                    return true;
                }
                
                String dbType = args[2].toLowerCase(Locale.ROOT);
                if (dbType.equals("sqlite")) {
                    if (args.length < 4) {
                        sender.sendMessage("§cPor favor especifica el nombre del archivo SQLite (ej. authme.db colocado en la carpeta de SimpleLogin).");
                        return true;
                    }
                    String dbFileName = args[3];
                    File dbFile = new File(plugin.getDataFolder(), dbFileName);
                    if (!dbFile.exists()) {
                        File authmeDir = new File(plugin.getDataFolder().getParentFile(), "AuthMe");
                        File alternativeFile = new File(authmeDir, dbFileName);
                        if (alternativeFile.exists()) {
                            dbFile = alternativeFile;
                        }
                    }
                    
                    if (!dbFile.exists()) {
                        sender.sendMessage("§cEl archivo " + dbFileName + " no se encontró en " + plugin.getDataFolder().getAbsolutePath() + " ni en plugins/AuthMe/.");
                        return true;
                    }
                    
                    File finalDbFile = dbFile;
                    sender.sendMessage("§aIniciando migración asíncrona desde SQLite: " + finalDbFile.getName() + "...");
                    
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            List<com.premiumauth.simplelogin.database.MigratedAccount> migrated = com.premiumauth.simplelogin.database.AuthMeMigrator.extractFromSQLite(finalDbFile);
                            sender.sendMessage("§aExtraídas " + migrated.size() + " cuentas. Insertando en la base de datos de SimpleLogin...");
                            
                            int batchSize = 500;
                            for (int i = 0; i < migrated.size(); i += batchSize) {
                                List<com.premiumauth.simplelogin.database.MigratedAccount> subList = migrated.subList(i, Math.min(i + batchSize, migrated.size()));
                                plugin.getDatabaseManager().bulkInsertAccounts(subList).join();
                            }
                            sender.sendMessage("§a¡Migración completada exitosamente! Se procesaron " + migrated.size() + " cuentas (se omitieron las duplicadas).");
                        } catch (Exception e) {
                            sender.sendMessage("§cError durante la migración: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                } else if (dbType.equals("mysql")) {
                    if (args.length < 8) {
                        sender.sendMessage("Uso MySQL: /sl migrate authme mysql <host> <port> <database> <user> <password> [useSSL=true/false] [tableName=authme]");
                        return true;
                    }
                    String host = args[3];
                    int port;
                    try {
                        port = Integer.parseInt(args[4]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cPuerto inválido.");
                        return true;
                    }
                    String database = args[5];
                    String user = args[6];
                    String password = args[7];
                    boolean useSSL = args.length >= 9 && Boolean.parseBoolean(args[8]);
                    String tableName = args.length >= 10 ? args[9] : "authme";
                    
                    sender.sendMessage("§aIniciando migración asíncrona desde MySQL " + host + ":" + port + "/" + database + "...");
                    
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            List<com.premiumauth.simplelogin.database.MigratedAccount> migrated = com.premiumauth.simplelogin.database.AuthMeMigrator.extractFromMySQL(host, port, database, tableName, user, password, useSSL);
                            sender.sendMessage("§aExtraídas " + migrated.size() + " cuentas de MySQL. Insertando en SimpleLogin...");
                            
                            int batchSize = 500;
                            for (int i = 0; i < migrated.size(); i += batchSize) {
                                List<com.premiumauth.simplelogin.database.MigratedAccount> subList = migrated.subList(i, Math.min(i + batchSize, migrated.size()));
                                plugin.getDatabaseManager().bulkInsertAccounts(subList).join();
                            }
                            sender.sendMessage("§a¡Migración de MySQL completada exitosamente! Se procesaron " + migrated.size() + " cuentas.");
                        } catch (Exception e) {
                            sender.sendMessage("§cError durante la migración: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                } else {
                    sender.sendMessage("§cTipo de base de datos no soportado: " + dbType + ". Usa 'sqlite' o 'mysql'.");
                }
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleFixInventories(CommandSender sender, String customPath) {
        sender.sendMessage("Buscando inventarios a migrar...");
        plugin.getDatabaseManager().getAllAccounts().thenAccept(accounts -> {
            if (accounts.isEmpty()) {
                sender.sendMessage("No hay cuentas en la base de datos.");
                return;
            }

            World world = plugin.getServer().getWorlds().get(0);
            File worldFolder = world.getWorldFolder();
            File dataDir = null;

            if (customPath != null && !customPath.isEmpty()) {
                for (String p : new String[]{customPath,
                        worldFolder.getAbsolutePath() + "/" + customPath,
                        worldFolder.getParentFile().getAbsolutePath() + "/" + customPath}) {
                    File f = new File(p);
                    sender.sendMessage("Probando: " + f.getAbsolutePath());
                    if (f.isDirectory()) { dataDir = f; break; }
                }
            }

            if (dataDir == null || !dataDir.isDirectory()) {
                for (String p : new String[]{
                        worldFolder.getAbsolutePath() + "/playerdata",
                        worldFolder.getAbsolutePath() + "/players/data",
                        worldFolder.getAbsolutePath().replaceAll("/world$", "") + "/world/players/data",
                        "world/players/data",
                        "world/playerdata"
                }) {
                    File f = new File(p);
                    sender.sendMessage("Probando: " + f.getAbsolutePath());
                    if (f.isDirectory()) { dataDir = f; break; }
                }
            }

            if (dataDir == null || !dataDir.isDirectory()) {
                sender.sendMessage("ERROR: No se encontro la carpeta de datos.");
                sender.sendMessage("Prueba: /sl fixinv world/players/data");
                return;
            }

            sender.sendMessage("Usando: " + dataDir.getAbsolutePath());

            int renamed = 0;
            int skipped = 0;

            for (Account acc : accounts) {
                String username = acc.getUsername();
                UUID newUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
                UUID oldUuid = null;

                if (acc.isPremiumEnabled() && acc.getPremiumUuid() != null) {
                    oldUuid = acc.getPremiumUuid();
                } else if (acc.isPremiumEnabled() && acc.getOfflineUuid() != null) {
                    oldUuid = acc.getOfflineUuid();
                }

                if (oldUuid != null && !oldUuid.equals(newUuid)) {
                    if (tryRename(dataDir, sender, username, oldUuid, newUuid)) renamed++; else skipped++;
                } else if (acc.isPremiumEnabled() && acc.getPremiumUuid() == null && acc.getOfflineUuid() == null) {
                    sender.sendMessage("SKIP " + username + ": premium sin UUID (usar /sl forcepremium luego de entrar)");
                    skipped++;
                }
            }

            sender.sendMessage("=== Completado ===");
            sender.sendMessage("Renombrados: " + renamed + "  Saltados: " + skipped);
        }).exceptionally(ex -> {
            sender.sendMessage("ERROR: " + ex.getMessage());
            return null;
        });
    }

    private boolean tryRename(File dataDir, CommandSender sender, String username, UUID oldUuid, UUID newUuid) {
        if (oldUuid.equals(newUuid)) return false;
        File oldFile = new File(dataDir, oldUuid.toString() + ".dat");
        File newFile = new File(dataDir, newUuid.toString() + ".dat");
        if (!oldFile.exists()) {
            sender.sendMessage("SKIP " + username + ": no existe " + oldUuid);
            return false;
        }
        if (newFile.exists()) {
            sender.sendMessage("SKIP " + username + ": ya existe " + newUuid);
            return false;
        }
        if (oldFile.renameTo(newFile)) {
            sender.sendMessage("OK " + username + ": " + oldUuid + " -> " + newUuid);
            return true;
        }
        sender.sendMessage("FAIL " + username + ": no se pudo renombrar");
        return false;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_header"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_setspawn"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_unregister"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_forcepremium"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_reload"));
        sender.sendMessage(plugin.getMessageManager().getMessage("admin.help_backup"));
        sender.sendMessage("<yellow>/sl fixinv</yellow> <white>- Migrar inventarios premium</white>");
        sender.sendMessage("<yellow>/sl migrate</yellow> <white>- Migrar base de datos de otros plugins (AuthMe)</white>");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("simplelogin.admin")) return List.of();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> subs = new ArrayList<>(List.of("setspawn", "unregister", "forcepremium", "reload", "backup", "fixinv", "migrate"));
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
        if (args.length == 2 && "migrate".equalsIgnoreCase(args[0])) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return List.of("authme").stream().filter(s -> s.startsWith(partial)).toList();
        }
        if (args.length == 3 && "migrate".equalsIgnoreCase(args[0]) && "authme".equalsIgnoreCase(args[1])) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            return List.of("sqlite", "mysql").stream().filter(s -> s.startsWith(partial)).toList();
        }
        return List.of();
    }
}
