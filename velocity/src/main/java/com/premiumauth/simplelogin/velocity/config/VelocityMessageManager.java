package com.premiumauth.simplelogin.velocity.config;

import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
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

    private final SimpleLoginVelocity plugin;
    private Path messagesPath;
    private CommentedConfigurationNode root;
    private final Map<String, String> cache = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public VelocityMessageManager(SimpleLoginVelocity plugin) {
        this.plugin = plugin;
        this.messagesPath = resolveMessagesPath();
        loadMessages();
    }

    private Path resolveMessagesPath() {
        String language = plugin.getConfigManager().getLanguage();
        return plugin.getDataDirectory().resolve("messages").resolve(language + ".yml");
    }

    private void loadMessages() {
        try {
            String language = plugin.getConfigManager().getLanguage();
            Path messagesDir = plugin.getDataDirectory().resolve("messages");
            if (!Files.exists(messagesDir)) {
                Files.createDirectories(messagesDir);
            }

            messagesPath = messagesDir.resolve(language + ".yml");

            if (!Files.exists(messagesPath)) {
                copyFromResource(language);
            }

            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(messagesPath).build();

            try {
                root = loader.load();
            } catch (Exception parseEx) {
                plugin.getLogger().warn("Messages file corrupted, recreating from defaults...");
                Files.deleteIfExists(messagesPath);
                copyFromResource(language);
                loader = YamlConfigurationLoader.builder().path(messagesPath).build();
                root = loader.load();
            }

            addDefaults(loader);
            cache.clear();
            loadToCache(root, "");

        } catch (IOException e) {
            plugin.getLogger().error("Error cargando mensajes", e);
        }
    }

    private void copyFromResource(String language) {
        try (var stream = getClass().getResourceAsStream("/messages/" + language + ".yml")) {
            if (stream != null) {
                Files.copy(stream, messagesPath);
                return;
            }
        } catch (IOException e) {
            plugin.getLogger().error("Error copiando messages/" + language + ".yml", e);
        }

        try (var fallback = getClass().getResourceAsStream("/messages/es.yml")) {
            if (fallback != null) {
                Files.copy(fallback, messagesPath);
            } else {
                Files.createFile(messagesPath);
            }
        } catch (IOException e) {
            plugin.getLogger().error("Error copiando fallback messages", e);
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

    private boolean addDefault(String path, String value) {
       if (root == null) return false;
       try {
           Object[] keys = path.split("\\.");
           CommentedConfigurationNode node = (CommentedConfigurationNode) root.node(keys);
           if (node.virtual()) {
               node.set(value);
               return true;
           }
       } catch (Exception e) {
           plugin.getLogger().warn("Could not add default message for: {}", path, e);
       }
       return false;
    }

    private void addDefaults(YamlConfigurationLoader loader) throws IOException {
       boolean changed = false;
       if (root.node("prefix").virtual()) { root.node("prefix").set("<gradient:#00b4d8:#48cae4>SimpleLogin</gradient>"); changed = true; }
       if (root.node("errors", "no_permission").virtual()) { root.node("errors", "no_permission").set("<red>✘</red> <gray>No tienes permiso para ejecutar este comando.</gray>"); changed = true; }
       if (root.node("errors", "only_players").virtual()) { root.node("errors", "only_players").set("<red>✘</red> <gray>Este comando solo puede ser usado por jugadores.</gray>"); changed = true; }
       if (root.node("auth", "change_password_usage").virtual()) { root.node("auth", "change_password_usage").set("<red>✘</red> <gray>Uso: <white>/changepassword <actual> <nueva> <confirmar></white></gray>"); changed = true; }
       if (root.node("auth", "change_password_success").virtual()) { root.node("auth", "change_password_success").set("<green>✔</green> <gray>Contraseña actualizada correctamente.</gray>"); changed = true; }
       if (root.node("auth", "logout_success").virtual()) { root.node("auth", "logout_success").set("<green>✔</green> <gray>Sesión cerrada. Vuelve a entrar para iniciar sesión de nuevo.</gray>"); changed = true; }
       if (root.node("velocity", "checking").virtual()) { root.node("velocity", "checking").set("<yellow>⟳ Verificando estado de tu cuenta...</yellow>"); changed = true; }
       if (root.node("velocity", "maintenance").virtual()) { root.node("velocity", "maintenance").set("<red>⚠</red> <gray>El sistema de autenticación se encuentra en mantenimiento.</gray>"); changed = true; }
       if (root.node("velocity", "premium_kick").virtual()) { root.node("velocity", "premium_kick").set("<red><bold>Cuenta Premium Detectada</bold></red>\n<gray>Esta cuenta está vinculada a una licencia oficial de Minecraft.</gray>\n<gray>Por favor, inicia sesión desde tu launcher oficial.</gray>"); changed = true; }

       if (root.node("limbo", "login_prompt").virtual()) { root.node("limbo", "login_prompt").set("<gray>Usa <white><bold>/login <password></bold></white> para ingresar.</gray>"); changed = true; }
       if (root.node("limbo", "register_prompt").virtual()) { root.node("limbo", "register_prompt").set("<gray>Usa <white><bold>/register <password> <password></bold></white> para crear tu cuenta.</gray>"); changed = true; }
       if (root.node("limbo", "subtitle_prompt").virtual()) { root.node("limbo", "subtitle_prompt").set("<gradient:#ffd166:#ef476f>Autenticación Requerida</gradient>"); changed = true; }
       if (root.node("limbo", "login_usage").virtual()) { root.node("limbo", "login_usage").set("<red>✘</red> <gray>Uso: <white>/login <password></white></gray>"); changed = true; }
       if (root.node("limbo", "register_usage").virtual()) { root.node("limbo", "register_usage").set("<red>✘</red> <gray>Uso: <white>/register <password> <confirmar></white></gray>"); changed = true; }
       if (root.node("limbo", "login_success").virtual()) { root.node("limbo", "login_success").set("<green>✔</green> <gray>Inicio de sesión exitoso. ¡Bienvenido de vuelta!</gray>"); changed = true; }
       if (root.node("limbo", "register_success").virtual()) { root.node("limbo", "register_success").set("<green>✔</green> <gray>Cuenta creada exitosamente. ¡Bienvenido!</gray>"); changed = true; }
       if (root.node("limbo", "wrong_password").virtual()) { root.node("limbo", "wrong_password").set("<red>✘</red> <gray>Contraseña incorrecta. Inténtalo nuevamente.</gray>"); changed = true; }
       if (root.node("limbo", "not_registered").virtual()) { root.node("limbo", "not_registered").set("<yellow>!</yellow> <gray>No estás registrado. Usa <white>/register</white> primero.</gray>"); changed = true; }
       if (root.node("limbo", "already_registered").virtual()) { root.node("limbo", "already_registered").set("<yellow>!</yellow> <gray>Esta cuenta ya tiene una contraseña asignada. Usa <white>/login</white>.</gray>"); changed = true; }
       if (root.node("limbo", "passwords_no_match").virtual()) { root.node("limbo", "passwords_no_match").set("<red>✘</red> <gray>Las contraseñas no coinciden. Verifica e intenta de nuevo.</gray>"); changed = true; }
       if (root.node("limbo", "password_too_short").virtual()) { root.node("limbo", "password_too_short").set("<red>✘</red> <gray>La contraseña debe tener al menos <white>8 caracteres</white>.</gray>"); changed = true; }
       if (root.node("limbo", "too_many_attempts").virtual()) { root.node("limbo", "too_many_attempts").set("<red>✘</red> <gray>Demasiados intentos. Espera antes de intentar otra vez.</gray>"); changed = true; }
       if (root.node("limbo", "login_timeout").virtual()) { root.node("limbo", "login_timeout").set("<red>✘</red> <gray>Tiempo de autenticación agotado. Vuelve a entrar e inténtalo de nuevo.</gray>"); changed = true; }
       if (root.node("limbo", "unknown_command").virtual()) { root.node("limbo", "unknown_command").set("<gray>Comando no reconocido. Usa <white>/login</white> o <white>/register</white>.</gray>"); changed = true; }
       if (root.node("limbo", "session_restored").virtual()) { root.node("limbo", "session_restored").set("<green>✔</green> <gray>Sesión activa restaurada. Redirigiendo...</gray>"); changed = true; }
       if (root.node("limbo", "error").virtual()) { root.node("limbo", "error").set("<red>✘</red> <gray>Ocurrió un error inesperado. Contacta a un administrador.</gray>"); changed = true; }
       if (root.node("limbo", "ip_mismatch").virtual()) { root.node("limbo", "ip_mismatch").set("<red>⚠</red> <gray>IP no reconocida. Si esto es un error, contacta a un administrador.</gray>"); changed = true; }

       changed |= addDefault("admin.no_permission", "<red>✘</red> <gray>No tienes permiso para usar este comando.</gray>");
       changed |= addDefault("admin.unknown_subcommand", "<red>✘</red> <gray>Subcomando desconocido. Usa <white>/sl</white> para ver ayuda.</gray>");
       changed |= addDefault("admin.reload_success", "<green>✔</green> <gray>Configuración recargada.</gray>");
       changed |= addDefault("admin.setspawn_usage", "<red>Uso: /sl setspawn <auth|main></red>");
       changed |= addDefault("admin.setspawn_invalid_type", "<red>Tipo inválido. Usa 'auth' o 'main'.</red>");
       changed |= addDefault("admin.setspawn_success", "<green>Spawn '<type>' seteado en tu ubicación actual.</green>");
       changed |= addDefault("admin.setspawn_backend_ok", "<green>Spawn '<type>' seteado en el servidor backend.</green>");
       changed |= addDefault("admin.setspawn_not_connected", "<red>Debes estar conectado a un servidor backend.</red>");
       changed |= addDefault("admin.setspawn_send_error", "<red>No se pudo enviar setspawn al servidor backend.</red>");
       changed |= addDefault("admin.unregister_usage", "<red>Uso: /sl unregister <jugador></red>");
       changed |= addDefault("admin.unregister_not_found", "<red>Jugador no encontrado en la base de datos.</red>");
       changed |= addDefault("admin.unregister_success", "<green>Cuenta de <player> reseteada completamente.</green>");
       changed |= addDefault("admin.unregister_error", "<red>Error eliminando cuenta de <player>.</red>");
       changed |= addDefault("admin.unregister_kick", "<red>Cuenta eliminada por admin. Reconecta.</red>");
       changed |= addDefault("admin.forcepremium_usage", "<red>Uso: /sl forcepremium <jugador></red>");
       changed |= addDefault("admin.forcepremium_not_found", "<red>Jugador no encontrado en la base de datos.</red>");
       changed |= addDefault("admin.forcepremium_enabled", "<green>Modo premium de <player> activado. Debe reconectar.</green>");
       changed |= addDefault("admin.forcepremium_disabled", "<green>Modo premium de <player> desactivado. Debe reconectar.</green>");
       changed |= addDefault("admin.forcepremium_error", "<red>Error forzando premium de <player>.</red>");
       changed |= addDefault("admin.forcepremium_kick", "<green>Premium activado por admin. Reconecta.</green>");
       changed |= addDefault("admin.resetip_usage", "<red>Uso: /sl resetip <jugador></red>");
       changed |= addDefault("admin.resetip_error", "<red>Error reseteando IP de <player>.</red>");
       changed |= addDefault("admin.resetip_success", "<green>IP vinculada de <player> liberada. Podrá reconectar desde cualquier IP.</green>");
       changed |= addDefault("admin.setip_usage", "<red>Uso: /sl setip <jugador></red>");
       changed |= addDefault("admin.setip_player_offline", "<red>Jugador '<player>' no está online.</red>");
       changed |= addDefault("admin.setip_error", "<red>Error actualizando IP de <player>.</red>");
       changed |= addDefault("admin.setip_success", "<green>IP vinculada de <player> actualizada a: <white>{ip}</white>.</green>");
       changed |= addDefault("admin.status_not_found", "<red>Cuenta no encontrada para '<player>'.</red>");
       changed |= addDefault("admin.status_header", "<gold>=== Estado de <player> ===</gold>");
       changed |= addDefault("admin.status_registered", "<yellow>Registrado:</yellow> <white>{value}</white>");
       changed |= addDefault("admin.status_type", "<yellow>Tipo:</yellow> <white>{value}</white>");
       changed |= addDefault("admin.status_online", "<yellow>Online:</yellow> <white>{value}</white>");
       changed |= addDefault("admin.status_ip", "<yellow>IP vinculada:</yellow> <white>{value}</white>");
       changed |= addDefault("admin.status_session", "<yellow>Sesión activa:</yellow> <white>{value}</white>");
       changed |= addDefault("admin.resetpassword_usage", "<red>Uso: /sl resetpassword <jugador></red>");
       changed |= addDefault("admin.resetpassword_error", "<red>Error reseteando contraseña de <player>.</red>");
       changed |= addDefault("admin.resetpassword_success", "<green>Contraseña temporal para <player>: <white>{password}</white></green>");
       changed |= addDefault("admin.resetpassword_kick", "<red>Tu contraseña fue reiniciada por un administrador. Vuelve a entrar.</red>");
       changed |= addDefault("admin.info_header", "<gold>===== SimpleLogin v{version} =====</gold>");
       changed |= addDefault("admin.info_db", "<yellow>BD:</yellow> <white>{type}</white>");
       changed |= addDefault("admin.info_limbo", "<yellow>LimboAPI:</yellow> <white>{status}</white>");
       changed |= addDefault("admin.info_main_server", "<yellow>Server main:</yellow> <white>{server}</white>");
       changed |= addDefault("admin.info_auth_server", "<yellow>Server auth:</yellow> <white>{server}</white>");
       changed |= addDefault("admin.info_run_inside", "<gray>Para verificar spawns, ejecuta este comando dentro de un servidor backend.</gray>");
       changed |= addDefault("admin.info_backend_error", "<red>No se pudo consultar spawns desde el backend.</red>");
       changed |= addDefault("admin.info_spawn_main", "<yellow>Spawn main seteado en Paper: <white>{value}</white></yellow>");
       changed |= addDefault("admin.info_spawn_auth", "<yellow>Spawn auth seteado en Paper: <white>{value}</white></yellow>");
       changed |= addDefault("admin.help_header", "<gold>===== Comandos de SimpleLogin =====</gold>");
       changed |= addDefault("admin.help_setspawn", "<yellow>/sl setspawn <auth|main></yellow> <white>- Setear spawns</white>");
       changed |= addDefault("admin.help_unregister", "<yellow>/sl unregister <user></yellow> <white>- Eliminar cuenta</white>");
       changed |= addDefault("admin.help_forcepremium", "<yellow>/sl forcepremium <user></yellow> <white>- Forzar modo premium</white>");
       changed |= addDefault("admin.help_bypass", "<yellow>/sl bypass <user></yellow> <white>- Forzar offline mode</white>");
       changed |= addDefault("admin.help_reload", "<yellow>/sl reload</yellow> <white>- Recargar config</white>");
       changed |= addDefault("admin.help_resetip", "<yellow>/sl resetip <user></yellow> <white>- Liberar IP vinculada</white>");
       changed |= addDefault("admin.help_setip", "<yellow>/sl setip <user></yellow> <white>- Actualizar IP vinculada</white>");
       changed |= addDefault("admin.help_status", "<yellow>/sl status <user></yellow> <white>- Mostrar estado de cuenta</white>");
       changed |= addDefault("admin.help_resetpassword", "<yellow>/sl resetpassword <user></yellow> <white>- Generar contraseña temporal</white>");
       changed |= addDefault("admin.help_info", "<yellow>/sl info</yellow> <white>- Diagnóstico rápido</white>");
       changed |= addDefault("admin.help_backup", "<yellow>/sl backup [list|restore <file>]</yellow> <white>- Respaldar BD</white>");
       changed |= addDefault("admin.yes", "SI");
       changed |= addDefault("admin.no", "NO");
       changed |= addDefault("admin.none", "Ninguna");
       changed |= addDefault("admin.bypass_usage", "<red>Uso: /sl bypass <jugador></red>");
       changed |= addDefault("admin.bypass_enabled", "<green>Bypass activado para <player>. Entrará en modo offline.</green>");
       changed |= addDefault("admin.bypass_disabled", "<green>Bypass desactivado para <player>.</green>");
       changed |= addDefault("admin.bypass_error", "<red>Error al cambiar bypass de <player>.</red>");
       changed |= addDefault("admin.backup_creating", "<yellow>⟳ Creando backup de la base de datos...</yellow>");
       changed |= addDefault("admin.backup_success", "<green>✔</green> <gray>Backup creado: </gray><white>{file}</white>");
       changed |= addDefault("admin.backup_error", "<red>✘</red> <gray>Error al crear backup: </gray><red>{error}</red>");
       changed |= addDefault("admin.backup_restore_usage", "<red>Uso: /sl backup restore <archivo></red>");
       changed |= addDefault("admin.backup_restoring", "<yellow>⟳ Restaurando backup...</yellow>");
       changed |= addDefault("admin.backup_restore_success", "<green>✔</green> <gray>Backup restaurado: </gray><white>{file}</white>");
       changed |= addDefault("admin.backup_restore_error", "<red>✘</red> <gray>Error al restaurar: </gray><red>{error}</red>");
       changed |= addDefault("admin.backup_list_header", "<gold>=== Backups disponibles ===</gold>");
       changed |= addDefault("admin.backup_list_empty", "<gray>No hay backups.</gray>");
       changed |= addDefault("admin.backup_list_item", "<yellow>-</yellow> <white>{file}</white>");
       changed |= addDefault("admin.backup_invalid_file", "<red>Archivo inválido. Debe terminar en .db o .sql</red>");

       if (changed) {
           loader.save(root);
       }
    }

    public Component getMessage(String path, Map<String, String> placeholders) {
        String raw = cache.getOrDefault(path, "<red>Missing message: " + path + "</red>");
        String prefix = cache.getOrDefault("prefix", "<green>[simplelogin]</green>");
        
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

    public String getString(String path) {
        return cache.getOrDefault(path, path);
    }

    public void reload() {
        messagesPath = resolveMessagesPath();
        loadMessages();
        plugin.getLogger().debug("Mensajes recargados.");
    }
}
