package com.mhgandhi.chatBridge.core.events;

import org.bukkit.event.Event;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ServerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    protected ServerEvent() {
        //events should be called through ChatGateway.callEvent(PluginEvent), where sync is enforced
        cancelled = false;
    }

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean c) { cancelled = c; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
