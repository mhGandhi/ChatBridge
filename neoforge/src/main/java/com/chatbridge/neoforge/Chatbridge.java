package com.chatbridge.neoforge;

import com.mhgandhi.chatBridge.core.Application;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(Chatbridge.MODID)
public final class Chatbridge {
    public static final String MODID = "chatbridge";
    private static final Logger LOGGER = LogUtils.getLogger();

    private Application cb;

    public Chatbridge() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        var server = event.getServer();


        // Next:
        // 1. Load NeoForgeConfig.
        // 2. Create NeoForgeGame using server.
        // 3. Construct Application.
        // 4. Register NeoForgeMinecraftGateway.
        // 5. Start Application.
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {

        // Close Application here.
    }
}