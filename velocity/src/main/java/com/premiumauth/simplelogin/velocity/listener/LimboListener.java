package com.premiumauth.simplelogin.velocity.listener;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.premiumauth.simplelogin.velocity.auth.VelocityAuthManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;

/**
 * Limpia el estado de autenticacion cuando un jugador se desconecta del proxy.
 */
public class LimboListener {

    private final SimpleLoginVelocity plugin;
    private final VelocityAuthManager authManager;

    public LimboListener(SimpleLoginVelocity plugin, VelocityAuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        authManager.remove(event.getPlayer().getUniqueId());
        plugin.clearPremiumStatus(event.getPlayer().getUsername());
    }
}
