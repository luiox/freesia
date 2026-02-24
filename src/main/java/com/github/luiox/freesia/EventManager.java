package com.github.luiox.freesia;

import com.github.luiox.freesia.dispatch.EventDispatcher;
import com.github.luiox.freesia.handler.EventHandler;
import com.github.luiox.freesia.handler.EventHandlerScanner;
import com.github.luiox.freesia.dispatch.MethodEventDispatcher;
import com.github.luiox.freesia.handler.MethodHandlerScanner;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EventManager implements EventBus {
    final Map<Object, EventDispatcher> listenerDispatchers = new ConcurrentHashMap<>();
    private final EventHandlerScanner eventHandlerScanner = new MethodHandlerScanner();

    public <E> E post(E event) {
        for (EventDispatcher dispatcher : this.listenerDispatchers.values())
            dispatcher.dispatch(event);
        return event;
    }

    public boolean isRegistered(Object listener) {
        return this.listenerDispatchers.containsKey(listener);
    }

    public boolean addListener(Object listenerContainer) {
        if (this.listenerDispatchers.containsKey(listenerContainer))
            return false;
        Map<Class<?>, Set<EventHandler>> eventHandlers = this.eventHandlerScanner.locate(listenerContainer);
        if (eventHandlers.isEmpty())
            return false;
        return (this.listenerDispatchers.put(listenerContainer, new MethodEventDispatcher(eventHandlers)) == null);
    }

    public boolean removeListener(Object listenerContainer) {
        return (this.listenerDispatchers.remove(listenerContainer) != null);
    }
}