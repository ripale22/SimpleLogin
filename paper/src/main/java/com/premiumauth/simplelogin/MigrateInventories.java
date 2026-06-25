package com.premiumauth.simplelogin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

public class MigrateInventories {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Uso: java -cp simplelogin.jar;sqlite.jar MigrateInventories <database.db> <world>");
            System.out.println("Ejemplo: java MigrateInventories plugins/simplelogin/database.db world");
            System.exit(1);
        }
        String dbPath = args[0];
        String worldName = args[1];

        Class.forName("org.sqlite.JDBC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT username, premium_uuid, offline_uuid, is_premium FROM accounts");
            Path playerdataDir = Paths.get(worldName, "playerdata");
            Path advancementsDir = Paths.get(worldName, "advancements");
            Path statsDir = Paths.get(worldName, "stats");

            if (!Files.isDirectory(playerdataDir)) {
                System.out.println("ERROR: No se encuentra " + playerdataDir);
                System.exit(1);
            }

            int renamed = 0;
            int skipped = 0;

            while (rs.next()) {
                String username = rs.getString("username");
                String premiumUuidStr = rs.getString("premium_uuid");
                String offlineUuidStr = rs.getString("offline_uuid");
                boolean isPremium = rs.getInt("is_premium") == 1;

                UUID oldUuid = null;
                if (isPremium && premiumUuidStr != null && !premiumUuidStr.isEmpty()) {
                    oldUuid = UUID.fromString(premiumUuidStr);
                } else if (offlineUuidStr != null && !offlineUuidStr.isEmpty()) {
                    UUID storedOffline = UUID.fromString(offlineUuidStr);
                    UUID newOffline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes("UTF-8"));
                    if (!storedOffline.equals(newOffline)) {
                        oldUuid = storedOffline;
                    }
                }

                if (oldUuid == null) {
                    skipped++;
                    continue;
                }

                UUID newUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes("UTF-8"));

                String oldName = oldUuid.toString();
                String newName = newUuid.toString();

                Path oldDat = playerdataDir.resolve(oldName + ".dat");
                Path newDat = playerdataDir.resolve(newName + ".dat");
                if (Files.exists(oldDat) && !Files.exists(newDat)) {
                    Files.move(oldDat, newDat);
                    System.out.println("OK: " + username + "  " + oldName + ".dat -> " + newName + ".dat");
                    renamed++;
                } else if (Files.exists(newDat)) {
                    System.out.println("SKIP: " + username + " (ya existe " + newName + ".dat)");
                    skipped++;
                }

                Path oldAdv = advancementsDir.resolve(oldName + ".json");
                Path newAdv = advancementsDir.resolve(newName + ".json");
                if (Files.exists(oldAdv) && !Files.exists(newAdv)) {
                    Files.move(oldAdv, newAdv);
                    System.out.println("OK: " + username + "  advancements/" + oldName + ".json -> " + newName + ".json");
                }

                Path oldStats = statsDir.resolve(oldName + ".json");
                Path newStats = statsDir.resolve(newName + ".json");
                if (Files.exists(oldStats) && !Files.exists(newStats)) {
                    Files.move(oldStats, newStats);
                    System.out.println("OK: " + username + "  stats/" + oldName + ".json -> " + newName + ".json");
                }
            }

            System.out.println("\n=== RESUMEN ===");
            System.out.println("Renombrados: " + renamed);
            System.out.println("Saltados: " + skipped);
        }
    }
}
