package com.github.luiox.freesia.handler;

import com.github.luiox.freesia.Event;
import com.github.luiox.freesia.EventListener;
import com.github.luiox.freesia.filter.EventFilterScanner;
import com.github.luiox.freesia.filter.MethodFilterScanner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Discovers {@link Listener}-annotated methods.
 *
 * <p>The class chain and the implemented interfaces are both walked, because sharing
 * listener behaviour through a base class or an interface is the natural way to write
 * this and a scanner that only reads {@code getDeclaredMethods()} silently registers
 * nothing at all - no error, no listener, just a feature that never runs.
 *
 * <p>Walking most-derived first, with a signature set that both passes share, gives the
 * right answer for overrides: a subclass that overrides an annotated method registers
 * only the subclass version, and a class method always wins over an interface default with
 * the same signature.
 */
public final class MethodListenerScanner implements ListenerScanner {

    private final EventFilterScanner filterScanner;

    public MethodListenerScanner() {
        this(new MethodFilterScanner());
    }

    public MethodListenerScanner(EventFilterScanner filterScanner) {
        this.filterScanner = filterScanner;
    }

    @Override
    public List<EventListener> locate(Object listenerContainer) {
        List<EventListener> found = new ArrayList<>(4);
        Set<String> claimed = new HashSet<>(8);
        walkClasses(listenerContainer.getClass(), listenerContainer, claimed, found);
        walkInterfaces(listenerContainer.getClass().getInterfaces(), listenerContainer, claimed, found);
        return found;
    }

    private void walkClasses(Class<?> type, Object container, Set<String> claimed, List<EventListener> found) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            collect(c.getDeclaredMethods(), container, claimed, found);
            walkInterfaces(c.getInterfaces(), container, claimed, found);
        }
    }

    private void walkInterfaces(Class<?>[] interfaces, Object container, Set<String> claimed, List<EventListener> found) {
        for (Class<?> i : interfaces) {
            for (Class<?> c = i; c != null && c != Object.class; c = c.getSuperclass()) {
                collect(c.getDeclaredMethods(), container, claimed, found);
                walkInterfaces(c.getInterfaces(), container, claimed, found);
            }
        }
    }

    private void collect(Method[] methods, Object container, Set<String> claimed, List<EventListener> found) {
        // getDeclaredMethods() has no specified order, so without this the dispatch order of
        // several equally-declared listeners on one class would vary between JVM runs - and
        // an order you cannot reproduce is an order you cannot debug. Sorting by name gives a
        // stable sequence; source order is not recoverable through reflection, so a listener
        // that genuinely needs a specific position declares it with Listener#order().
        Method[] sorted = methods.clone();
        Arrays.sort(sorted, Comparator.comparing(Method::getName));
        for (Method method : sorted) {
            Listener annotation = method.getDeclaredAnnotation(Listener.class);
            if (annotation == null || method.isSynthetic() || method.isBridge()) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                reject(method, "it must take exactly one argument");
                continue;
            }
            Class<?> parameter = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(parameter)) {
                reject(method, "its argument must be an Event, but was " + parameter.getName());
                continue;
            }
            // Claimed across the class and interface passes, so an override is registered once.
            if (!claimed.add(signatureOf(method))) {
                continue;
            }
            found.add(new MethodEventListener(container, method, this.filterScanner.scan(method)));
        }
    }

    private void reject(Method method, String reason) {
        System.getLogger(MethodListenerScanner.class.getPackageName())
                .log(System.Logger.Level.ERROR,
                        "@Listener on " + method.getDeclaringClass().getName() + '#' + method.getName()
                                + " was ignored because " + reason);
    }

    private static String signatureOf(Method method) {
        return method.getName() + '(' + Arrays.toString(method.getParameterTypes()) + ')';
    }
}
