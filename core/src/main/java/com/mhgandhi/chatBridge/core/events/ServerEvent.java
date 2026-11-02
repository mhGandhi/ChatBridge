package com.mhgandhi.chatBridge.core.events;


public class ServerEvent {
    private boolean cancelled;

    protected ServerEvent() {
        //events should be called through ChatGateway.callEvent(PluginEvent), where sync is enforced
        cancelled = false;
    }

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean c) { cancelled = c; }
}
