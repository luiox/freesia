package com.github.luiox.freesia.handler;

import com.github.luiox.freesia.filter.EventFilter;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Set;

public final class MethodEventHandler implements EventHandler {
    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();
    private static boolean failed = false;
    private final Object listenerParent;
    private final Method method;
    private final Set<EventFilter> eventFilters;

    private final Listener listenerAnnotation;
    private MethodHandle methodHandle;

    public MethodEventHandler(Object listenerParent, Method method, Set<EventFilter> eventFilters) {
        this.listenerParent = listenerParent;
        if (!method.isAccessible())
            method.setAccessible(true);
        this.method = method;
        try {
            this.methodHandle = lookup.unreflect(method);
        } catch (Throwable t) {
            if (!failed) {
                (new RuntimeException("Couldnt unreflect handler", t)).printStackTrace();
                failed = true;
            }
        }
        this.eventFilters = eventFilters;
        this.listenerAnnotation = method.getAnnotation(Listener.class);
    }

    public <E> void handle(E event) {
        for (EventFilter filter : this.eventFilters) {
            if (!filter.test(this, event))
                return;
        }
        if (this.methodHandle != null)
            try {
                if (this.listenerParent != null) {
                    this.methodHandle.invoke(this.listenerParent, event);
                } else {
                    this.methodHandle.invoke(event);
                }
                return;
            } catch (Throwable t) {
                t.printStackTrace();
                return;
            }
        try {
            this.method.invoke(this.listenerParent, new Object[]{event});
        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public Object getListener() {
        return this.method;
    }

    public ListenerPriority getPriority() {
        return this.listenerAnnotation.priority();
    }

    public Iterable<EventFilter> getFilters() {
        return this.eventFilters;
    }

    public int compareTo(EventHandler eventHandler) {
        return Integer.compare(eventHandler.getPriority().getPriorityLevel(), getPriority().getPriorityLevel());
    }
}