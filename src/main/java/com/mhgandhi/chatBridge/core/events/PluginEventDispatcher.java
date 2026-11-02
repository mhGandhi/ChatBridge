package com.mhgandhi.chatBridge.core.events;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class PluginEventDispatcher {//chatty crazy
    private final Map<Class<?>, Consumer<? super ServerEvent>> handlers = new LinkedHashMap<>();
    private Consumer<? super ServerEvent> defaultHandler = null;

    /** Register a handler for a specific event type. */
    public <E extends ServerEvent> void on(Class<E> type, Consumer<E> handler) {
        handlers.put(type, (Consumer<? super ServerEvent>) e -> handler.accept(type.cast(e)));
    }

    /** Optional fallback if no handler matches. */
    public void onDefault(Consumer<? super ServerEvent> handler) {
        this.defaultHandler = handler;
    }

    /** Dispatch an event to the first matching handler (supports inheritance & interfaces). */
    public void dispatch(ServerEvent event) {
        for (var entry : handlers.entrySet()) {
            Class<?> key = entry.getKey();
            if (key.isInstance(event)) { // works for subclasses & interfaces
                entry.getValue().accept(event);
                return;
            }
        }
        if (defaultHandler != null) {
            defaultHandler.accept(event);
        }
    }
}
