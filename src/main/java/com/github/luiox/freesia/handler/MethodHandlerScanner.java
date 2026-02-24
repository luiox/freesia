package com.github.luiox.freesia.handler;

import com.github.luiox.freesia.filter.EventFilterScanner;
import com.github.luiox.freesia.filter.MethodFilterScanner;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class MethodHandlerScanner implements EventHandlerScanner {
    private final AnnotatedListenerPredicate annotatedListenerPredicate = new AnnotatedListenerPredicate();

    private final EventFilterScanner<Method> filterScanner = new MethodFilterScanner();

    public Map<Class<?>, Set<EventHandler>> locate(Object listenerContainer) {
        HashMap<Class<?>, Set<EventHandler>> eventHandlers = new HashMap<>();
        Stream.of(listenerContainer.getClass().getDeclaredMethods())
                .filter(this.annotatedListenerPredicate)
                .forEach(method -> eventHandlers.computeIfAbsent(method.getParameterTypes()[0],
                                obj -> new TreeSet<>())
                        .add(new MethodEventHandler(listenerContainer, method, this.filterScanner.scan(method))));
        return eventHandlers;
    }
}