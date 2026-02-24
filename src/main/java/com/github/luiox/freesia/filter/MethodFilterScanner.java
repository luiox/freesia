package com.github.luiox.freesia.filter;

import com.github.luiox.freesia.handler.Listener;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class MethodFilterScanner implements EventFilterScanner<Method> {
    public Set<EventFilter> scan(Method listener) {
        if (!listener.isAnnotationPresent(Listener.class))
            return Collections.emptySet();
        Set<EventFilter> filters = new HashSet<>();
        for (Class<? extends EventFilter> filter : listener.getDeclaredAnnotation(Listener.class).filters()) {
            try {
                filters.add(filter.getDeclaredConstructor().newInstance());
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        return filters;
    }
}