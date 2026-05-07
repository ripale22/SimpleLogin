package com.premiumauth.hybridlogin.commands;

import com.premiumauth.hybridlogin.HybridLoginPlugin;
import com.premiumauth.hybridlogin.models.Account;
import com.premiumauth.hybridlogin.models.MojangProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Comando /premium con Handshake de Verificacion.
 * <p>Paso 1 del handshake:</p>
 * <ul>
 *   <li>Consulta Mojang para obtener el UUID premium real del nombre.</li>
 *   <li>Guarda el UUID y marca is_verification_pending=1.</li>
 *   <li>NO activa premium todavia; la activacion real ocurre en PlayerJoinEvent
 *       cuando Velocity forwarda el UUID legitimo.</li>
 * </ul>
 */
public class PremiumCommand implements CommandExecutor {

    private final HybridLoginPlugin plugin;

    public PremiumCommand(HybridLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("errors.only_players"));
            return true;
        }

        if (!player.hasPermission("simplelogin.premium")) {
            player.sendMessage(plugin.getMessageManager().getMessage("errors.no_permission"));
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            player.sendMessage(plugin.getMessageManager().getMessage("premium.warning"));
            return true;
        }

        String username = player.getName();
        player.sendMessage(plugin.getMessageManager().getMessage("premium.verifying"));

        plugin.getDatabaseManager().getAccount(username).thenAccept(optAccount -> {
            if (optAccount.isPresent() && optAccount.get().isPremiumEnabled()) {
                sync(() -> player.sendMessage(plugin.getMessageManager().getMessage("premium.already_verified")));
                return;
            }

            plugin.getMojangApiService().fetchPremiumProfile(username).thenAccept(optProfile -> {
                sync(() -> {
                    if (optProfile.isEmpty()) {
                        player.sendMessage(plugin.getMessageManager().getMessage("premium.not_found"));
                        return;
                    }

                    MojangProfile profile = optProfile.get();
                    UUID premiumUuid = profile.getUniqueId();
                    if (premiumUuid == null) {
                        player.sendMessage(plugin.getMessageManager().getMessage("general.error"));
                        return;
                    }

                    if (optAccount.isPresent()) {
                        Account account = optAccount.get();
                        account.setPremiumUuid(premiumUuid);
                        account.setVerificationPending(true);

                        plugin.getDatabaseManager().updateAccount(account).thenRun(() ->
                                sync(() -> player.kick(plugin.getMessageManager().getMessage("premium.kick_success")))
                        ).exceptionally(ex -> {
                            sync(() -> player.sendMessage(plugin.getMessageManager().getMessage("general.error")));
                            plugin.getLogger().severe("Error al iniciar verificacion premium para " + username + ": " + ex.getMessage());
                            return null;
                        });
                    } else {
                        plugin.getDatabaseManager().createAccount(username, player.getUniqueId()).thenRun(() ->
                                plugin.getDatabaseManager().getAccount(username).thenAccept(newOpt -> {
                                    if (newOpt.isEmpty()) {
                                        sync(() -> player.sendMessage(plugin.getMessageManager().getMessage("general.error")));
                                        return;
                                    }
                                    Account account = newOpt.get();
                                    account.setPremiumUuid(premiumUuid);
                                    account.setVerificationPending(true);

                                    plugin.getDatabaseManager().updateAccount(account).thenRun(() ->
                                            sync(() -> player.kick(plugin.getMessageManager().getMessage("premium.kick_success")))
                                    ).exceptionally(ex -> {
                                        sync(() -> player.sendMessage(plugin.getMessageManager().getMessage("general.error")));
                                        plugin.getLogger().severe("Error al iniciar verificacion premium para " + username + ": " + ex.getMessage());
                                        return null;
                                    });
                                })
                        ).exceptionally(ex -> {
                            sync(() -> player.sendMessage(plugin.getMessageManager().getMessage("general.error")));
                            plugin.getLogger().severe("Error creando cuenta para verificacion premium de " + username);
                            return null;
                        });
                    }
                });
            }).exceptionally(ex -> {
                sync(() -> player.sendMessage(plugin.getMessageManager().getMessage("general.error")));
                return null;
            });
        });

        return true;
    }

    private void sync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
