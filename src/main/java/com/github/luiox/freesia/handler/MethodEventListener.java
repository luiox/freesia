package com.github.luiox.freesia.handler;

import com.github.luiox.freesia.Event;
import com.github.luiox.freesia.EventListener;
import com.github.luiox.freesia.filter.EventFilter;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A listener backed by a reflected method, with its call body compiled once into a
 * lambda.
 *
 * <p>Two things here are load-bearing and easy to get wrong.
 *
 * <p>The first is {@link #lookupFor}. {@code MethodHandles.lookup()} inside this class has
 * no private access to the declaring class of a listener, so {@code unreflect} on someone
 * else's private method fails with {@code IllegalAccessException} no matter how many times
 * {@code setAccessible(true)} was called - accessibility and lookup authority are different
 * things. {@code privateLookupIn} grants the authority, and falls back to the plain lookup
 * when the target class is genuinely unreachable, which is what makes listeners in another
 * class loader (a plugin, say) work instead of throwing at registration.
 *
 * <p>The second is the filter array being {@code null} rather than empty when a listener
 * declares none. An empty array still costs a loop header and a length load on every
 * dispatch; {@code null} costs one predictable branch.
 */
public final class MethodEventListener implements EventListener {

    private static final MethodHandles.Lookup FALLBACK = MethodHandles.lookup();

    private final Object owner;
    private final Method method;
    private final EventAction action;
    private final EventFilter[] filters;
    private final Class<? extends Event> eventType;
    private final ListenerPriority priority;
    private final int order;
    private final String description;

    public MethodEventListener(Object owner, Method method, EventFilter[] filters) {
        this.owner = owner;
        this.method = method;
        this.filters = filters;
        this.eventType = castEventType(method);
        Listener annotation = method.getDeclaredAnnotation(Listener.class);
        this.priority = annotation.priority();
        this.order = annotation.order();
        this.description = owner.getClass().getName() + '#' + method.getName();
        if (!method.trySetAccessible()) {
            // Not fatal: public members stay reachable, and the action build below will
            // fail loudly if the method genuinely is not invocable.
            System.getLogger(MethodEventListener.class.getPackageName())
                    .log(System.Logger.Level.DEBUG,
                            "could not open access to " + this.description + "; relying on its declared visibility");
        }
        this.action = createAction(method, owner);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> castEventType(Method method) {
        return (Class<? extends Event>) method.getParameterTypes()[0];
    }

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Event event) {
        EventFilter[] local = this.filters;
        if (local != null) {
            for (int i = 0; i < local.length; i++) {
                if (!local[i].test(this, event)) {
                    return;
                }
            }
        }
        try {
            this.action.invoke(event);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new ListenerInvocationException(this.description, event.getClass(), t);
        }
    }

    private static MethodHandles.Lookup lookupFor(Class<?> declaringClass) {
        try {
            return MethodHandles.privateLookupIn(declaringClass, FALLBACK);
        } catch (IllegalAccessException e) {
            return FALLBACK;
        }
    }

    private static EventAction createAction(Method targetMethod, Object target) {
        MethodHandles.Lookup lookup = lookupFor(targetMethod.getDeclaringClass());
        try {
            MethodHandle implementation = lookup.unreflect(targetMethod);
            MethodType erased = MethodType.methodType(void.class, Object.class);
            MethodType instantiated = MethodType.methodType(void.class, targetMethod.getParameterTypes()[0]);

            MethodType factory;
            if (Modifier.isStatic(targetMethod.getModifiers())) {
                factory = MethodType.methodType(EventAction.class);
                CallSite site = LambdaMetafactory.metafactory(lookup, "invoke", factory, erased, implementation, instantiated);
                return (EventAction) site.getTarget().invokeExact();
            }

            factory = MethodType.methodType(EventAction.class, targetMethod.getDeclaringClass());
            CallSite site = LambdaMetafactory.metafactory(lookup, "invoke", factory, erased, implementation, instantiated);
            return (EventAction) site.getTarget().invoke(target);
        } catch (Throwable t) {
            throw new IllegalArgumentException("cannot compile listener "
                    + targetMethod.getDeclaringClass().getName() + '#' + targetMethod.getName()
                    + "; is it static, non-bridge, and open to this class loader?", t);
        }
    }

    @Override
    public Class<? extends Event> eventType() {
        return this.eventType;
    }

    @Override
    public ListenerPriority priority() {
        return this.priority;
    }

    @Override
    public int order() {
        return this.order;
    }

    @Override
    public Object owner() {
        return this.owner;
    }

    @Override
    public String describe() {
        return this.description;
    }

    /** Retained for diagnostics; the method itself is not on the dispatch path. */
    public Method method() {
        return this.method;
    }

    @Override
    public String toString() {
        return this.description;
    }
}
