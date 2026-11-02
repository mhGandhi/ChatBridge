package com.mhgandhi.chatBridge.platform.paper;

import com.mhgandhi.chatBridge.core.*;
import com.mhgandhi.chatBridge.platform.paper.implementation.PaperConfig;
import com.mhgandhi.chatBridge.platform.paper.implementation.PaperGame;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

//todo keep list of IChats; message events with fields message, sender, sourceChat; and like that it would be nicer

public final class PluginEntryPoint extends JavaPlugin {
    //public static final int MSG_LIMIT = 2000;//todo implement msg limit (2000?) in the gateways (mby seperate limits?)

    private Application cb;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        cb = new Application(
                new PaperConfig(getConfig()),
                new PaperGame(this),
                getLogger()
        );

        if(cb.confWhitelist){
            PlayerRejecter pr = new PlayerRejecter(this, cb.getIdentityManager());
            getServer().getPluginManager().registerEvents(pr, this);
        }

        MinecraftGateway mg = new MinecraftGateway(cb.getLogger(), cb.getEventRouter(), cb.getIdentityManager(), cb.confConnectionReminders, this);
        cb.registerGateway(mg);

        Objects.requireNonNull(getCommand("connect"), "Missing command 'connect' in plugin.yml")
                .setExecutor(mg);
        Objects.requireNonNull(getCommand("connect"), "Missing command 'connect' in plugin.yml")
                .setTabCompleter(mg);
        Objects.requireNonNull(getCommand("disconnect"), "Missing command 'disconnect' in plugin.yml")
                .setExecutor(mg);
        Objects.requireNonNull(getCommand("status"), "Missing command 'status' in plugin.yml")
                .setExecutor(mg);

        getServer().getPluginManager().registerEvents(mg,this);

        cb.start();
    }

    @Override
    public void onDisable() {
        try {
            cb.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
