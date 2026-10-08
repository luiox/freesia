package com.github.luiox.freesia.filter;

import com.github.luiox.freesia.handler.Listener;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Instantiates the filter types named on {@link Listener#filters()}.
 *
 * <p>A filter class that cannot be instantiated is dropped with a diagnostic rather than
 * failing registration: a broken filter should not cost you the listener itself, and the
 * alternative - propagating - turns a typo in one annotation into a dead event type.
 */
public final class MethodFilterScanner implements EventFilterScanner {

    @Override
    @SuppressWarnings("rawtypes")
    public EventFilter[] scan(Method method) {
        Listener listener = method.getDeclaredAnnotation(Listener.class);
        if (listener == null || listener.filters().length == 0) {
            return null;
        }
        Class<? extends EventFilter>[] declared = listener.filters();
        EventFilter[] filters = new EventFilter[declared.length];
        int count = 0;
        for (Class<? extends EventFilter> type : declared) {
            try {
                filters[count++] = type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                System.getLogger(MethodFilterScanner.class.getPackageName())
                        .log(System.Logger.Level.ERROR,
                                "cannot instantiate filter " + type.getName()
                                        + " declared on " + method.getDeclaringClass().getName()
                                        + '#' + method.getName() + "; the listener will run without it", e);
            }
        }
        return count == 0 ? null : Arrays.copyOf(filters, count);
    }
}
