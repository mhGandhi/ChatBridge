package com.chatbridge.neoforge;

import com.chatbridge.neoforge.implementation.NeoForgeCommandRegistrar;
import com.chatbridge.neoforge.implementation.NeoForgeConfig;
import com.chatbridge.neoforge.implementation.NeoForgeGame;
import com.mhgandhi.chatBridge.core.Application;
import com.mhgandhi.chatBridge.core.YamlConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

@Mod(Chatbridge.MODID)
public final class Chatbridge {
    public static final String MODID = "chatbridge";
    private static final Logger LOGGER = Logger.getLogger(MODID);

    private final NeoForgeCommandRegistrar commands =
            new NeoForgeCommandRegistrar();

    private NeoForgeMinecraftGateway gateway;

    private Application cb;

    public Chatbridge() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        //todo commands.registerCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        var server = event.getServer();

        Executor serverExecutor = task -> {
            if (server.isSameThread()) {
                task.run();
            } else {
                server.execute(task);
            }
        };

        try {
            var config = new YamlConfig(
                    FMLPaths.CONFIGDIR.get()
                            .resolve("chatbridge")
                            .resolve("config.yml"),

                    Objects.requireNonNull(
                            Chatbridge.class.getResourceAsStream("/config.yml"),
                            "Missing bundled config.yml"
                    )
            );

            cb = new Application(
                    serverExecutor,
                    config,
                    null,
                    commands,
                    LOGGER
            );

            gateway = new NeoForgeMinecraftGateway(
                    cb.getLogger(),
                    cb.getEventRouter(),
                    cb.getIdentityManager(),
                    cb.confConnectionReminders,
                    cb.confWhitelist,
                    server
            );

            cb.registerGateway(gateway);
            NeoForge.EVENT_BUS.register(gateway);

            cb.start();
        } catch (Exception error) {
            LOGGER.log(
                    Level.SEVERE,
                    "Failed to start ChatBridge",
                    error
            );

            stopApplication();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        stopApplication();
    }

    private void stopApplication() {
        // Stop receiving game events before closing the application.
        if (gateway != null) {
            NeoForge.EVENT_BUS.unregister(gateway);
            gateway = null;
        }

        try {
            if (cb != null) {
                cb.close();
            }
        } catch (Exception error) {
            LOGGER.log(
                    Level.SEVERE,
                    "Failed to close ChatBridge",
                    error
            );
        } finally {
            cb = null;
            //todo commands.clearCallbacks();
        }
    }
}