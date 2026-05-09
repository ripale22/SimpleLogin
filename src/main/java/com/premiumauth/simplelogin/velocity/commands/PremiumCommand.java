package com.premiumauth.simplelogin.velocity.commands;

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
            invocation.source().sendMessage(Component.text("Solo jugadores pueden usar este comando.", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("simplelogin.premium")) {
            player.sendMessage(plugin.getMessageManager().getMessage("errors.no_permission"));
            return;
        }

        String[] args = invocation.arguments().isEmpty() ? new String[0] : invocation.arguments().split(" ");

        if (args.length == 0) {
            player.sendMessage(Component.text("")
                    .append(Component.text("⚠ ", NamedTextColor.YELLOW))
                    .append(Component.text("Solo activar si tu cuenta es premium real. ", NamedTextColor.RED))
                    .append(Component.text("Usa /premium confirm para confirmar.", NamedTextColor.GOLD)));
            return;
        }

        if (args.length != 1 || !"confirm".equalsIgnoreCase(args[0])) {
            player.sendMessage(Component.text("Uso: /premium confirm", NamedTextColor.RED));
            return;
        }

        plugin.getDatabaseManager().getAccountData(player.getUsername()).orTimeout(5, TimeUnit.SECONDS).whenComplete((opt, ex) -> {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                if (ex != null || opt.isEmpty()) {
                    player.sendMessage(Component.text("Error al obtener tus datos o cuenta no registrada.", NamedTextColor.RED));
                    return;
                }

                var account = opt.get();
                if (!account.isRegistered()) {
                    player.sendMessage(Component.text("Debes estar registrado para activar el modo premium.", NamedTextColor.RED));
                    return;
                }

                boolean currentStatus = account.isPremiumEnabled();
                if (currentStatus) {
                    player.sendMessage(Component.text("El modo premium ya esta activado.", NamedTextColor.YELLOW));
                    return;
                }

                plugin.getDatabaseManager().setPremiumEnabled(player.getUsername(), true).whenComplete((v, err) -> {
                    plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                        if (err != null) {
                            player.sendMessage(Component.text("Ocurrio un error al actualizar tu estado.", NamedTextColor.RED));
                            return;
                        }

                        plugin.setPremiumStatus(player.getUsername(), true);
                        player.disconnect(Component.text("¡Modo premium activado! Vuelve a entrar para validar tu cuenta.", NamedTextColor.GREEN));
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
