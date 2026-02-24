package com.github.luiox.freesia;

import com.github.luiox.freesia.handler.EventHandler;
import com.github.luiox.freesia.handler.EventHandlerScanner;
import com.github.luiox.freesia.handler.MethodHandlerScanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Objects;

public class EventManager implements EventBus {
    private final Map<Object, Map<Class<?>, List<EventHandler>>> listenerHandlers = new ConcurrentHashMap<>();
    private final Map<Class<?>, CopyOnWriteArrayList<EventHandler>> handlersByEventType = new ConcurrentHashMap<>();
    private final EventHandlerScanner eventHandlerScanner = new MethodHandlerScanner();
    private final ExecutorService executorService;

    public EventManager() {
        this(Executors.newCachedThreadPool());
    }

    public EventManager(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public <E> E post(E event) {
        Objects.requireNonNull(event, "event");
        CopyOnWriteArrayList<EventHandler> handlers = this.handlersByEventType.get(event.getClass());
        if (handlers == null || handlers.isEmpty())
            return event;

        for (EventHandler eventHandler : handlers) {
            if (event instanceof Cancellable && ((Cancellable) event).isCancelled())
                break;

            if (eventHandler.isAsync()) {
                this.executorService.execute(() -> eventHandler.handle(event));
            } else {
                eventHandler.handle(event);
            }
        }
        return event;
    }

    public boolean isRegistered(Object listener) {
        Objects.requireNonNull(listener, "listener");
        return this.listenerHandlers.containsKey(listener);
    }

    public synchronized boolean register(Object listenerContainer) {
        Objects.requireNonNull(listenerContainer, "listenerContainer");
        if (this.listenerHandlers.containsKey(listenerContainer))
            return false;

        Map<Class<?>, Set<EventHandler>> eventHandlers = this.eventHandlerScanner.locate(listenerContainer);
        if (eventHandlers.isEmpty())
            return false;

        Map<Class<?>, List<EventHandler>> indexedHandlers = new HashMap<>();
        eventHandlers.forEach((eventType, handlers) -> {
            CopyOnWriteArrayList<EventHandler> bucket = this.handlersByEventType.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>());
            bucket.addAll(handlers);
            bucket.sort(Comparator.naturalOrder());
            indexedHandlers.put(eventType, new ArrayList<>(handlers));
        });

        return (this.listenerHandlers.put(listenerContainer, indexedHandlers) == null);
    }

    public synchronized boolean unregister(Object listenerContainer) {
        Objects.requireNonNull(listenerContainer, "listenerContainer");
        Map<Class<?>, List<EventHandler>> registeredHandlers = this.listenerHandlers.remove(listenerContainer);
        if (registeredHandlers == null)
            return false;

        registeredHandlers.forEach((eventType, handlers) -> {
            CopyOnWriteArrayList<EventHandler> bucket = this.handlersByEventType.get(eventType);
            if (bucket == null)
                return;

            bucket.removeAll(handlers);
            if (bucket.isEmpty())
                this.handlersByEventType.remove(eventType, bucket);
        });
        return true;
    }
}