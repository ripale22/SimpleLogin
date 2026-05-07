package com.premiumauth.hybridlogin.models;

import java.util.UUID;

/**
 * DTO que representa la respuesta de las APIs de Mojang para un perfil de jugador.
 * Compatible con la serializacion de Gson.
 */
public class MojangProfile {

    private String id;
    private String name;

    /**
     * @return nombre de usuario devuelto por Mojang.
     */
    public String getName() {
        return name;
    }

    /**
     * @return UUID en formato string sin guiones (formato Mojang raw).
     */
    public String getId() {
        return id;
    }

    /**
     * Convierte el ID crudo de Mojang a un objeto {@link UUID} estandar de Java.
     *
     * @return UUID del jugador premium.
     */
    public UUID getUniqueId() {
        if (id == null || id.length() != 32) {
            return null;
        }
        String formatted = id.replaceFirst(
                "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                "$1-$2-$3-$4-$5"
        );
        return UUID.fromString(formatted);
    }
}
