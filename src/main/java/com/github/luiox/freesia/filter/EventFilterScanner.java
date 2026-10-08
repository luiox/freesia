package com.github.luiox.freesia.filter;

import java.lang.reflect.Method;

/** Builds the filter instances declared on a listener method. */
@FunctionalInterface
public interface EventFilterScanner {

    /**
     * @return filters for {@code method}, or {@code null} when it declares none. The raw
     *         array type is what lets the dispatch loop call through erasure without a
     *         per-call cast; {@code null} rather than an empty array lets it skip the loop.
     */
    EventFilter[] scan(Method method);
}
