package com.premiumauth.simplelogin.auth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestiona las sesiones activas de jugadores en memoria.
 * Thread-safe mediante ConcurrentHashMap.
 */
public class SessionManager {

    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Registra una nueva sesion para un jugador.
     *
     * @param username               nombre del jugador.
     * @param uniqueId               UUID con el que entro al servidor.
     * @param premiumAuthenticated   true si fue validado como sesion premium legitima.
     * @return la sesion creada.
     */
    public PlayerSession createSession(String username, UUID uniqueId, boolean premiumAuthenticated) {
        PlayerSession session = new PlayerSession(username, uniqueId, premiumAuthenticated);
        activeSessions.put(uniqueId, session);
        return session;
    }

    /**
     * Obtiene la sesion activa de un jugador.
     *
     * @param uniqueId UUID del jugador.
     * @return la sesion o null si no existe.
     */
    public PlayerSession getSession(UUID uniqueId) {
        return activeSessions.get(uniqueId);
    }

    /**
     * Elimina la sesion de un jugador.
     *
     * @param uniqueId UUID del jugador.
     */
    public void removeSession(UUID uniqueId) {
        activeSessions.remove(uniqueId);
    }

    /**
     * Verifica si un jugador tiene una sesion premium valida activa.
     *
     * @param uniqueId UUID del jugador.
     * @return true si la sesion existe y fue autenticada como premium.
     */
    public boolean isPremiumAuthenticated(UUID uniqueId) {
        PlayerSession session = activeSessions.get(uniqueId);
        return session != null && session.isPremiumAuthenticated();
    }

    /**
     * Verifica si un jugador esta autenticado localmente (/login o /register)
     * o si es premium auto-autenticado.
     *
     * @param uniqueId UUID del jugador.
     * @return true si puede interactuar libremente.
     */
    public boolean isAuthenticated(UUID uniqueId) {
        PlayerSession session = activeSessions.get(uniqueId);
        return session != null && session.isLocallyAuthenticated();
    }

    /**
     * Marca una sesion como autenticada localmente.
     *
     * @param uniqueId UUID del jugador.
     */
    public void authenticate(UUID uniqueId) {
        PlayerSession session = activeSessions.get(uniqueId);
        if (session != null) {
            session.setLocallyAuthenticated(true);
        }
    }
}
