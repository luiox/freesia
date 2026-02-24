package com.github.luiox.freesia;

public interface EventBus {
    <E> E post(E object);

    boolean isRegistered(Object object);

    boolean addListener(Object object);

    boolean removeListener(Object object);
}