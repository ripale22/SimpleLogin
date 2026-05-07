package com.premiumauth.hybridlogin.velocity.listener;

import com.premiumauth.hybridlogin.velocity.HybridLoginVelocity;
import com.premiumauth.hybridlogin.velocity.auth.VelocityAuthManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;

/**
 * Limpia el estado de autenticacion cuando un jugador se desconecta del proxy.
 */
public class LimboListener {

    private final HybridLoginVelocity plugin;
    private final VelocityAuthManager authManager;

    public LimboListener(HybridLoginVelocity plugin, VelocityAuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        authManager.remove(event.getPlayer().getUniqueId());
        plugin.clearPremiumStatus(event.getPlayer().getUsername());
    }
}
