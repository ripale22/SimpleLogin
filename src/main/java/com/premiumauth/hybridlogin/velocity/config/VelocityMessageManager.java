package com.premiumauth.hybridlogin.velocity.config;

import com.premiumauth.hybridlogin.velocity.HybridLoginVelocity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class VelocityMessageManager {

    private final HybridLoginVelocity plugin;
    private final Path messagesPath;
    private CommentedConfigurationNode root;
    private final Map<String, String> cache = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public VelocityMessageManager(HybridLoginVelocity plugin) {
        this.plugin = plugin;
        this.messagesPath = plugin.getDataDirectory().resolve("messages.yml");
        loadMessages();
    }

    private void loadMessages() {
        try {
            if (!Files.exists(messagesPath)) {
                Files.createDirectories(plugin.getDataDirectory());
                // Fallback a crear el archivo por defecto si no existe en los recursos
                try (var stream = getClass().getResourceAsStream("/messages.yml")) {
                    if (stream != null) {
                        Files.copy(stream, messagesPath);
                    } else {
                        Files.createFile(messagesPath);
                    }
                }
            }
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(messagesPath).build();
            root = loader.load();
            
            addDefaults(loader);
            cache.clear();
            loadToCache(root, "");
            
        } catch (IOException e) {
            plugin.getLogger().error("Error cargando messages.yml", e);
        }
    }

    private void loadToCache(CommentedConfigurationNode node, String prefix) {
        if (node.isMap()) {
            for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : node.childrenMap().entrySet()) {
                String key = entry.getKey().toString();
                String path = prefix.isEmpty() ? key : prefix + "." + key;
                loadToCache(entry.getValue(), path);
            }
        } else if (node.getString() != null) {
            cache.put(prefix, node.getString());
        }
    }

    private void addDefaults(YamlConfigurationLoader loader) throws IOException {
        boolean changed = false;
        if (root.node("prefix").virtual()) { root.node("prefix").set("<gradient:#00b4d8:#48cae4>SimpleLogin</gradient>"); changed = true; }
        if (root.node("errors", "no_permission").virtual()) { root.node("errors", "no_permission").set("<red><bold>✘</bold></red> <gray>No tienes permiso para ejecutar este comando.</gray>"); changed = true; }
        if (root.node("errors", "only_players").virtual()) { root.node("errors", "only_players").set("<red><bold>✘</bold></red> <gray>Este comando solo puede ser usado por jugadores.</gray>"); changed = true; }
        if (root.node("auth", "change_password_usage").virtual()) { root.node("auth", "change_password_usage").set("<red><bold>✘</bold></red> <gray>Uso: <white>/changepassword <actual> <nueva> <confirmar></white></gray>"); changed = true; }
        if (root.node("auth", "change_password_success").virtual()) { root.node("auth", "change_password_success").set("<green><bold>✔</bold></green> <gray>Contraseña actualizada correctamente.</gray>"); changed = true; }
        if (root.node("auth", "logout_success").virtual()) { root.node("auth", "logout_success").set("<green><bold>✔</bold></green> <gray>Sesión cerrada. Vuelve a entrar para iniciar sesión de nuevo.</gray>"); changed = true; }
        if (root.node("velocity", "checking").virtual()) { root.node("velocity", "checking").set("<yellow>⟳ Verificando estado de tu cuenta...</yellow>"); changed = true; }
        if (root.node("velocity", "maintenance").virtual()) { root.node("velocity", "maintenance").set("<red><bold>⚠</bold></red> <gray>El sistema de autenticación se encuentra en mantenimiento.</gray>"); changed = true; }
        if (root.node("velocity", "premium_kick").virtual()) { root.node("velocity", "premium_kick").set("<red><bold>Cuenta Premium Detectada</bold></red>\n<gray>Esta cuenta está vinculada a una licencia oficial de Minecraft.</gray>\n<gray>Por favor, inicia sesión desde tu launcher oficial.</gray>"); changed = true; }

        if (root.node("limbo", "login_prompt").virtual()) { root.node("limbo", "login_prompt").set("<gray>Usa <white><bold>/login <password></bold></white> para ingresar.</gray>"); changed = true; }
        if (root.node("limbo", "register_prompt").virtual()) { root.node("limbo", "register_prompt").set("<gray>Usa <white><bold>/register <password> <password></bold></white> para crear tu cuenta.</gray>"); changed = true; }
        if (root.node("limbo", "subtitle_prompt").virtual()) { root.node("limbo", "subtitle_prompt").set("<gradient:#ffd166:#ef476f>Autenticación Requerida</gradient>"); changed = true; }
        if (root.node("limbo", "login_usage").virtual()) { root.node("limbo", "login_usage").set("<red><bold>✘</bold></red> <gray>Uso: <white>/login <password></white></gray>"); changed = true; }
        if (root.node("limbo", "register_usage").virtual()) { root.node("limbo", "register_usage").set("<red><bold>✘</bold></red> <gray>Uso: <white>/register <password> <confirmar></white></gray>"); changed = true; }
        if (root.node("limbo", "login_success").virtual()) { root.node("limbo", "login_success").set("<green><bold>✔</bold></green> <gray>Inicio de sesión exitoso. ¡Bienvenido de vuelta!</gray>"); changed = true; }
        if (root.node("limbo", "register_success").virtual()) { root.node("limbo", "register_success").set("<green><bold>✔</bold></green> <gray>Cuenta creada exitosamente. ¡Bienvenido!</gray>"); changed = true; }
        if (root.node("limbo", "wrong_password").virtual()) { root.node("limbo", "wrong_password").set("<red><bold>✘</bold></red> <gray>Contraseña incorrecta. Inténtalo nuevamente.</gray>"); changed = true; }
        if (root.node("limbo", "not_registered").virtual()) { root.node("limbo", "not_registered").set("<yellow><bold>!</bold></yellow> <gray>No estás registrado. Usa <white>/register</white> primero.</gray>"); changed = true; }
        if (root.node("limbo", "already_registered").virtual()) { root.node("limbo", "already_registered").set("<yellow><bold>!</bold></yellow> <gray>Esta cuenta ya tiene una contraseña asignada. Usa <white>/login</white>.</gray>"); changed = true; }
        if (root.node("limbo", "passwords_no_match").virtual()) { root.node("limbo", "passwords_no_match").set("<red><bold>✘</bold></red> <gray>Las contraseñas no coinciden. Verifica e intenta de nuevo.</gray>"); changed = true; }
        if (root.node("limbo", "password_too_short").virtual()) { root.node("limbo", "password_too_short").set("<red><bold>✘</bold></red> <gray>La contraseña debe tener al menos <white>8 caracteres</white>.</gray>"); changed = true; }
        if (root.node("limbo", "too_many_attempts").virtual()) { root.node("limbo", "too_many_attempts").set("<red><bold>✘</bold></red> <gray>Demasiados intentos. Espera antes de intentar otra vez.</gray>"); changed = true; }
        if (root.node("limbo", "login_timeout").virtual()) { root.node("limbo", "login_timeout").set("<red><bold>✘</bold></red> <gray>Tiempo de autenticación agotado. Vuelve a entrar e inténtalo de nuevo.</gray>"); changed = true; }
        if (root.node("limbo", "unknown_command").virtual()) { root.node("limbo", "unknown_command").set("<gray>Comando no reconocido. Usa <white>/login</white> o <white>/register</white>.</gray>"); changed = true; }
        if (root.node("limbo", "session_restored").virtual()) { root.node("limbo", "session_restored").set("<green><bold>✔</bold></green> <gray>Sesión activa restaurada. Redirigiendo...</gray>"); changed = true; }
        if (root.node("limbo", "error").virtual()) { root.node("limbo", "error").set("<red><bold>✘</bold></red> <gray>Ocurrió un error inesperado. Contacta a un administrador.</gray>"); changed = true; }
        if (root.node("limbo", "ip_mismatch").virtual()) { root.node("limbo", "ip_mismatch").set("<red><bold>⚠</bold></red> <gray>IP no reconocida. Si esto es un error, contacta a un administrador.</gray>"); changed = true; }

        if (changed) {
            loader.save(root);
        }
    }

    public Component getMessage(String path, Map<String, String> placeholders) {
        String raw = cache.getOrDefault(path, "<red>Missing message: " + path + "</red>");
        String prefix = cache.getOrDefault("prefix", "<green>[HybridLogin]</green>");
        
        raw = raw.replace("{prefix}", prefix);
        
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        if (raw.contains("&")) {
            return legacySerializer.deserialize(raw);
        }
        return miniMessage.deserialize(raw);
    }

    public Component getMessage(String path) {
        return getMessage(path, null);
    }

    public void reload() {
        loadMessages();
        plugin.getLogger().debug("Mensajes recargados.");
    }
}
