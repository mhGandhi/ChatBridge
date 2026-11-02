package com.mhgandhi.chatBridge.paper;

import com.mhgandhi.chatBridge.core.Application;
import com.mhgandhi.chatBridge.paper.implementation.PaperCommandRegistrar;
import com.mhgandhi.chatBridge.paper.implementation.PaperConfig;
import com.mhgandhi.chatBridge.paper.implementation.PaperGame;
import org.bukkit.plugin.java.JavaPlugin;

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
        try {
            cb.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
