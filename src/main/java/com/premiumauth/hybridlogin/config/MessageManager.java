package com.premiumauth.hybridlogin.config;

import com.premiumauth.hybridlogin.HybridLoginPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {
    private final HybridLoginPlugin plugin;
    private FileConfiguration messages;
    private File messagesFile;
    private final Map<String, String> cache = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public MessageManager(HybridLoginPlugin plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            try (InputStream in = plugin.getResource("messages.yml")) {
                if (in != null) {
                    Files.copy(in, messagesFile.toPath());
                } else {
                    messagesFile.createNewFile();
                }
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear messages.yml: " + e.getMessage());
            }
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);
        addDefaults();
        cache.clear();
        for (String key : messages.getKeys(true)) {
            if (messages.isString(key)) {
                cache.put(key, messages.getString(key));
            }
        }
    }

    private void addDefaults() {
        messages.addDefault("prefix", "<gradient:#00b4d8:#48cae4>SimpleLogin</gradient>");
        messages.addDefault("errors.no_permission", "<red><bold>✘</bold></red> <gray>No tienes permiso para ejecutar este comando.</gray>");
        messages.addDefault("errors.only_players", "<red><bold>✘</bold></red> <gray>Este comando solo puede ser usado por jugadores.</gray>");
        messages.addDefault("auth.must_login", "<yellow><bold>!</bold></yellow> <gray>Debes autenticarte. Usa <white>/login</white> o <white>/register</white>.</gray>");
        messages.addDefault("auth.login_success", "{prefix} <green><bold>✔</bold></green> <gray>Inicio de sesión exitoso.</gray>");
        messages.addDefault("auth.register_success", "{prefix} <green><bold>✔</bold></green> <gray>Cuenta creada y sesión iniciada.</gray>");
        messages.addDefault("auth.already_logged_in", "{prefix} <yellow><bold>!</bold></yellow> <gray>Ya estás autenticado.</gray>");
        messages.addDefault("auth.wrong_password", "{prefix} <red><bold>✘</bold></red> <gray>Contraseña incorrecta.</gray>");
        messages.addDefault("auth.not_registered", "{prefix} <yellow><bold>!</bold></yellow> <gray>No estás registrado. Usa <white>/register</white>.</gray>");
        messages.addDefault("auth.passwords_dont_match", "{prefix} <red><bold>✘</bold></red> <gray>Las contraseñas no coinciden.</gray>");
        messages.addDefault("premium.warning", "<red><bold>⚠ ADVERTENCIA DE SEGURIDAD</bold></red>\n<gray>Este comando activará el modo Premium para tu cuenta.</gray>\n<gray>Si NO tienes Minecraft comprado oficialmente, </gray><red>PERDERÁS EL ACCESO</red><gray> a tu cuenta.</gray>\n<gray>Para confirmar, escribe: </gray><green>/premium confirm</green>");
        messages.addDefault("premium.verifying", "<yellow>⟳ Verificando cuenta premium con Mojang...</yellow>");
        messages.addDefault("premium.already_verified", "<yellow><bold>!</bold></yellow> <gray>Tu cuenta ya está verificada como premium.</gray>");
        messages.addDefault("premium.not_found", "<red><bold>✘</bold></red> <gray>No se encontró una cuenta premium asociada a tu nombre.</gray>");
        messages.addDefault("premium.kick_success", "<green><bold>✔ Verificación Iniciada</bold></green>\n\n<gray>Hemos vinculado tu cuenta con los servidores de Mojang.</gray>\n<gray>Para completar la activación, vuelve a entrar</gray>\n<gray>usando tu launcher oficial de Minecraft.</gray>");
        messages.addDefault("premium.auto_login", "{prefix} <green><bold>✔</bold></green> <gray>Verificación premium completada. Auto-login activado.</gray>");
        messages.addDefault("premium.failed", "<red><bold>✘</bold></red> <gray>La verificación premium falló. Usa tu launcher oficial.</gray>");
        messages.addDefault("general.error", "<red><bold>✘</bold></red> <gray>Ha ocurrido un error. Contacta a la administración.</gray>");
        
        messages.options().copyDefaults(true);
        saveMessages();
    }

    public void saveMessages() {
        try {
            messages.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar messages.yml: " + e.getMessage());
        }
    }

    public Component getMessage(String path, Map<String, String> placeholders) {
        String raw = cache.getOrDefault(path, messages.getString(path, "<red>Missing message: " + path + "</red>"));
        String prefix = cache.getOrDefault("prefix", "<gradient:#00b4d8:#48cae4>SimpleLogin</gradient>");
        
        raw = raw.replace("{prefix}", prefix);
        
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        // Soporta colores clasicos & y MiniMessage
        if (raw.contains("&")) {
            return legacySerializer.deserialize(raw);
        }
        return miniMessage.deserialize(raw);
    }

    public Component getMessage(String path) {
        return getMessage(path, null);
    }
}
