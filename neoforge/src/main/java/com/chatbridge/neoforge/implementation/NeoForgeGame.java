package com.chatbridge.neoforge.implementation;

import com.mhgandhi.chatBridge.core.Application;
import com.mhgandhi.chatBridge.core.domain.Game;
import com.mhgandhi.chatBridge.core.domain.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public final class NeoForgeGame implements Game {
    private final MinecraftServer server;
    private final Path dataDirectory;
    private final Executor serverExecutor;
    private final Runnable stopApplication;
    private final BiConsumer<Long, String> saveCredentials;

    public NeoForgeGame(
            MinecraftServer server,
            Path dataDirectory,
            Executor serverExecutor,
            Runnable stopApplication,
            BiConsumer<Long, String> saveCredentials
    ) throws IOException {
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.serverExecutor = serverExecutor;
        this.stopApplication = stopApplication;
        this.saveCredentials = saveCredentials;

        Files.createDirectories(dataDirectory);
    }

    @Override
    public Player getPlayer(UUID uuid) {
        var player = server.getPlayerList().getPlayer(uuid);

        if (player == null) {
            return null;
        }

        return new NeoForgePlayer(//todo mc player constructor
                uuid,
                player.getGameProfile().getName(),
                hasSavedPlayerData(uuid)
        );
    }

    @Override
    public Player getOfflinePlayer(UUID uuid) {
        Player online = getPlayer(uuid);
        if (online != null) {
            return online;
        }

        var cache = server.getProfileCache();

        String name = cache == null
                ? null
                : cache.get(uuid)
                .map(profile -> profile.getName())
                .orElse(null);

        // Return a wrapper even when the name is unknown.
        // Your IdentityManager currently expects a non-null result here.
        return new NeoForgePlayer(uuid, name, hasSavedPlayerData(uuid));
    }

    private boolean hasSavedPlayerData(UUID uuid) {
        return Files.isRegularFile(
                server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                        .resolve(uuid + ".dat")
        );
    }

    @Override
    public void kick(UUID uuid) {
        serverExecutor.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                return;
            }

            String reason = PlainTextComponentSerializer.plainText()
                    .serialize(Application.getFormatter().disconnectKick());

            player.connection.disconnect(Component.literal(reason));
        });
    }

    @Override
    public void disableCB() {
        serverExecutor.execute(stopApplication);
    }

    @Override
    public void saveWebhookCredentials(long id, String token) {
        serverExecutor.execute(() -> saveCredentials.accept(id, token));
    }

    @Override
    public File getDataFolder() {
        return dataDirectory.toFile();
    }
}