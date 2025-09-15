package com.mhgandhi.chatBridge.events;

import org.bukkit.event.Event;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PluginEvent extends Event implements Cancellable {//todo why even cancellable?
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    protected PluginEvent() {
        //events should be called through ChatGateway.callEvent(PluginEvent), where sync is enforced
        super(false);
        cancelled = false;
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean c) { cancelled = c; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
