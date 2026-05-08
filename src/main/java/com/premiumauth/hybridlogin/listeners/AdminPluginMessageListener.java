package com.premiumauth.hybridlogin.listeners;

import com.premiumauth.hybridlogin.HybridLoginPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

public class AdminPluginMessageListener implements PluginMessageListener {

    private static final String SET_SPAWN = "SET_SPAWN";
    private static final String INFO_REQUEST = "INFO_REQUEST";
    private static final String INFO_RESPONSE = "INFO_RESPONSE";

    private final HybridLoginPlugin plugin;

    public AdminPluginMessageListener(HybridLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!"simplelogin:admin".equals(channel)) {
            return;
        }
        if (!player.hasPermission("simplelogin.admin")) {
            player.sendMessage(plugin.getMessageManager().getMessage("errors.no_permission"));
            return;
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            String action = input.readUTF();
            if (INFO_REQUEST.equals(action)) {
                sendInfoResponse(player, input.readUTF());
                return;
            }
            if (!SET_SPAWN.equals(action)) {
                return;
            }

            String type = input.readUTF().toLowerCase(Locale.ROOT);
            if (!type.equals("main") && !type.equals("auth")) {
                player.sendMessage(Component.text("Tipo invalido. Usa 'auth' o 'main'.", NamedTextColor.RED));
                return;
            }

            plugin.getConfigManager().setSpawn(type, player.getLocation());
            player.sendMessage(Component.text("Spawn '" + type + "' seteado en tu ubicacion actual.", NamedTextColor.GREEN));
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo procesar mensaje admin desde Velocity: " + e.getMessage());
        }
    }

    private void sendInfoResponse(Player player, String requesterName) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(INFO_RESPONSE);
            output.writeUTF(requesterName);
            output.writeBoolean(plugin.getConfigManager().getSpawn("main") != null);
            output.writeBoolean(plugin.getConfigManager().getSpawn("auth") != null);
        }
        player.sendPluginMessage(plugin, "simplelogin:admin", bytes.toByteArray());
    }
}
