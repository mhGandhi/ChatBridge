package com.chatbridge.neoforge;

import com.chatbridge.neoforge.implementation.NeoForgeCommandRegistrar;
import com.chatbridge.neoforge.implementation.NeoForgeConfig;
import com.chatbridge.neoforge.implementation.NeoForgeGame;
import com.mhgandhi.chatBridge.core.Application;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

@Mod(Chatbridge.MODID)
public final class Chatbridge {
    public static final String MODID = "chatbridge";

    // Your Paper adapter passes java.util.logging.Logger to Application.
    // Keep that type unless you change the core's logging abstraction.
    private static final Logger LOGGER = Logger.getLogger(MODID);

    private final NeoForgeCommandRegistrar commands =
            new NeoForgeCommandRegistrar();

    private Application cb;
    private NeoForgeMinecraftGateway gateway;

    public Chatbridge() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        commands.registerCommands(event.getDispatcher());
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
            // Proposed adapter API:
            // load local configuration, creating defaults if necessary.
            NeoForgeConfig config = NeoForgeConfig.load();

            NeoForgeGame game = new NeoForgeGame(
                    server,
                    config,
                    LOGGER
            );

            cb = new Application(
                    serverExecutor,
                    config,
                    game,
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
            commands.clearCallbacks();
        }
    }
}