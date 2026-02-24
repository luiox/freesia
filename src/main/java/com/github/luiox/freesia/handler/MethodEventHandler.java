package com.github.luiox.freesia.handler;

import com.github.luiox.freesia.filter.EventFilter;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

public final class MethodEventHandler implements EventHandler {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private final Object listenerParent;
    private final Method method;
    private final Set<EventFilter> eventFilters;

    private final Listener listenerAnnotation;
    private final EventAction eventAction;

    public MethodEventHandler(Object listenerParent, Method method, Set<EventFilter> eventFilters) {
        this.listenerParent = listenerParent;
        if (!method.isAccessible())
            method.setAccessible(true);
        this.method = method;
        this.eventFilters = eventFilters;
        this.listenerAnnotation = method.getAnnotation(Listener.class);
        this.eventAction = this.createEventAction(method, listenerParent);
    }

    public <E> void handle(E event) {
        for (EventFilter filter : this.eventFilters) {
            if (!filter.test(this, event))
                return;
        }
        try {
            this.eventAction.invoke(event);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private EventAction createEventAction(Method targetMethod, Object target) {
        try {
            MethodHandle implementation = LOOKUP.unreflect(targetMethod);
            MethodType erasedSignature = MethodType.methodType(void.class, Object.class);
            MethodType instantiatedSignature = MethodType.methodType(void.class, targetMethod.getParameterTypes()[0]);

            MethodType factoryType;
            CallSite callSite;
            if (Modifier.isStatic(targetMethod.getModifiers())) {
                factoryType = MethodType.methodType(EventAction.class);
                callSite = LambdaMetafactory.metafactory(
                        LOOKUP,
                        "invoke",
                        factoryType,
                        erasedSignature,
                        implementation,
                        instantiatedSignature
                );
                return (EventAction) callSite.getTarget().invokeExact();
            }

            factoryType = MethodType.methodType(EventAction.class, targetMethod.getDeclaringClass());
            callSite = LambdaMetafactory.metafactory(
                    LOOKUP,
                    "invoke",
                    factoryType,
                    erasedSignature,
                    implementation,
                    instantiatedSignature
            );
            return (EventAction) callSite.getTarget().invoke(target);
        } catch (Throwable throwable) {
            throw new RuntimeException("Could not create lambda event action for method: " + targetMethod, throwable);
        }
    }

    public Object getListener() {
        return this.method;
    }

    public int getPriority() {
        return this.listenerAnnotation.priority();
    }

    public boolean isAsync() {
        return this.listenerAnnotation.async();
    }

    public Iterable<EventFilter> getFilters() {
        return this.eventFilters;
    }

    public int compareTo(EventHandler eventHandler) {
        return Integer.compare(eventHandler.getPriority(), getPriority());
    }
}