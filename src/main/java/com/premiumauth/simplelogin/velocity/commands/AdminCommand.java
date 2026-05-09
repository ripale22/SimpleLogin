package com.premiumauth.simplelogin.velocity.commands;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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
    private static final List<String> SUBCOMMANDS = List.of("unregister", "forcepremium", "reload", "setspawn", "resetip", "setip", "status", "resetpassword", "info");
    private static final List<String> SPAWN_TYPES = List.of("main", "auth");
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
            source.sendMessage(Component.text("No tienes permiso para usar este comando.", NamedTextColor.RED));
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
            case "reload" -> handleReload(source);
            case "setspawn" -> handleSetSpawn(source, args);
            case "resetip" -> handleResetIp(source, args);
            case "setip" -> handleSetIp(source, args);
            case "status" -> handleStatus(source, args);
            case "resetpassword" -> handleResetPassword(source, args);
            case "info" -> handleInfo(source);
            default -> {
            source.sendMessage(Component.text("Subcomando desconocido. Usa /sl para ver ayuda.", NamedTextColor.RED));
                sendUsage(source);
            }
        }
    }

    private void sendUsage(CommandSource source) {
        source.sendMessage(Component.text("===== SimpleLogin Admin =====", NamedTextColor.GOLD));
        source.sendMessage(Component.text("/sl unregister <jugador> - Elimina cuenta", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl forcepremium <jugador> - Fuerza premium", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl reload - Recarga config", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl setspawn <main|auth> - Ubicación de aparición", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl resetip <jugador> - Libera IP vinculada", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl setip <jugador> - Actualiza IP vinculada a la actual", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl status <jugador> - Muestra estado de cuenta", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl resetpassword <jugador> - Genera contraseña temporal", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl info - Diagnóstico rápido", NamedTextColor.YELLOW));
    }

    private void handleUnregister(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Uso: /sl unregister <jugador>", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        plugin.getLogger().info("[Admin] {} haciendo unregister de '{}'", getSenderName(source), targetName);

        plugin.getDatabaseManager().unregisterAccount(targetName).orTimeout(5, TimeUnit.SECONDS).whenComplete((v, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null) {
                    source.sendMessage(Component.text("Error eliminando cuenta de " + targetName, NamedTextColor.RED));
                    plugin.getLogger().error("[Admin] Error unregister '{}'", targetName, ex);
                    return;
                }
                source.sendMessage(Component.text("Cuenta de ", NamedTextColor.GREEN)
                        .append(Component.text(targetName, NamedTextColor.YELLOW))
                        .append(Component.text(" eliminada.", NamedTextColor.GREEN)));
                Optional<Player> online = plugin.getProxy().getPlayer(targetName);
                online.ifPresent(p -> p.disconnect(Component.text("Cuenta eliminada por admin. Reconecta.", NamedTextColor.RED)));
            }).schedule();
        });
    }

    private void handleForcePremium(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Uso: /sl forcepremium <jugador>", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        plugin.getLogger().info("[Admin] {} haciendo forcepremium de '{}'", getSenderName(source), targetName);

        plugin.getDatabaseManager().forcePremium(targetName, true).orTimeout(5, TimeUnit.SECONDS).whenComplete((v, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null) {
                    source.sendMessage(Component.text("Error forzando premium de " + targetName, NamedTextColor.RED));
                    plugin.getLogger().error("[Admin] Error forcepremium '{}'", targetName, ex);
                    return;
                }
                plugin.setPremiumStatus(targetName, true);
                source.sendMessage(Component.text("Premium forzado para ", NamedTextColor.GREEN)
                        .append(Component.text(targetName, NamedTextColor.YELLOW))
                        .append(Component.text(". Debe reconectar.", NamedTextColor.GREEN)));
                Optional<Player> online = plugin.getProxy().getPlayer(targetName);
                online.ifPresent(p -> p.disconnect(Component.text("Premium activado por admin. Reconecta.", NamedTextColor.GREEN)));
            }).schedule();
        });
    }

    private void handleReload(CommandSource source) {
        plugin.getLogger().info("[Admin] {} recargando config", getSenderName(source));
        plugin.getConfigManager().reload();
        plugin.getMessageManager().reload();
        source.sendMessage(Component.text("Config recargada.", NamedTextColor.GREEN));
    }

    private void handleSetSpawn(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Uso: /sl setspawn <main|auth>", NamedTextColor.RED));
            return;
        }
        String type = args[1].toLowerCase();
        if (!type.equals("main") && !type.equals("auth")) {
            source.sendMessage(Component.text("Usa 'main' o 'auth'.", NamedTextColor.RED));
            return;
        }

        if (!(source instanceof Player player) || player.getCurrentServer().isEmpty()) {
            source.sendMessage(Component.text("Debes estar conectado a un servidor backend.", NamedTextColor.RED));
            return;
        }

        String serverName = player.getCurrentServer().get().getServerInfo().getName();
        if ("main".equals(type)) {
            plugin.getConfigManager().setMainSpawnServer(serverName);
        } else {
            plugin.getConfigManager().setAuthSpawnServer(serverName);
        }

        if (sendSetSpawnMessage(player, type)) {
            plugin.getLogger().info("[Admin] {} actualizó spawn {} en '{}'", getSenderName(source), type, serverName);
            return;
        }

        source.sendMessage(Component.text("Servidor destino guardado como '" + serverName + "', pero Paper no respondió para guardar la ubicación. Verifica que SimpleLogin esté instalado en ese backend.", NamedTextColor.YELLOW));
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
            player.sendMessage(Component.text("No se pudo enviar setspawn al servidor backend.", NamedTextColor.RED));
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
            if ("unregister".equals(sub) || "forcepremium".equals(sub) || "resetip".equals(sub) || "setip".equals(sub)
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
        }
        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    private void handleResetIp(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Uso: /sl resetip <jugador>", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        plugin.getLogger().info("[Admin] {} reseteando IP de '{}'", getSenderName(source), targetName);

        plugin.getDatabaseManager().resetRegisteredIp(targetName).orTimeout(5, TimeUnit.SECONDS).whenComplete((v, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null) {
                    source.sendMessage(Component.text("Error reseteando IP de " + targetName, NamedTextColor.RED));
                    plugin.getLogger().error("[Admin] Error reseteando IP de '{}'", targetName, ex);
                    return;
                }
                source.sendMessage(Component.text("IP vinculada de ", NamedTextColor.GREEN)
                        .append(Component.text(targetName, NamedTextColor.YELLOW))
                        .append(Component.text(" liberada. Podrá reconectar desde cualquier IP.", NamedTextColor.GREEN)));
            }).schedule();
        });
    }

    private void handleSetIp(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Uso: /sl setip <jugador>", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        Optional<Player> target = plugin.getProxy().getPlayer(targetName);
        if (target.isEmpty()) {
            source.sendMessage(Component.text("Jugador '" + targetName + "' no está online.", NamedTextColor.RED));
            return;
        }
        String currentIp = target.get().getRemoteAddress().getAddress().getHostAddress();
        plugin.getLogger().info("[Admin] {} seteando IP de '{}' a '{}'", getSenderName(source), targetName, currentIp);

        plugin.getDatabaseManager().updateRegisteredIp(targetName, currentIp).orTimeout(5, TimeUnit.SECONDS).whenComplete((v, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null) {
                    source.sendMessage(Component.text("Error seteando IP de " + targetName, NamedTextColor.RED));
                    plugin.getLogger().error("[Admin] Error seteando IP de '{}'", targetName, ex);
                    return;
                }
                source.sendMessage(Component.text("IP vinculada de ", NamedTextColor.GREEN)
                        .append(Component.text(targetName, NamedTextColor.YELLOW))
                        .append(Component.text(" actualizada a: ", NamedTextColor.GREEN))
                        .append(Component.text(currentIp, NamedTextColor.YELLOW)));
            }).schedule();
        });
    }

    private String getSenderName(CommandSource source) {
        return source instanceof Player p ? p.getUsername() : "CONSOLE";
    }

    private void handleStatus(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Uso: /sl status <jugador>", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        plugin.getDatabaseManager().getAccountData(targetName).orTimeout(5, TimeUnit.SECONDS).whenComplete((opt, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null || opt.isEmpty()) {
                    source.sendMessage(Component.text("Cuenta no encontrada: " + targetName, NamedTextColor.RED));
                    return;
                }
                var account = opt.get();
                boolean online = plugin.getProxy().getPlayer(targetName).isPresent();
                boolean sessionActive = account.hasValidSession(account.getLastIp());
                source.sendMessage(Component.text("Estado de " + account.getUsername(), NamedTextColor.GOLD));
                source.sendMessage(Component.text("Registrado: " + yesNo(account.isRegistered()), NamedTextColor.YELLOW));
                source.sendMessage(Component.text("Tipo: " + (account.isPremiumEnabled() ? "PREMIUM" : "CRACKED"), NamedTextColor.YELLOW));
                source.sendMessage(Component.text("Online: " + yesNo(online), NamedTextColor.YELLOW));
                source.sendMessage(Component.text("IP vinculada: " + valueOrNone(account.getRegisteredIp()), NamedTextColor.YELLOW));
                source.sendMessage(Component.text("Sesión activa: " + yesNo(sessionActive), NamedTextColor.YELLOW));
            }).schedule();
        });
    }

    private void handleResetPassword(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Uso: /sl resetpassword <jugador>", NamedTextColor.RED));
            return;
        }
        String targetName = args[1];
        String temporaryPassword = "Temp" + Long.toString(System.currentTimeMillis(), 36);
        String hash = BCrypt.hashpw(temporaryPassword, BCrypt.gensalt(12));
        plugin.getDatabaseManager().updatePassword(targetName, hash).orTimeout(5, TimeUnit.SECONDS).whenComplete((v, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null) {
                    source.sendMessage(Component.text("Error reseteando contraseña de " + targetName, NamedTextColor.RED));
                    return;
                }
                plugin.getDatabaseManager().clearSession(targetName);
                source.sendMessage(Component.text("Contraseña temporal para " + targetName + ": " + temporaryPassword, NamedTextColor.GREEN));
                plugin.getProxy().getPlayer(targetName).ifPresent(player ->
                        player.disconnect(Component.text("Tu contraseña fue reiniciada por un administrador. Vuelve a entrar.", NamedTextColor.RED)));
            }).schedule();
        });
    }

    private void handleInfo(CommandSource source) {
        String main = plugin.getConfigManager().getMainSpawnServer();
        String auth = plugin.getConfigManager().getAuthSpawnServer();
        boolean limbo = plugin.getProxy().getPluginManager().getPlugin("limboapi").isPresent();
        source.sendMessage(Component.text("SimpleLogin 1.0.0-SNAPSHOT", NamedTextColor.GOLD));
        source.sendMessage(Component.text("BD: " + plugin.getConfigManager().getDatabaseType(), NamedTextColor.YELLOW));
        source.sendMessage(Component.text("LimboAPI: " + yesNo(limbo), NamedTextColor.YELLOW));
        source.sendMessage(Component.text("Server main: " + main + " (" + yesNo(plugin.getProxy().getServer(main).isPresent()) + ")", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("Server auth: " + auth + " (" + yesNo(plugin.getProxy().getServer(auth).isPresent()) + ")", NamedTextColor.YELLOW));
        if (source instanceof Player player && player.getCurrentServer().isPresent()) {
            if (!sendInfoRequest(player)) {
                player.sendMessage(Component.text("No se pudo consultar spawns de Paper. Instala esta misma versión en el backend.", NamedTextColor.YELLOW));
            }
        } else {
            source.sendMessage(Component.text("Para verificar spawns de Paper ejecuta /sl info dentro del backend.", NamedTextColor.YELLOW));
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

    private String yesNo(boolean value) {
        return value ? "SI" : "NO";
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "Ninguna" : value;
    }
}
