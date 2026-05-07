package com.premiumauth.hybridlogin.velocity.commands;

import com.premiumauth.hybridlogin.velocity.HybridLoginVelocity;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AdminCommand implements RawCommand {

    private final HybridLoginVelocity plugin;
    private static final List<String> SUBCOMMANDS = List.of("unregister", "forcepremium", "reload", "setspawn", "resetip", "setip");
    private static final List<String> SPAWN_TYPES = List.of("main", "auth");

    public AdminCommand(HybridLoginVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments().isEmpty() ? new String[0] : invocation.arguments().split(" ");

        plugin.getLogger().info("[AdminCommand] Ejecutado por '{}' con args: {}",
                source instanceof Player p ? p.getUsername() : "CONSOLE",
                String.join(" ", args));

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
            default -> {
            source.sendMessage(Component.text("Subcomando desconocido. Usa: unregister, forcepremium, reload, setspawn, resetip, setip", NamedTextColor.RED));
                sendUsage(source);
            }
        }
    }

    private void sendUsage(CommandSource source) {
        source.sendMessage(Component.text("===== SimpleLogin Admin =====", NamedTextColor.GOLD));
        source.sendMessage(Component.text("/sl unregister <jugador> - Elimina cuenta", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl forcepremium <jugador> - Fuerza premium", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl reload - Recarga config", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl setspawn <main|auth> - Servidor destino", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl resetip <jugador> - Libera IP vinculada", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/sl setip <jugador> - Actualiza IP vinculada a la actual", NamedTextColor.YELLOW));
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
        String serverName = null;

        if (source instanceof Player player && player.getCurrentServer().isPresent()) {
            serverName = player.getCurrentServer().get().getServerInfo().getName();
        }
        if (serverName == null) {
            source.sendMessage(Component.text("Debes estar conectado a un servidor backend.", NamedTextColor.RED));
            return;
        }

        if ("main".equals(type)) {
            plugin.getConfigManager().setMainSpawnServer(serverName);
            source.sendMessage(Component.text("Spawn MAIN: ", NamedTextColor.GREEN).append(Component.text(serverName, NamedTextColor.YELLOW)));
        } else if ("auth".equals(type)) {
            plugin.getConfigManager().setAuthSpawnServer(serverName);
            source.sendMessage(Component.text("Spawn AUTH: ", NamedTextColor.GREEN).append(Component.text(serverName, NamedTextColor.YELLOW)));
        } else {
            source.sendMessage(Component.text("Usa 'main' o 'auth'.", NamedTextColor.RED));
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
            if ("unregister".equals(sub) || "forcepremium".equals(sub) || "resetip".equals(sub) || "setip".equals(sub)) {
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
}
