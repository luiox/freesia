package com.github.luiox.freesia.handler;

import com.github.luiox.freesia.filter.EventFilter;

public interface EventHandler extends Comparable<EventHandler> {
    <E> void handle(E paramE);

    Object getListener();

    ListenerPriority getPriority();

    Iterable<EventFilter> getFilters();
}