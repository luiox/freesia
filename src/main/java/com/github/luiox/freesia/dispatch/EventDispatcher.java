package com.github.luiox.freesia.dispatch;

public interface EventDispatcher {
    <E> void dispatch(E paramE);
}