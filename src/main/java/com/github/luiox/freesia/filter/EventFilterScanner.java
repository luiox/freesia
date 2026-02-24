package com.github.luiox.freesia.filter;

import java.util.Set;

public interface EventFilterScanner<T> {
    Set<EventFilter> scan(T paramT);
}