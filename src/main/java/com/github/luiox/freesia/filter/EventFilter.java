package com.github.luiox.freesia.filter;

import com.github.luiox.freesia.handler.EventHandler;

public interface EventFilter<E> {
    boolean test(EventHandler paramEventHandler, E paramE);
}