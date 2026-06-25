package com.premiumauth.simplelogin.velocity.limbo;

import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import net.elytrium.limboapi.api.player.GameMode;
import com.premiumauth.simplelogin.velocity.SimpleLoginVelocity;
import com.premiumauth.simplelogin.models.Account;
import com.premiumauth.simplelogin.velocity.auth.VelocityAuthManager;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AuthLimboManager {

    private final SimpleLoginVelocity plugin;
    private final VelocityAuthManager authManager;
    private final LimboFactory factory;
    private final Map<UUID, net.elytrium.limboapi.api.player.LimboPlayer> activeLimboPlayers = new ConcurrentHashMap<>();
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();
    private Limbo authServer;
    private VirtualWorld world;

    public AuthLimboManager(SimpleLoginVelocity plugin, VelocityAuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.factory = (LimboFactory) plugin.getProxy().getPluginManager()
                .getPlugin("limboapi")
                .flatMap(PluginContainer::getInstance)
                .orElseThrow(() -> new RuntimeException("LimboAPI plugin is not installed on Velocity."));
        setupLimbo();
        registerCommands();
    }

    private void setupLimbo() {
        this.world = factory.createVirtualWorld(
                Dimension.OVERWORLD,
                0.5, 64.0, 0.5,
                0f, 0f
        );

        this.authServer = factory.createLimbo(this.world)
                .setName("simplelogin-Auth")
                .setGameMode(GameMode.ADVENTURE)
                .setReadTimeout(30000);

        plugin.getLogger().debug("[simplelogin] Native Limbo server initialized.");
    }

    private void registerCommands() {
        LiteralCommandNode<CommandSource> loginNode = BrigadierCommand.literalArgumentBuilder("login")
                .requires(source -> {
                    if (source instanceof Player player) {
                        return !authManager.isAuthenticated(player.getUniqueId());
                    }
                    return true;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("password", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String password = StringArgumentType.getString(ctx, "password");
                            CommandSource source = ctx.getSource();
                            if (source instanceof Player player) {
                                handleLogin(player, password);
                            }
                            return 1;
                        }))
                .executes(ctx -> {
                    CommandSource source = ctx.getSource();
                    if (source instanceof Player player) {
                        player.sendMessage(plugin.getMessageManager().getMessage("limbo.login_usage"));
                    }
                    return 1;
                })
                .build();
        CommandMeta loginMeta = plugin.getProxy().getCommandManager().metaBuilder("login")
                .aliases("log", "l")
                .plugin(plugin)
                .build();
        BrigadierCommand loginCommand = new BrigadierCommand(loginNode);
        plugin.getProxy().getCommandManager().register(loginMeta, loginCommand);
        authServer.registerCommand(loginMeta, loginCommand);

        LiteralCommandNode<CommandSource> registerNode = BrigadierCommand.literalArgumentBuilder("register")
                .requires(source -> {
                    if (source instanceof Player player) {
                        return !authManager.isAuthenticated(player.getUniqueId());
                    }
                    return true;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("password", StringArgumentType.string())
                        .then(BrigadierCommand.requiredArgumentBuilder("confirm", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String password = StringArgumentType.getString(ctx, "password");
                                    String confirm = StringArgumentType.getString(ctx, "confirm");
                                    CommandSource source = ctx.getSource();
                                    if (source instanceof Player player) {
                                        handleRegister(player, password, confirm);
                                    }
                                    return 1;
                                })))
                .executes(ctx -> {
                    CommandSource source = ctx.getSource();
                    if (source instanceof Player player) {
                        player.sendMessage(plugin.getMessageManager().getMessage("limbo.register_usage"));
                    }
                    return 1;
                })
                .build();
        CommandMeta registerMeta = plugin.getProxy().getCommandManager().metaBuilder("register")
                .aliases("reg", "r")
                .plugin(plugin)
                .build();
        BrigadierCommand registerCommand = new BrigadierCommand(registerNode);
        plugin.getProxy().getCommandManager().register(registerMeta, registerCommand);
        authServer.registerCommand(registerMeta, registerCommand);

        plugin.getLogger().debug("[Command] Brigadier commands registered in Velocity and LimboAPI: /login, /register (with tab completion)");
    }

    private void handleLogin(Player player, String password) {
        if (!authManager.isPending(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.already_registered"));
            return;
        }

        String rateLimitKey = rateLimitKey(player);
        if (plugin.getLoginRateLimiter().isBlocked(rateLimitKey, plugin.getConfigManager().getLoginCooldownSeconds())) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.too_many_attempts"));
            return;
        }

        plugin.getDatabaseManager().getAccount(player.getUsername())
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((opt, ex) -> {
                    plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                        if (ex != null || opt.isEmpty()) {
                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
                            return;
                        }
                        var account = opt.get();
                        if (!account.isRegistered()) {
                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.not_registered"));
                            return;
                        }

                        com.premiumauth.simplelogin.utils.PasswordHasher.VerificationResult vResult = com.premiumauth.simplelogin.utils.PasswordHasher.verify(password, account.getPasswordHash());
                        if (vResult == com.premiumauth.simplelogin.utils.PasswordHasher.VerificationResult.SUCCESS_UP_TO_DATE || vResult == com.premiumauth.simplelogin.utils.PasswordHasher.VerificationResult.SUCCESS_NEEDS_REHASH) {
                            plugin.getLoginRateLimiter().recordSuccess(rateLimitKey);
                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.login_success"));
                            
                            if (vResult == com.premiumauth.simplelogin.utils.PasswordHasher.VerificationResult.SUCCESS_NEEDS_REHASH) {
                                String newHash = com.premiumauth.simplelogin.utils.PasswordHasher.hash(password);
                                plugin.getDatabaseManager().updatePassword(player.getUsername(), newHash);
                                account.setPasswordHash(newHash);
                            }
                            
                            authManager.authenticate(player.getUniqueId());
                            long expires = System.currentTimeMillis() + (plugin.getConfigManager().getSessionDurationHours() * 3600_000L);
                            plugin.getDatabaseManager().updateSession(player.getUsername(), null, expires)
                                    .whenComplete((v, sessionErr) -> {
                                        plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                                            if (sessionErr != null) {
                                                plugin.getLogger().warn("Failed to update session of {}", player.getUsername(), sessionErr);
                                                player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
                                                return;
                                            }
                                            processingPlayers.remove(player.getUniqueId());
                                            sendToLobby(player);
                                        }).schedule();
                                    });
                        } else {
                            plugin.getLoginRateLimiter().recordFailure(rateLimitKey,
                                    plugin.getConfigManager().getMaxLoginAttempts(),
                                    plugin.getConfigManager().getLoginCooldownSeconds());
                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.wrong_password"));
                        }
                    }).schedule();
                });
    }

    private void handleRegister(Player player, String password, String confirm) {
        if (!authManager.isPending(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.already_registered"));
            return;
        }

        if (!password.equals(confirm)) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.passwords_no_match"));
            return;
        }
        if (password.length() < plugin.getConfigManager().getMinPasswordLength()) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.password_too_short"));
            return;
        }

        String rateLimitKey = rateLimitKey(player);
        if (plugin.getLoginRateLimiter().isBlocked(rateLimitKey, plugin.getConfigManager().getLoginCooldownSeconds())) {
            player.sendMessage(plugin.getMessageManager().getMessage("limbo.too_many_attempts"));
            return;
        }

        plugin.getDatabaseManager().getAccount(player.getUsername())
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((opt, ex) -> {
                    plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                        if (ex != null) {
                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
                            return;
                        }
                        if (opt.isPresent() && opt.get().isRegistered()) {
                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.already_registered"));
                            return;
                        }

                        String hash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt(12));
                        plugin.getDatabaseManager().registerAccount(player.getUsername(), player.getUniqueId(), hash)
                                .whenComplete((v, err) -> {
                                    plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                                        if (err != null) {
                                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
                                            return;
                                        }
                                        player.sendMessage(plugin.getMessageManager().getMessage("limbo.register_success"));
                                        plugin.getLoginRateLimiter().recordSuccess(rateLimitKey);
                                        authManager.authenticate(player.getUniqueId());
                                        long expires = System.currentTimeMillis() + (plugin.getConfigManager().getSessionDurationHours() * 3600_000L);
                                        plugin.getDatabaseManager().updateSession(player.getUsername(), null, expires)
                                                .whenComplete((session, sessionErr) -> {
                                                    plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                                                        if (sessionErr != null) {
                                                            plugin.getLogger().warn("Failed to update session of {}", player.getUsername(), sessionErr);
                                                            player.sendMessage(plugin.getMessageManager().getMessage("limbo.error"));
                                                            return;
                                                        }
                                                        processingPlayers.remove(player.getUniqueId());
                                                        sendToLobby(player);
                                                    }).schedule();
                                                });
                                    }).schedule();
                                });
                    }).schedule();
                });
    }

    private String rateLimitKey(Player player) {
        return player.getRemoteAddress().getAddress().getHostAddress() + ":" + player.getUsername().toLowerCase();
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();

        Boolean isPremium = plugin.getPremiumStatus(username);
        if (Boolean.TRUE.equals(isPremium)) {
            String target = plugin.getConfigManager().getMainSpawnServer();
            Optional<RegisteredServer> server = plugin.getProxy().getServer(target);
            if (server.isPresent()) {
                plugin.getLogger().debug("[Spawn] {} is premium. Initial server -> '{}'", username, target);
                event.setInitialServer(server.get());
            } else {
                plugin.getLogger().error("[Spawn] Main server '{}' not found for premium '{}'", target, username);
            }
        } else if (!authManager.isAuthenticated(player.getUniqueId())) {
            plugin.getLogger().debug("[Spawn] {} is not premium nor authenticated. Sending to limbo.", username);
            processingPlayers.add(player.getUniqueId());
            sendToLimbo(player);
        }
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        String targetName = event.getOriginalServer().getServerInfo().getName();
        String lobbyName = plugin.getConfigManager().getLobbyServer();

        if (!targetName.equalsIgnoreCase(lobbyName)) {
            return;
        }

        // Do not intercept if already authenticated, premium, or being processed in limbo
        if (authManager.isAuthenticated(uuid)) {
            plugin.getLogger().debug("[Limbo] {} is already authenticated, allowing access to lobby.", username);
            return;
        }

        Boolean isPremium = plugin.getPremiumStatus(username);
        if (Boolean.TRUE.equals(isPremium)) {
            plugin.getLogger().debug("[Limbo] {} is premium, allowing access to lobby.", username);
            return;
        }

        if (isInLimbo(uuid) || processingPlayers.contains(uuid)) {
            plugin.getLogger().debug("[Limbo] {} is already in limbo or being processed, ignoring.", username);
            return;
        }

        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        plugin.getLogger().debug("[Limbo] {} intercepted, verifying session.", username);

        plugin.getDatabaseManager().getAccount(username)
                .orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((opt, ex) -> {
                    plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                        if (ex != null) {
                            plugin.getLogger().error("[Limbo] Error verifying session of {}", username, ex);
                            processingPlayers.add(uuid);
                            sendToLimbo(player);
                            return;
                        }

                        var account = opt.orElse(null);

                        if (account != null && account.hasValidSession()) {
                            authManager.authenticate(uuid);
                            plugin.getLogger().debug("[Limbo] {} session is valid, sending directly to lobby.", username);
                            sendToLobbyDirect(player);
                        } else {
                            processingPlayers.add(uuid);
                            sendToLimbo(player);
                        }
                    }).schedule();
                });
    }

    @Subscribe
    public void onLimboRegister(LoginLimboRegisterEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();

        Boolean isPremium = plugin.getPremiumStatus(username);
        if (Boolean.TRUE.equals(isPremium)) {
            factory.passLoginLimbo(player);
            return;
        }

        if (authManager.isAuthenticated(player.getUniqueId())) {
            factory.passLoginLimbo(player);
            return;
        }

        event.addOnJoinCallback(() -> {
            authServer.spawnPlayer(player, new AuthLimboSessionHandler(plugin, this, authManager));
        });
    }

    public void sendToLimbo(Player player) {
        authServer.spawnPlayer(player, new AuthLimboSessionHandler(plugin, this, authManager));
    }

    public void sendToLobby(Player player) {
        String targetServer = plugin.getConfigManager().getMainSpawnServer();
        Optional<RegisteredServer> lobby = plugin.getProxy().getServer(targetServer);
        if (lobby.isPresent()) {
            plugin.getLogger().debug("[Limbo] {} redirected to server '{}'.", player.getUsername(), targetServer);
            redirectFromLimbo(player);
        } else {
            plugin.getLogger().error("Lobby server '{}' not found.", targetServer);
            player.sendMessage(Component.text("Error: Lobby is not configured.", NamedTextColor.RED));
        }
    }

    private void sendToLobbyDirect(Player player) {
        redirectFromLimbo(player);
    }

    public void registerLimboPlayer(UUID uuid, net.elytrium.limboapi.api.player.LimboPlayer limboPlayer) {
        activeLimboPlayers.put(uuid, limboPlayer);
    }

    public void unregisterLimboPlayer(UUID uuid) {
        activeLimboPlayers.remove(uuid);
        processingPlayers.remove(uuid);
    }

    public boolean isInLimbo(UUID uuid) {
        return activeLimboPlayers.containsKey(uuid);
    }

    public boolean isProcessing(UUID uuid) {
        return processingPlayers.contains(uuid);
    }

    public void redirectFromLimbo(Player player) {
        String targetServer = plugin.getConfigManager().getMainSpawnServer();
        Optional<RegisteredServer> lobby = plugin.getProxy().getServer(targetServer);
        if (lobby.isEmpty()) {
            plugin.getLogger().error("Lobby server '{}' not found.", targetServer);
            player.sendMessage(Component.text("Error: Lobby is not configured.", NamedTextColor.RED));
            return;
        }

        net.elytrium.limboapi.api.player.LimboPlayer limboPlayer = activeLimboPlayers.get(player.getUniqueId());
        if (limboPlayer != null) {
            plugin.getLogger().debug("[Limbo] Disconnecting {} from limbo to lobby.", player.getUsername());
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                limboPlayer.disconnect(lobby.get());
                activeLimboPlayers.remove(player.getUniqueId());
                processingPlayers.remove(player.getUniqueId());
            }).delay(300, TimeUnit.MILLISECONDS).schedule();
        } else {
            plugin.getProxy().getScheduler().buildTask(plugin, () -> {
                player.createConnectionRequest(lobby.get()).fireAndForget();
                processingPlayers.remove(player.getUniqueId());
            }).delay(300, TimeUnit.MILLISECONDS).schedule();
        }
    }
}
