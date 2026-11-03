package com.chatbridge.neoforge.implementation;

import com.mhgandhi.chatBridge.core.domain.CommandRegistrar;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class NeoForgeCommandRegistrar implements CommandRegistrar {

    @Override
    public void registerStatusCommand(Function<UUID, Component> execute) {

    }

    @Override
    public void registerDisconnectCommand(Function<UUID, Component> execute) {

    }

    @Override
    public void registerConnectCommand(BiFunction<UUID, String, Component> execute, BiFunction<UUID, String, List<String>> suggest) {

    }
}