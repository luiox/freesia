package com.github.luiox.freesia.handler;

import java.lang.reflect.Method;
import java.util.function.Predicate;

public final class AnnotatedListenerPredicate implements Predicate<Method> {
    @Override
    public boolean test(Method method) {
        return (method.isAnnotationPresent(Listener.class) && method
                .getParameterCount() == 1);
    }
}