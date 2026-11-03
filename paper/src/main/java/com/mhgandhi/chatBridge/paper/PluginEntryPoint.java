package com.mhgandhi.chatBridge.paper;

import com.mhgandhi.chatBridge.core.Application;
import com.mhgandhi.chatBridge.paper.implementation.PaperCommandRegistrar;
import com.mhgandhi.chatBridge.paper.implementation.PaperConfig;
import com.mhgandhi.chatBridge.paper.implementation.PaperGame;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executor;

//todo keep list of IChats; message events with fields message, sender, sourceChat; and like that it would be nicer

public final class PluginEntryPoint extends JavaPlugin {
    private Application cb;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Executor serverExecutor = task -> {
            if(Bukkit.isPrimaryThread()){
                task.run();
            }else{
                Bukkit.getScheduler().runTask(this, task);
            }
        };

        cb = new Application(
                serverExecutor,
                new PaperConfig(getConfig()),
                new PaperGame(this),
                new PaperCommandRegistrar(this),
                getLogger()
        );


        PaperMinecraftGateway mg = new PaperMinecraftGateway(cb.getLogger(), cb.getEventRouter(), cb.getIdentityManager(), cb.confConnectionReminders, cb.confWhitelist, this);

        cb.registerGateway(mg);

        getServer().getPluginManager().registerEvents(mg,this);

        cb.start();
    }

    @Override
    public void onDisable() {
        if (cb == null) return;

        try {
            cb.close();
        } catch (Exception e) {
            getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "Failed to close ChatBridge",
                    e
            );
        } finally {
            cb = null;
        }
    }
}
