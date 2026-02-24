package com.github.luiox.freesia;

public interface EventBus {
    <E> E post(E object);

    boolean isRegistered(Object object);

    boolean register(Object object);

    boolean unregister(Object object);
}