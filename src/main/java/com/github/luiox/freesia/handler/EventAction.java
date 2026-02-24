package com.github.luiox.freesia.handler;

@FunctionalInterface
public interface EventAction {
    void invoke(Object event) throws Throwable;
}
