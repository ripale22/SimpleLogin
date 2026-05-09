package com.premiumauth.simplelogin.velocity.listener;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.DataInputStream;
import java.io.IOException;

public class AdminPluginMessageListener {

    private static final String INFO_RESPONSE = "INFO_RESPONSE";

    private final SimpleLoginVelocity plugin;

    public AdminPluginMessageListener(SimpleLoginVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(SimpleLoginVelocity.ADMIN_CHANNEL)) {
            return;
        }
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        try (DataInputStream input = new DataInputStream(event.dataAsInputStream())) {
            String action = input.readUTF();
            if (!INFO_RESPONSE.equals(action)) {
                return;
            }
            String requester = input.readUTF();
            boolean mainSet = input.readBoolean();
            boolean authSet = input.readBoolean();
            plugin.getProxy().getPlayer(requester).ifPresent(player -> {
                player.sendMessage(Component.text("Spawn main seteado en Paper: " + yesNo(mainSet), NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Spawn auth seteado en Paper: " + yesNo(authSet), NamedTextColor.YELLOW));
            });
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        } catch (IOException e) {
            plugin.getLogger().warn("No se pudo leer respuesta de diagnóstico desde Paper", e);
        }
    }

    private String yesNo(boolean value) {
        return value ? "SI" : "NO";
    }
}
