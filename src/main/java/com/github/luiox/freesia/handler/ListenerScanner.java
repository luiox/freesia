package com.github.luiox.freesia.handler;

import com.github.luiox.freesia.EventListener;

import java.util.List;

/** Finds the listener methods declared on an object. */
@FunctionalInterface
public interface ListenerScanner {

    List<EventListener> locate(Object listenerContainer);
}
