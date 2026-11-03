package com.chatbridge.neoforge.implementation;

import com.mhgandhi.chatBridge.core.domain.CommandRegistrar;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class NeoForgeCommandRegistrar implements CommandRegistrar {
    private Function<UUID, Component> status;
    private Function<UUID, Component> disconnect;

    private BiFunction<UUID, String, Component> connect;
    private BiFunction<UUID, String, List<String>> connectSuggestions;

    @Override
    public void registerStatusCommand(
            Function<UUID, Component> execute
    ) {
        status = Objects.requireNonNull(execute);
    }

    @Override
    public void registerDisconnectCommand(
            Function<UUID, Component> execute
    ) {
        disconnect = Objects.requireNonNull(execute);
    }

    @Override
    public void registerConnectCommand(
            BiFunction<UUID, String, Component> execute,
            BiFunction<UUID, String, List<String>> suggest
    ) {
        connect = Objects.requireNonNull(execute);
        connectSuggestions = Objects.requireNonNull(suggest);
    }

    public void clearCallbacks() {
        status = null;
        disconnect = null;
        connect = null;
        connectSuggestions = null;
    }

    // registerCommands(CommandDispatcher<CommandSourceStack> dispatcher)
    // installs Brigadier nodes that call these fields at execution time.
}