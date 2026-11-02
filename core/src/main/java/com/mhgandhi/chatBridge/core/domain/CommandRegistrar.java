package com.mhgandhi.chatBridge.core.domain;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface CommandRegistrar {
    void registerStatusCommand(
            Function<UUID, Component> execute
    );

    void registerDisconnectCommand(
            Function<UUID, Component> execute
    );

    void registerConnectCommand(
            BiFunction<UUID, String, Component> execute,
            BiFunction<UUID, String, List<String>> suggest
    );
}
