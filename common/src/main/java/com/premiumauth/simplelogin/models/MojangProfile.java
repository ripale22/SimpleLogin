package com.premiumauth.simplelogin.models;

import java.util.UUID;

public class MojangProfile {
    private String id;
    private String name;

    public MojangProfile() {}

    public MojangProfile(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getUniqueId() {
        if (id == null || id.length() != 32) return null;
        try {
            String formattedId = id.substring(0, 8) + "-" +
                    id.substring(8, 12) + "-" +
                    id.substring(12, 16) + "-" +
                    id.substring(16, 20) + "-" +
                    id.substring(20, 32);
            return UUID.fromString(formattedId);
        } catch (Exception e) {
            return null;
        }
    }
}
