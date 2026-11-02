package com.mhgandhi.chatBridge.paper.implementation;

import com.mhgandhi.chatBridge.core.Application;
import com.mhgandhi.chatBridge.core.domain.CommandRegistrar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PaperCommandRegistrar implements CommandRegistrar {
    private final JavaPlugin plugin;

    public PaperCommandRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerStatusCommand(Function<UUID, Component> execute) {
        Objects.requireNonNull(plugin.getCommand("status"), "Missing command 'status' in plugin.yml")
                .setExecutor((sender, command, label, args) -> {
                    if (!(sender instanceof org.bukkit.entity.Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    try {
                        player.sendMessage(execute.apply(player.getUniqueId()));
                    } catch (RuntimeException error) {
                        sendCommandError(sender, error);
                    }
                    return true;
                });
    }

    @Override
    public void registerDisconnectCommand(Function<UUID, Component> execute) {
        Objects.requireNonNull(plugin.getCommand("disconnect"), "Missing command 'disconnect' in plugin.yml")
                .setExecutor((sender, command, label, args) -> {
                    if (!(sender instanceof org.bukkit.entity.Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    try {
                        player.sendMessage(execute.apply(player.getUniqueId()));
                    } catch (RuntimeException error) {
                        sendCommandError(sender, error);
                    }
                    return true;
                });
    }

    @Override
    public void registerConnectCommand(BiFunction<UUID, String, Component> execute, BiFunction<UUID, String, List<String>> suggest) {
        Objects.requireNonNull(plugin.getCommand("connect"), "Missing command 'connect' in plugin.yml")
                .setExecutor((sender, command, label, args) -> {
                    if (!(sender instanceof org.bukkit.entity.Player player)) {
                        sender.sendMessage("Players only.");
                        return true;
                    }

                    try {
                        player.sendMessage(execute.apply(player.getUniqueId(), args[0]));
                    } catch (RuntimeException error) {
                        sendCommandError(sender, error);
                    }
                    return true;
                });
        Objects.requireNonNull(plugin.getCommand("connect"), "Missing command 'connect' in plugin.yml")
                .setTabCompleter((sender, command, label, args)->{
                    if (!(sender instanceof Player p)) return Collections.emptyList();
                    if (args.length != 1) return Collections.emptyList();

                    try{
                        return suggest.apply(p.getUniqueId(), args[0].toLowerCase());
                    } catch (RuntimeException ignored) {
                        return Collections.emptyList();
                    }
                });
    }


    private void sendCommandError(@NotNull CommandSender sender, RuntimeException error) {
        sender.sendMessage(Component.text(Application.getFormatter().mcCommandError(error.getMessage()), NamedTextColor.RED));
    }
}
