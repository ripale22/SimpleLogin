package com.premiumauth.simplelogin.velocity.listener;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;

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
                player.sendMessage(plugin.getMessageManager().getMessage("admin.info_spawn_main", Map.of("value", yesNo(mainSet))));
                player.sendMessage(plugin.getMessageManager().getMessage("admin.info_spawn_auth", Map.of("value", yesNo(authSet))));
            });
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        } catch (IOException e) {
            plugin.getLogger().warn("Could not read diagnostic response from Paper", e);
        }
    }

    private String yesNo(boolean value) {
        return plugin.getMessageManager().getString(value ? "admin.yes" : "admin.no");
    }
}
