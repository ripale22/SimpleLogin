package com.premiumauth.hybridlogin.commands;

import com.premiumauth.hybridlogin.HybridLoginPlugin;
import com.premiumauth.hybridlogin.models.Account;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

public class AdminCommand implements CommandExecutor {

    private final HybridLoginPlugin plugin;

    public AdminCommand(HybridLoginPlugin plugin) {
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
                    player.sendMessage(Component.text("Uso: /sl setspawn <auth|main>").color(NamedTextColor.RED));
                    return true;
                }
                String type = args[1].toLowerCase(Locale.ROOT);
                if (!type.equals("auth") && !type.equals("main")) {
                    player.sendMessage(Component.text("Tipo invalido. Usa 'auth' o 'main'.").color(NamedTextColor.RED));
                    return true;
                }
                plugin.getConfigManager().setSpawn(type, player.getLocation());
                player.sendMessage(Component.text("Spawn '" + type + "' seteado en tu ubicacion actual.").color(NamedTextColor.GREEN));
                break;

            case "unregister":
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Uso: /sl unregister <jugador>").color(NamedTextColor.RED));
                    return true;
                }
                String targetUnreg = args[1];
                plugin.getDatabaseManager().getAccount(targetUnreg).thenAccept(optAcc -> {
                    if (optAcc.isEmpty()) {
                        sender.sendMessage(Component.text("Jugador no encontrado en la base de datos.").color(NamedTextColor.RED));
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
                        sender.sendMessage(Component.text("Cuenta de " + targetUnreg + " reseteada completamente.").color(NamedTextColor.GREEN));
                    });
                });
                break;

            case "forcepremium":
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Uso: /sl forcepremium <jugador>").color(NamedTextColor.RED));
                    return true;
                }
                String targetPrem = args[1];
                plugin.getDatabaseManager().getAccount(targetPrem).thenAccept(optAcc -> {
                    if (optAcc.isEmpty()) {
                        sender.sendMessage(Component.text("Jugador no encontrado en la base de datos.").color(NamedTextColor.RED));
                        return;
                    }
                    Account acc = optAcc.get();
                    boolean newStatus = !acc.isPremiumEnabled();
                    acc.setPremiumEnabled(newStatus);
                    acc.setVerificationPending(false);
                    plugin.getDatabaseManager().updateAccount(acc).thenRun(() -> {
                        sender.sendMessage(Component.text("Modo premium de " + targetPrem + " " + (newStatus ? "activado" : "desactivado") + ". Debe reconectar.").color(NamedTextColor.GREEN));
                    });
                });
                break;

            case "reload":
                plugin.getConfigManager().reloadConfig();
                plugin.getMessageManager().loadMessages();
                sender.sendMessage(Component.text("Configuraciones y mensajes recargados.").color(NamedTextColor.GREEN));
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Comandos de SimpleLogin ===").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/sl setspawn <auth|main>").color(NamedTextColor.YELLOW).append(Component.text(" - Setear spawns").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/sl unregister <user>").color(NamedTextColor.YELLOW).append(Component.text(" - Borrar contraseña").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/sl forcepremium <user>").color(NamedTextColor.YELLOW).append(Component.text(" - Alternar modo premium").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/sl reload").color(NamedTextColor.YELLOW).append(Component.text(" - Recargar config").color(NamedTextColor.WHITE)));
    }
}
