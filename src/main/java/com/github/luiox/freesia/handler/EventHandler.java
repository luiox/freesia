package com.github.luiox.freesia.handler;

import com.github.luiox.freesia.filter.EventFilter;

public interface EventHandler extends Comparable<EventHandler> {
    <E> void handle(E paramE);

    Object getListener();

    int getPriority();

    boolean isAsync();

    Iterable<EventFilter> getFilters();
}